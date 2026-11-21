package org.easypaper.report

import org.easypaper.model.DiaryNote
import org.easypaper.model.EasyPaperJson
import org.easypaper.model.Food
import org.easypaper.storage.aesGcm
import org.easypaper.storage.generateRecordKey
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * An encrypted report: [bytes] travel through the server or a file, the key travels only inside [qr].
 * Without the QR string the bytes cannot be decrypted. [photos]: photoRef -> encrypted photo bytes,
 * same key, opened with [openPhoto].
 */
class SealedReport(val bytes: ByteArray, val qr: String, val photos: Map<String, ByteArray> = emptyMap())

private const val QR_PREFIX = "ep1:"

@OptIn(ExperimentalEncodingApi::class)
private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/**
 * Encrypts with a fresh AES-256-GCM key per report; the report id is bound as associated data.
 * [photos] (photoRef -> bytes) must be photos referenced by this report's food/diary entries; each one is
 * encrypted with the same key and bound to "<reportId>/photo/<ref>", so photos cannot be swapped or passed off as the report.
 * Photos must be JPEG (the platform converts HEIC/PNG/WebP first) and go through [stripJpegMetadata], so GPS,
 * phone model and other metadata never leave the phone; anything else -> IllegalArgumentException.
 */
@OptIn(ExperimentalEncodingApi::class)
suspend fun sealReport(report: Report, photos: Map<String, ByteArray> = emptyMap()): SealedReport {
    val referenced = report.record.entries.mapNotNull {
        when (val p = it.payload) { is Food -> p.photoRef; is DiaryNote -> p.photoRef; else -> null }
    }.toSet()
    val unknown = photos.keys - referenced
    require(unknown.isEmpty()) { "Photos not referenced by the report: $unknown" }
    val clean = photos.mapValues { (ref, photo) ->
        require(photo.size > 2 && photo[0] == 0xFF.toByte() && photo[1] == 0xD8.toByte()) { "Photo $ref is not a JPEG; convert it first" }
        stripJpegMetadata(photo)
    }
    val key = generateRecordKey()
    val cipher = aesGcm(key)
    val json = EasyPaperJson.encodeToString(Report.serializer(), report)
    val bytes = cipher.encrypt(json.encodeToByteArray(), report.id.encodeToByteArray())
    val sealedPhotos = clean.mapValues { (ref, photo) -> cipher.encrypt(photo, photoBinding(report.id, ref)) }
    return SealedReport(bytes, QR_PREFIX + report.id + ":" + base64.encode(key), sealedPhotos)
}

/** Decrypts one photo of the report with the key from the scanned QR. Wrong key, wrong ref or damaged bytes throw. */
suspend fun openPhoto(bytes: ByteArray, qr: String, ref: String): ByteArray {
    val (id, key) = parseQr(qr)
    return aesGcm(key).decrypt(bytes, photoBinding(id, ref))
}

private fun photoBinding(reportId: String, ref: String) = "$reportId/photo/$ref".encodeToByteArray()

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
