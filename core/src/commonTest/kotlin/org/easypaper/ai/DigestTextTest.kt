package org.easypaper.ai

import org.easypaper.report.BloodPressureDay
import org.easypaper.report.DatedValue
import org.easypaper.report.FactDigest
import org.easypaper.report.IntRange3
import org.easypaper.report.MedicationFact
import org.easypaper.report.NoteFact
import org.easypaper.report.QuestionFact
import org.easypaper.report.WaterDay
import org.easypaper.report.toLocalizedText
import org.easypaper.report.toText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DigestTextTest {
    private val digest = FactDigest(
        requestId = "req-1",
        complaint = "Headache after lunch",
        firstDate = "2026-11-17",
        lastDate = "2026-11-18",
        entryCount = 10,
        bloodPressure = listOf(
            BloodPressureDay("2026-11-17", 2, IntRange3(128, 136, 132), IntRange3(84, 88, 86), IntRange3(72, 76, 74), listOf("e01", "e02")),
        ),
        water = listOf(WaterDay("2026-11-17", 500, listOf("e03"))),
        medications = listOf(
            MedicationFact("Patient's medication", "123", "5 mg", true, listOf("2026-11-17"), listOf("e04")),
            MedicationFact("Other medication 10 mg", null, "10 mg", false, listOf("2026-11-18"), listOf("e05")),
        ),
        questions = listOf(QuestionFact("q1", "How did you sleep?", listOf(DatedValue("2026-11-17", "Not very well", "e06")))),
        notes = listOf(
            NoteFact("diary", "2026-11-17", "I felt tired", null, "e07"),
            NoteFact("food", "2026-11-17", "Soup and bread", "photo/e08", "e08"),
            NoteFact("doctor_question", "2026-11-18", "Should I keep taking aspirin?", null, "e09"),
            NoteFact("food", "2026-11-18", null, "photo/e10", "e10"),
        ),
    )

    @Test
    fun englishAndUnsupportedLanguageKeepTheOriginalEnglishDigest() {
        assertEquals(digest.toText(), digest.toLocalizedText("en"))
        assertEquals(digest.toText(), digest.toLocalizedText("fr"))
        assertTrue(digest.toText().startsWith("Complaint:"))
    }

    @Test
    fun germanLabelsKeepNumbersUnitsAndReferences() {
        val text = digest.toLocalizedText("de")
        assertTrue(text.startsWith("Beschwerde: Headache after lunch. Einträge: 10, von 2026-11-17 bis 2026-11-18."))
        assertTrue("Blutdruck 2026-11-17: 2 Messungen, systolisch 128-136 (Mittelwert 132), diastolisch 84-88 (Mittelwert 86), Puls 72-76 (Mittelwert 74) mmHg [e01,e02]" in text)
        assertTrue("Wasser 2026-11-17: 500 mL [e03]" in text)
        assertTrue("Medikament (vom Patienten bestätigt): Patient's medication, 5 mg, GTIN 123; erfasst am 2026-11-17 [e04]" in text)
        assertTrue("Medikament (NICHT vom Patienten bestätigt): Other medication 10 mg; erfasst am 2026-11-18 [e05]" in text)
        assertTrue("Frage \"How did you sleep?\": 2026-11-17 Not very well [e06]" in text)
        assertTrue("Tagebuch 2026-11-17: I felt tired [e07]" in text)
        assertTrue("Essen 2026-11-17: Soup and bread (Foto photo/e08) [e08]" in text)
        assertTrue("Frage an den Arzt 2026-11-18: Should I keep taking aspirin? [e09]" in text)
        assertTrue("Essen 2026-11-18:  (Foto photo/e10) [e10]" in text)
    }

    @Test
    fun ukrainianLabelsKeepPatientTextAndEveryReference() {
        val text = digest.toLocalizedText("uk")
        for (label in listOf("Скарга", "Записи", "Артеріальний тиск", "середнє", "Вода", "Ліки", "НЕ підтверджено пацієнтом", "Запитання", "Щоденник", "Їжа", "Запитання до лікаря", "фото")) {
            assertTrue(label in text, label)
        }
        for (language in listOf("de", "uk")) {
            val localized = digest.toLocalizedText(language)
            for (patientText in listOf(digest.complaint) + digest.medications.map { it.name } +
                digest.questions.flatMap { q -> listOf(q.text) + q.answers.map { it.value } } + digest.notes.mapNotNull { it.text }) {
                assertTrue(patientText in localized, "$language: $patientText")
            }
            val refs = Regex("""\[e\d+(?:,e\d+)*\]""")
            assertEquals(refs.findAll(digest.toText()).map { it.value }.toList(), refs.findAll(localized).map { it.value }.toList())
        }
    }

    @Test
    fun absentDatesAndPulseAreHandledWithoutAddingFacts() {
        val empty = digest.copy(firstDate = null, lastDate = null, entryCount = 0, bloodPressure = emptyList(), water = emptyList(), medications = emptyList(), questions = emptyList(), notes = emptyList())
        assertEquals("Beschwerde: Headache after lunch. Einträge: 0, von - bis -.", empty.toLocalizedText("de"))
        assertEquals("Скарга: Headache after lunch. Записи: 0, від - до -.", empty.toLocalizedText("uk"))
        val withoutPulse = digest.copy(bloodPressure = digest.bloodPressure.map { it.copy(pulse = null) })
        assertFalse("Puls" in withoutPulse.toLocalizedText("de"))
        assertFalse("пульс" in withoutPulse.toLocalizedText("uk"))
    }
}
