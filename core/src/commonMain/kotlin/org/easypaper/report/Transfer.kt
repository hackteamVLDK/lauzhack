package org.easypaper.report

import org.easypaper.model.EasyPaperJson
import org.easypaper.storage.aesGcm
import org.easypaper.storage.generateRecordKey
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * An encrypted report: [bytes] travel through the server or a file, the key travels only inside [qr].
 * Without the QR string the bytes cannot be decrypted.
 */
class SealedReport(val bytes: ByteArray, val qr: String)

private const val QR_PREFIX = "ep1:"

@OptIn(ExperimentalEncodingApi::class)
private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/** Encrypts with a fresh AES-256-GCM key per report; the report id is bound as associated data. */
@OptIn(ExperimentalEncodingApi::class)
suspend fun sealReport(report: Report): SealedReport {
    val key = generateRecordKey()
    val json = EasyPaperJson.encodeToString(Report.serializer(), report)
    val bytes = aesGcm(key).encrypt(json.encodeToByteArray(), report.id.encodeToByteArray())
    return SealedReport(bytes, QR_PREFIX + report.id + ":" + base64.encode(key))
}

/** Report id from a scanned QR string, to fetch the bytes from the server. Throws on a foreign QR. */
fun reportIdFromQr(qr: String): String = parseQr(qr).first

/** Decrypts bytes with the key from the scanned QR. Wrong key or damaged bytes throw. */
suspend fun openReport(bytes: ByteArray, qr: String): Report {
    val (id, key) = parseQr(qr)
    val json = aesGcm(key).decrypt(bytes, id.encodeToByteArray()).decodeToString()
    return EasyPaperJson.decodeFromString(Report.serializer(), json)
}

@OptIn(ExperimentalEncodingApi::class)
private fun parseQr(qr: String): Pair<String, ByteArray> {
    val split = qr.lastIndexOf(':')
    require(qr.startsWith(QR_PREFIX) && split > QR_PREFIX.length) { "Not an Easy Paper report QR" }
    val key = base64.decode(qr.substring(split + 1))
    require(key.size == 32) { "Bad key in report QR" }
    return qr.substring(QR_PREFIX.length, split) to key
}
