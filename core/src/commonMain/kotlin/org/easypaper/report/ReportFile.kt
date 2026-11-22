package org.easypaper.report

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.serialization.Serializable
import org.easypaper.model.EasyPaperJson
import org.easypaper.storage.BACKUP_ITERATIONS
import org.easypaper.storage.aesGcm
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val REPORT_FILE_MAGIC = "EPR1".encodeToByteArray()
private const val HEADER_BYTES = 24
private val ALLOWED_ITERATIONS = 10_000..10_000_000

@Serializable
private class ReportFileContent(val report: Report, val photos: Map<String, String>)

/** EPR1 | PBKDF2-SHA256 iterations (u32 BE) | random salt (16) | AES-256-GCM (IV + ciphertext + tag).
 * The whole header is authenticated as AAD. Uses the same photo privacy rules as QR sharing. */
@OptIn(ExperimentalEncodingApi::class)
suspend fun sealReportFile(report: Report, photos: Map<String, ByteArray>, password: String): ByteArray {
    require(password.length >= 8) { "Password must have at least 8 characters" }
    val clean = prepareReportPhotos(report, photos)
    val iterations = BACKUP_ITERATIONS
    val salt = CryptographyRandom.nextBytes(16)
    val header = REPORT_FILE_MAGIC + byteArrayOf(
        (iterations ushr 24).toByte(), (iterations ushr 16).toByte(), (iterations ushr 8).toByte(), iterations.toByte(),
    ) + salt
    val json = EasyPaperJson.encodeToString(ReportFileContent.serializer(),
        ReportFileContent(report, clean.mapValues { Base64.encode(it.value) }))
    return header + aesGcm(reportFileKey(password, salt, iterations)).encrypt(json.encodeToByteArray(), header)
}

/** Wrong password, truncated, altered or foreign files throw. The password is never part of the file. */
@OptIn(ExperimentalEncodingApi::class)
suspend fun openReportFile(bytes: ByteArray, password: String): Pair<Report, Map<String, ByteArray>> {
    require(bytes.size >= HEADER_BYTES + 12 + 16 && bytes.copyOfRange(0, 4).contentEquals(REPORT_FILE_MAGIC)) {
        "Not an Easy Paper report file"
    }
    val iterations = (4 until 8).fold(0) { n, i -> (n shl 8) or (bytes[i].toInt() and 0xFF) }
    require(iterations in ALLOWED_ITERATIONS) { "Unsupported iteration count" }
    val header = bytes.copyOfRange(0, HEADER_BYTES)
    val key = reportFileKey(password, header.copyOfRange(8, HEADER_BYTES), iterations)
    val json = aesGcm(key).decrypt(bytes.copyOfRange(HEADER_BYTES, bytes.size), header).decodeToString()
    val content = EasyPaperJson.decodeFromString(ReportFileContent.serializer(), json)
    return content.report to content.photos.mapValues { Base64.decode(it.value) }
}

private suspend fun reportFileKey(password: String, salt: ByteArray, iterations: Int): ByteArray =
    CryptographyProvider.Default.get(PBKDF2).secretDerivation(SHA256, iterations, 256.bits, salt)
        .deriveSecretToByteArray(password.encodeToByteArray())
