package org.easypaper.report

import org.easypaper.model.DiaryNote
import org.easypaper.model.Food
import org.easypaper.model.PatientRecord
import org.easypaper.storage.DiaryRepository
import org.easypaper.storage.PatientRepository

/** What the doctor's app holds after scanning the QR: the report and every photo it references, decrypted. */
class ReceivedReport(val report: Report, val photos: Map<String, ByteArray>)

private val PatientRecord.photoRefs: List<String>
    get() = entries.mapNotNull { when (val p = it.payload) { is Food -> p.photoRef; is DiaryNote -> p.photoRef; else -> null } }.distinct()

/**
 * The patient's side of the visit (task 30): seals [record] with its photos under a fresh key, uploads only
 * ciphertext to the relay and returns the QR string ("ep1:<id>:<key>") to show the doctor. A photo missing from
 * the phone is left out rather than failing the whole handover.
 */
suspend fun PatientRepository.handover(record: PatientRecord, relay: RelayClient, now: Long, summary: String? = null): String =
    handover(record, relay, now, summary) { photo(it) }

/** The same from the v2 diary (#47): [record] is a [org.easypaper.diary.selection] of categories and days. */
suspend fun DiaryRepository.handover(record: PatientRecord, relay: RelayClient, now: Long, summary: String? = null): String =
    handover(record, relay, now, summary) { photo(it) }

private suspend fun handover(record: PatientRecord, relay: RelayClient, now: Long, summary: String?, photo: suspend (String) -> ByteArray?): String {
    val report = Report(id = newReportId(), createdAt = now, record = record, summary = summary)
    val photos = record.photoRefs.mapNotNull { ref -> photo(ref)?.let { ref to it } }.toMap()
    val sealed = sealReport(report, photos)
    relay.upload(sealed)
    return sealed.qr
}

/**
 * The doctor's side: downloads and opens the report, then all its photos at once — the relay hands out every
 * object only once, so a photo not fetched now could never be fetched later. Throws [RelayException] (404: already
 * opened or expired) or IllegalArgumentException for a QR that is not ours.
 */
suspend fun RelayClient.receive(qr: String): ReceivedReport {
    val report = openReport(downloadReport(qr), qr)
    val photos = report.record.photoRefs.mapNotNull { ref ->
        try {
            ref to openPhoto(downloadPhoto(qr, ref), qr, ref)
        } catch (e: RelayException) {
            if (e.status == 404) null else throw e // the phone had no copy of this photo
        }
    }.toMap()
    return ReceivedReport(report, photos)
}
