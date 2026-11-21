package org.easypaper.report

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.easypaper.model.Answers
import org.easypaper.model.BloodPressure
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.CareRequest
import org.easypaper.model.DiaryNote
import org.easypaper.model.DoctorQuestion
import org.easypaper.model.EasyPaperJson
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.Medication
import org.easypaper.model.MedicationSource
import org.easypaper.model.PatientRecord
import org.easypaper.model.Water
import org.easypaper.model.WaterWidget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportTest {
    private val record = PatientRecord(
        CareRequest(
            id = "req-1", createdAt = 0, doctorName = "Dr. Test", complaint = "secret complaint",
            startDate = "2026-11-21", days = 3, locale = "en",
            widgets = listOf(BloodPressureWidget("bp", "BP"), WaterWidget("w", "Water")),
        ),
        listOf(
            Entry("e1", "bp", 1_795_000_000_000, BloodPressure(130, 85, 72)),
            Entry("e2", "w", 1_795_000_000_000, Water(250)),
            Entry("e3", "q", 1_795_000_000_000, Answers(mapOf("q1" to "yes"))),
            Entry("e4", "m", 1_795_000_000_000, Medication("Ibuprofen", "04600000000000", "200 mg", MedicationSource.SCAN, false)),
            Entry("e5", "f", 1_795_000_000_000, Food("soup", "photo-1")),
            Entry("e6", "d", 1_795_000_000_000, DiaryNote("dizzy")),
            Entry("e7", "dq", 1_795_000_000_000, DoctorQuestion("Coffee?")),
        ),
    )
    private val report = Report(id = "rep-1", createdAt = 1, record = record, summary = "BP elevated [e1]")

    @Test
    fun reportJsonRoundTrip() {
        val json = EasyPaperJson.encodeToString(Report.serializer(), report)
        assertEquals(report, EasyPaperJson.decodeFromString(Report.serializer(), json))
    }

    @Test
    fun sealedReportOpensOnlyWithItsQr() = runTest {
        val sealed = sealReport(report)
        assertFalse("secret complaint" in sealed.bytes.decodeToString())
        assertEquals("rep-1", reportIdFromQr(sealed.qr))
        assertEquals(report, openReport(sealed.bytes, sealed.qr))

        val otherQr = sealReport(report).qr // same id, different key
        assertFails { openReport(sealed.bytes, otherQr) }
        val damaged = sealed.bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        assertFails { openReport(damaged, sealed.qr) }
    }

    @Test
    fun badQrRejected() = runTest {
        val bytes = sealReport(report).bytes
        for (qr in listOf("", "hello", "ep1:", "ep1:rep-1:", "ep1:rep-1:AAAA", "xx1:rep-1:" + "A".repeat(43))) {
            assertFails("accepted '$qr'") { openReport(bytes, qr) }
        }
        assertFails { Report(id = "a:b", createdAt = 0, record = record) }
    }

    @Test
    fun fhirBundleKeepsEntryIdsAndCodes() {
        val resources = report.fhir.getValue("entry").jsonArray.map { it.jsonObject.getValue("resource").jsonObject }
            .filter { it.getValue("resourceType").jsonPrimitive.content !in setOf("Composition", "Questionnaire") }
        assertEquals(record.entries.map { it.id }, resources.map { it.getValue("id").jsonPrimitive.content })
        assertEquals(
            listOf("Observation", "Observation", "QuestionnaireResponse", "MedicationStatement", "Observation", "Observation", "Observation"),
            resources.map { it.getValue("resourceType").jsonPrimitive.content },
        )
        val bp = resources[0].toString()
        for (code in listOf("85354-9", "8480-6", "8462-4", "8867-4")) assertTrue(code in bp, "missing LOINC $code")
        assertTrue("\"effectiveDateTime\":\"2026-" in bp)
        assertEquals("draft", resources[3].getValue("status").jsonPrimitive.content) // unconfirmed scan
        assertTrue("04600000000000" in resources[3].toString())
    }

    @Test
    fun reportIdsAreRandomAndRelaySafe() = runTest {
        val ids = List(1000) { newReportId() }
        assertEquals(1000, ids.toSet().size)
        assertTrue(ids.all { Regex("[A-Za-z0-9_-]{22}").matches(it) }, ids.first()) // 128 bits, fits the relay's id rule
        val sealed = sealReport(report.copy(id = ids.first()))
        assertEquals(ids.first(), reportIdFromQr(sealed.qr))
    }
}
