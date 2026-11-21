package org.easypaper.report

import kotlinx.coroutines.test.runTest
import org.easypaper.demo.Demo
import org.easypaper.model.EasyPaperJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DigestTest {
    private val digest = Demo.record.factDigest()

    // Entry ids of the demo, day by day: bp e01,e02 · water e03-e05 · answers e06 | bp e07,e08 · water e09-e11 ·
    // answers e12 | bp e13,e14 · water e15,e16 · answers e17 | bp e18,e19 · water e20,e21 · answers e22 |
    // bp e23,e24 · water e25 · answers e26 | meds e27-e30 · food e31,e32 · diary e33,e34 · question e35.

    @Test
    fun bloodPressureMatchesHandCalculation() {
        assertEquals(5, digest.bloodPressure.size)
        assertEquals(
            BloodPressureDay("2026-11-17", 2, IntRange3(128, 136, 132), IntRange3(84, 88, 86), IntRange3(72, 76, 74), listOf("e01", "e02")),
            digest.bloodPressure[0],
        )
        // 131+142 = 273 -> 136.5 -> 137 (half up); 85+91 -> 88; 70+80 -> 75
        assertEquals(
            BloodPressureDay("2026-11-18", 2, IntRange3(131, 142, 137), IntRange3(85, 91, 88), IntRange3(70, 80, 75), listOf("e07", "e08")),
            digest.bloodPressure[1],
        )
        // 125+138 -> 131.5 -> 132; 82+89 -> 85.5 -> 86; 68+75 -> 71.5 -> 72
        assertEquals(IntRange3(125, 138, 132), digest.bloodPressure[2].systolic)
        assertEquals(IntRange3(82, 89, 86), digest.bloodPressure[2].diastolic)
        assertEquals(IntRange3(68, 75, 72), digest.bloodPressure[2].pulse)
    }

    @Test
    fun waterPerDay() {
        assertEquals(listOf(1000, 1200, 500, 1250, 1000), digest.water.map { it.totalMl })
        assertEquals(listOf("e03", "e04", "e05"), digest.water[0].refs)
    }

    @Test
    fun medicationsConfirmedFirstAndKeptApart() {
        assertEquals(2, digest.medications.size)
        val (dolo, panto) = digest.medications
        assertTrue(dolo.confirmed && dolo.name.startsWith("DOLO SPEDIFEN"))
        assertEquals("400 mg", dolo.dose)
        assertEquals(listOf("2026-11-17", "2026-11-18", "2026-11-20"), dolo.dates)
        assertEquals(listOf("e27", "e28", "e29"), dolo.refs)
        assertFalse(panto.confirmed)
        assertEquals("7680587170018", panto.code)
        assertEquals(listOf("e30"), panto.refs)
    }

    @Test
    fun questionsUseTheDoctorsWording() {
        val intensity = digest.questions.single { it.questionId == "q2" }
        assertEquals("Headache intensity", intensity.text)
        assertEquals(listOf("6", "7", "0", "5", "4"), intensity.answers.map { it.value })
        assertEquals(4, digest.questions.single { it.questionId == "q3" }.answers.size) // not answered on day 3
    }

    @Test
    fun everyEntryIsReferencedAndEveryRefExists() {
        val refs = digest.bloodPressure.flatMap { it.refs } + digest.water.flatMap { it.refs } +
            digest.medications.flatMap { it.refs } + digest.questions.flatMap { q -> q.answers.map { it.ref } } +
            digest.notes.map { it.ref }
        assertEquals(Demo.record.entries.map { it.id }.toSet(), refs.toSet())
        assertEquals(35, digest.entryCount)
        assertEquals("2026-11-17" to "2026-11-21", digest.firstDate to digest.lastDate)
    }

    @Test
    fun textHasFactsAndNoJudgement() {
        val text = digest.toText()
        assertTrue(
            "Blood pressure 2026-11-17: 2 readings, systolic 128-136 (avg 132), diastolic 84-88 (avg 86), pulse 72-76 (avg 74) mmHg [e01,e02]" in text,
            text,
        )
        assertTrue("Water 2026-11-19: 500 mL [e15,e16]" in text)
        assertTrue("Medication (NOT confirmed by patient)" in text)
        assertTrue("Food 2026-11-18: Coffee 3 cups, pasta (photo demo-photo-1) [e31]" in text)
        val lower = text.lowercase()
        for (word in listOf("high", "low", "normal", "elevated", "abnormal", "risk", "should", "must", "hypertension")) {
            assertFalse(Regex("\\b$word\\b").containsMatchIn(lower), "judgement word '$word' in:\n$text")
        }
        for (stem in listOf("diagnos", "recommend", "advis")) assertFalse(stem in lower, "'$stem' in:\n$text")
    }

    @Test
    fun patientTimeZoneMovesLateEntriesToTheNextDay() {
        // answers are recorded at 21:00 UTC; for a patient at UTC+3 that is 00:00 of the next day
        val shifted = Demo.record.factDigest(utcOffsetMinutes = 180)
        assertEquals("2026-11-18", shifted.questions.single { it.questionId == "q1" }.answers.first().date)
        assertEquals("2026-11-17", shifted.bloodPressure.first().date) // 20:00 UTC -> 23:00, same day
    }

    @Test
    fun digestJsonRoundTrip() {
        val json = EasyPaperJson.encodeToString(FactDigest.serializer(), digest)
        assertEquals(digest, EasyPaperJson.decodeFromString(FactDigest.serializer(), json))
    }

    @Test
    fun demoReportSurvivesTransfer() = runTest {
        val sealed = sealReport(Demo.report)
        assertEquals(Demo.report, openReport(sealed.bytes, sealed.qr))
    }
}
