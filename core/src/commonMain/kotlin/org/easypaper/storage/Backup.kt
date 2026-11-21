package org.easypaper.storage

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.serialization.Serializable
import org.easypaper.model.EasyPaperJson
import org.easypaper.model.PatientRecord
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** OWASP 2023 recommendation for PBKDF2-HMAC-SHA256. */
const val BACKUP_ITERATIONS = 600_000
private val MAGIC = "EPB1".encodeToByteArray()
private const val SALT_BYTES = 16
private const val HEADER_BYTES = 4 + 4 + SALT_BYTES
private val ALLOWED_ITERATIONS = 10_000..10_000_000 // a crafted file must not make the phone hash for hours

class RestoredBackup(val records: List<PatientRecord>, val photos: Map<String, ByteArray>)

@Serializable
private class BackupContent(val version: Int = 1, val records: List<PatientRecord>, val photos: Map<String, String>)

/**
 * The whole diary in one file for moving to a new phone: key from [passphrase] via PBKDF2-SHA256 with a random salt,
 * content AES-256-GCM encrypted. File: "EPB1" | iterations (u32 BE) | salt | ciphertext; the header is bound as
 * associated data. The passphrase is not stored anywhere — without it the file is useless.
 */
@OptIn(ExperimentalEncodingApi::class)
suspend fun exportBackup(
    records: List<PatientRecord>,
    photos: Map<String, ByteArray>,
    passphrase: String,
    iterations: Int = BACKUP_ITERATIONS,
): ByteArray {
    require(passphrase.length >= 8) { "Passphrase must have at least 8 characters" }
    require(iterations in ALLOWED_ITERATIONS) { "Iterations out of range" }
    val salt = CryptographyRandom.nextBytes(SALT_BYTES)
    val header = MAGIC + byteArrayOf((iterations ushr 24).toByte(), (iterations ushr 16).toByte(), (iterations ushr 8).toByte(), iterations.toByte()) + salt
    val json = EasyPaperJson.encodeToString(BackupContent.serializer(), BackupContent(records = records, photos = photos.mapValues { Base64.encode(it.value) }))
    return header + aesGcm(derive(passphrase, salt, iterations)).encrypt(json.encodeToByteArray(), header)
}

/** Opens a file from [exportBackup]. Wrong passphrase, damaged or altered file -> exception. */
@OptIn(ExperimentalEncodingApi::class)
suspend fun importBackup(file: ByteArray, passphrase: String): RestoredBackup {
    require(file.size > HEADER_BYTES && file.copyOfRange(0, 4).contentEquals(MAGIC)) { "Not an Easy Paper backup" }
    val iterations = (4 until 8).fold(0) { acc, i -> (acc shl 8) or (file[i].toInt() and 0xFF) }
    require(iterations in ALLOWED_ITERATIONS) { "Backup has an unsupported iteration count" }
    val header = file.copyOfRange(0, HEADER_BYTES)
    val key = derive(passphrase, header.copyOfRange(8, HEADER_BYTES), iterations)
    val json = aesGcm(key).decrypt(file.copyOfRange(HEADER_BYTES, file.size), header).decodeToString()
    val content = EasyPaperJson.decodeFromString(BackupContent.serializer(), json)
    return RestoredBackup(content.records, content.photos.mapValues { Base64.decode(it.value) })
}

private suspend fun derive(passphrase: String, salt: ByteArray, iterations: Int): ByteArray =
    CryptographyProvider.Default.get(PBKDF2)
        .secretDerivation(SHA256, iterations, 256.bits, salt)
        .deriveSecretToByteArray(passphrase.encodeToByteArray())
