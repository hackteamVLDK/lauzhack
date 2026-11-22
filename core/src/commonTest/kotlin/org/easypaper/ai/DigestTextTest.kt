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
        assertEquals(digest.toText(), digest.toLocalizedText("es"))
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
        for (language in listOf("de", "uk", "fr", "ru")) {
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
    fun frenchAndRussianLabelsKeepNumbersUnitsAndPatientWords() {
        val fr = digest.toLocalizedText("fr")
        assertTrue(fr.startsWith("Motif de consultation: Headache after lunch. Entrées: 10, du 2026-11-17 au 2026-11-18."))
        assertTrue("Tension artérielle 2026-11-17: 2 mesures, systolique 128-136 (moyenne 132), diastolique 84-88 (moyenne 86), pouls 72-76 (moyenne 74) mmHg [e01,e02]" in fr)
        assertTrue("Eau 2026-11-17: 500 mL [e03]" in fr)
        assertTrue("Médicament (confirmé par le patient): Patient's medication, 5 mg, GTIN 123; noté le 2026-11-17 [e04]" in fr)
        assertTrue("Médicament (NON confirmé par le patient): Other medication 10 mg; noté le 2026-11-18 [e05]" in fr)
        assertTrue("Question \"How did you sleep?\": 2026-11-17 Not very well [e06]" in fr)
        assertTrue("Journal 2026-11-17: I felt tired [e07]" in fr)
        assertTrue("Repas 2026-11-17: Soup and bread (photo photo/e08) [e08]" in fr)
        assertTrue("Question au médecin 2026-11-18: Should I keep taking aspirin? [e09]" in fr)
        assertTrue("Repas 2026-11-18:  (photo photo/e10) [e10]" in fr)

        val ru = digest.toLocalizedText("ru")
        assertTrue(ru.startsWith("Жалоба: Headache after lunch. Записи: 10, с 2026-11-17 по 2026-11-18."))
        assertTrue("Артериальное давление 2026-11-17: измерений: 2, систолическое 128-136 (среднее 132), диастолическое 84-88 (среднее 86), пульс 72-76 (среднее 74) mmHg [e01,e02]" in ru)
        assertTrue("Вода 2026-11-17: 500 mL [e03]" in ru)
        assertTrue("Лекарство (подтверждено пациентом): Patient's medication, 5 mg, GTIN 123; записано 2026-11-17 [e04]" in ru)
        assertTrue("Лекарство (НЕ подтверждено пациентом): Other medication 10 mg; записано 2026-11-18 [e05]" in ru)
        assertTrue("Вопрос \"How did you sleep?\": 2026-11-17 Not very well [e06]" in ru)
        assertTrue("Дневник 2026-11-17: I felt tired [e07]" in ru)
        assertTrue("Еда 2026-11-17: Soup and bread (фото photo/e08) [e08]" in ru)
        assertTrue("Вопрос врачу 2026-11-18: Should I keep taking aspirin? [e09]" in ru)
        assertTrue("Еда 2026-11-18:  (фото photo/e10) [e10]" in ru)
    }

    @Test
    fun absentDatesAndPulseAreHandledWithoutAddingFacts() {
        val empty = digest.copy(firstDate = null, lastDate = null, entryCount = 0, bloodPressure = emptyList(), water = emptyList(), medications = emptyList(), questions = emptyList(), notes = emptyList())
        assertEquals("Beschwerde: Headache after lunch. Einträge: 0, von - bis -.", empty.toLocalizedText("de"))
        assertEquals("Скарга: Headache after lunch. Записи: 0, від - до -.", empty.toLocalizedText("uk"))
        assertEquals("Motif de consultation: Headache after lunch. Entrées: 0, du - au -.", empty.toLocalizedText("fr"))
        assertEquals("Жалоба: Headache after lunch. Записи: 0, с - по -.", empty.toLocalizedText("ru"))
        val withoutPulse = digest.copy(bloodPressure = digest.bloodPressure.map { it.copy(pulse = null) })
        assertFalse("Puls" in withoutPulse.toLocalizedText("de"))
        assertFalse("пульс" in withoutPulse.toLocalizedText("uk"))
        assertFalse("pouls" in withoutPulse.toLocalizedText("fr"))
        assertFalse("пульс" in withoutPulse.toLocalizedText("ru"))
    }
}
