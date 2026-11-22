package org.easypaper.report

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.serialization.Serializable
import org.easypaper.model.EasyPaperJson
import org.easypaper.storage.aesGcm
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val HANDOFF_VERSION: Byte = 1
private const val PUBLIC_KEY_BYTES = 65 // SEC1: 0x04 || X (32) || Y (32)
private const val HEADER_BYTES = 1 + PUBLIC_KEY_BYTES
private val HANDOFF_INFO = "easypaper-handoff-v1".encodeToByteArray()

/** Doctor's screen-local keys: public SEC1 uncompressed P-256 point, private PKCS#8 DER. Never send the private key. */
class HandoffKeys(val publicKey: ByteArray, val privateKey: ByteArray) {
    companion object {
        suspend fun generate(): HandoffKeys {
            val pair = CryptographyProvider.Default.get(ECDH).keyPairGenerator(EC.Curve.P256).generateKey()
            return HandoffKeys(
                pair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW),
                pair.privateKey.encodeToByteArray(EC.PrivateKey.Format.DER),
            )
        }
    }
}

@Serializable
private class HandoffContent(val report: Report, val photos: Map<String, String>)

/**
 * Version byte | ephemeral uncompressed P-256 public key (65 bytes) | AES-GCM ciphertext (IV + tag included).
 * ECDH + HKDF-SHA256 derives a fresh AES-256 key; the full header is authenticated as associated data.
 * Photos follow the same JPEG/reference/metadata rules as [sealReport]. The relay receives no private keys.
 */
@OptIn(ExperimentalEncodingApi::class)
suspend fun sealForDoctor(report: Report, photos: Map<String, ByteArray>, doctorPublicKey: ByteArray): ByteArray {
    val clean = prepareReportPhotos(report, photos)
    val ecdh = CryptographyProvider.Default.get(ECDH)
    val doctor = ecdh.publicKeyDecoder(EC.Curve.P256)
        .decodeFromByteArray(EC.PublicKey.Format.RAW, checkedPublicKey(doctorPublicKey))
    val patient = ecdh.keyPairGenerator(EC.Curve.P256).generateKey()
    val ephemeral = patient.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW)
    val header = byteArrayOf(HANDOFF_VERSION) + ephemeral
    val secret = patient.privateKey.sharedSecretGenerator().generateSharedSecretToByteArray(doctor)
    val json = EasyPaperJson.encodeToString(
        HandoffContent.serializer(), HandoffContent(report, clean.mapValues { Base64.encode(it.value) }),
    )
    return header + aesGcm(handoffKey(secret)).encrypt(json.encodeToByteArray(), header)
}

/** Opens only with the claiming doctor's private key. Foreign keys, altered headers/ciphertext and versions throw. */
@OptIn(ExperimentalEncodingApi::class)
suspend fun openFromPatient(bytes: ByteArray, doctorKeys: HandoffKeys): Pair<Report, Map<String, ByteArray>> {
    require(bytes.size >= HEADER_BYTES + 12 + 16 && bytes[0] == HANDOFF_VERSION) { "Invalid handoff packet or version" }
    val header = bytes.copyOfRange(0, HEADER_BYTES)
    val ecdh = CryptographyProvider.Default.get(ECDH)
    val patient = ecdh.publicKeyDecoder(EC.Curve.P256)
        .decodeFromByteArray(EC.PublicKey.Format.RAW, checkedPublicKey(header.copyOfRange(1, HEADER_BYTES)))
    val doctor = ecdh.privateKeyDecoder(EC.Curve.P256).decodeFromByteArray(EC.PrivateKey.Format.DER, doctorKeys.privateKey)
    val secret = doctor.sharedSecretGenerator().generateSharedSecretToByteArray(patient)
    val json = aesGcm(handoffKey(secret)).decrypt(bytes.copyOfRange(HEADER_BYTES, bytes.size), header).decodeToString()
    val content = EasyPaperJson.decodeFromString(HandoffContent.serializer(), json)
    return content.report to content.photos.mapValues { Base64.decode(it.value) }
}

internal fun checkedPublicKey(bytes: ByteArray): ByteArray {
    require(bytes.size == PUBLIC_KEY_BYTES && bytes[0] == 4.toByte()) { "Expected an uncompressed P-256 public key" }
    return bytes
}

private suspend fun handoffKey(secret: ByteArray): ByteArray =
    CryptographyProvider.Default.get(HKDF)
        .secretDerivation(SHA256, 256.bits, salt = null, info = HANDOFF_INFO)
        .deriveSecretToByteArray(secret)
