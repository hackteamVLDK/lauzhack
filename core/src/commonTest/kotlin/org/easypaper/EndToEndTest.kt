package org.easypaper

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.easypaper.diary.EntryProblem
import org.easypaper.diary.check
import org.easypaper.diary.progress
import org.easypaper.model.*
import org.easypaper.report.Report
import org.easypaper.report.factDigest
import org.easypaper.report.openPhoto
import org.easypaper.report.openReport
import org.easypaper.report.reportIdFromQr
import org.easypaper.report.sealReport
import org.easypaper.report.toCsv
import org.easypaper.report.toText
import org.easypaper.scan.MedicationDb
import org.easypaper.scan.parseScanCode
import org.easypaper.scan.toMedication
import org.easypaper.storage.BlobStore
import org.easypaper.storage.RecordStore
import org.easypaper.storage.generateRecordKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Doctor -> patient (2 days) -> encrypted store -> sealed report + photo -> QR -> doctor: the same facts arrive. */
class EndToEndTest {
    private val day = 86_400_000L
    private val hour = 3_600_000L
    private val start = 1_794_873_600_000L // 2026-11-17T00:00:00Z

    @Test
    fun factsSurviveTheWholeJourney() = runTest {
        // 1. Doctor builds the request
        val request = CareRequest(
            id = "req-e2e", createdAt = start - day, doctorName = "Dr. Rossi", complaint = "Stomach pain after meals",
            startDate = "2026-11-17", days = 2, locale = "de",
            widgets = listOf(
                QuestionnaireWidget("q", "Fragen", questions = listOf(Question("pain", "Schmerzen heute?", QuestionKind.YES_NO))),
                BloodPressureWidget("bp", "Blutdruck", timesPerDay = 1),
                WaterWidget("w", "Wasser"),
                FoodWidget("f", "Essen"),
                MedicationsWidget("m", "Medikamente"),
                DiaryWidget("d", "Tagebuch"),
                DoctorQuestionsWidget("dq", "Fragen an den Arzt"),
            ),
        )

        // 2. Patient scans a Pantoprazol box (GS1 DataMatrix) and confirms what was found
        val code = parseScanCode("]d201076805871700181728063010B42X\u001D21SN0001")!!
        assertEquals("280630", code.expiry)
        val med = MedicationDb().find(code)!!.toMedication("de").copy(confirmed = true)

        // 3. Two days of entries; every one passes the input check, a typo does not
        val entries = listOf(
            Entry("e1", "bp", start + 8 * hour, BloodPressure(118, 76, 64)),
            Entry("e2", "w", start + 10 * hour, Water(500)),
            Entry("e3", "m", start + 9 * hour, med),
            Entry("e4", "f", start + 12 * hour, Food("Pizza", "photo-pizza")),
            Entry("e5", "q", start + 21 * hour, Answers(mapOf("pain" to "yes"))),
            Entry("e6", "bp", start + day + 8 * hour, BloodPressure(121, 79)),
            Entry("e7", "d", start + day + 22 * hour, DiaryNote("Schmerz nach dem Abendessen")),
            Entry("e8", "dq", start + day + 12 * hour, DoctorQuestion("Darf ich Kaffee trinken?")),
        )
        entries.forEach { assertEquals(emptyList(), request.check(it), "flagged ${it.id}") }
        assertEquals(listOf(EntryProblem.SYSTOLIC_OUT_OF_RANGE), request.check(Entry("x", "bp", start, BloodPressure(1180, 76))))
        val record = PatientRecord(request, entries)

        // 4. Progress: day 1 complete; day 2 has only blood pressure among the required widgets
        val progress = record.progress()
        assertTrue(progress[0].complete)
        assertEquals(listOf("q", "w", "f", "m"), progress[1].widgets.filter { !it.complete }.map { it.widgetId })

        // 5. Encrypted local store
        val blobs = object : BlobStore {
            val map = mutableMapOf<String, ByteArray>()
            override suspend fun put(key: String, bytes: ByteArray) { map[key] = bytes }
            override suspend fun get(key: String) = map[key]
            override suspend fun keys() = map.keys.toList()
            override suspend fun delete(key: String) { map.remove(key) }
        }
        val store = RecordStore(blobs, generateRecordKey())
        store.save(record)
        assertFalse("Pizza" in blobs.map.getValue("record/req-e2e").decodeToString())
        val loaded = store.load("req-e2e")!!
        store.delete("req-e2e") // after the visit the patient erases the diary
        assertEquals(emptyList(), store.list())

        // 6. Sealed report with the food photo; only the QR opens it
        val photo = org.easypaper.report.tinyJpeg(ByteArray(3000) { (it % 251).toByte() })
        val sealed = sealReport(Report(id = "rep-e2e", createdAt = start + 2 * day, record = loaded), mapOf("photo-pizza" to photo))
        assertEquals("rep-e2e", reportIdFromQr(sealed.qr))
        val received = openReport(sealed.bytes, sealed.qr)
        assertEquals(record, received.record)
        assertContentEquals(photo, openPhoto(sealed.photos.getValue("photo-pizza"), sealed.qr, "photo-pizza"))

        // 7. Doctor side: digest, CSV and FHIR carry the same facts
        val digest = received.record.factDigest(utcOffsetMinutes = 60)
        assertEquals(listOf(118, 121), digest.bloodPressure.map { it.systolic.max })
        assertEquals(listOf(500), digest.water.map { it.totalMl })
        assertEquals("7680587170018", digest.medications.single().code)
        assertTrue(digest.medications.single().confirmed)
        val text = digest.toText()
        assertTrue("Stomach pain after meals" in text && "[e7]" in text && "Darf ich Kaffee trinken?" in text, text)

        val csv = received.record.toCsv(';')
        assertTrue("e3;" in csv && "7680587170018" in csv && "photo-pizza" in csv)
        assertEquals(entries.size + 1, csv.trimEnd().split("\r\n").size) // header + one row per entry (one answer)

        val fhir = received.fhir.getValue("entry").jsonArray.map { it.jsonObject.getValue("resource").jsonObject }
        val ids = fhir.map { it.getValue("id").jsonPrimitive.content }
        assertTrue(entries.all { it.id in ids })
        val medication = fhir.single { it.getValue("id").jsonPrimitive.content == "e3" }
        assertEquals("recorded", medication.getValue("status").jsonPrimitive.content)
        assertTrue("7680587170018" in medication.toString())
    }
}
