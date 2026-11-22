package org.easypaper.ui.patient.medications

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.demo.Demo
import org.easypaper.diary.EntryProblem
import org.easypaper.model.Medication
import org.easypaper.model.MedicationSource
import org.easypaper.scan.MedicationDb
import org.easypaper.scan.PhotoReading
import org.easypaper.scan.recognize
import org.easypaper.ui.shell.inMemoryPatientRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MedicationsModelTest {
    private val utc = TimeZone.UTC
    private val lastDay = LocalDate(2026, 11, 21) // day 5 of the demo (17–21 Nov)
    private val widget = "m"

    @Test
    fun demoListShowsTheUncheckedScanFirstAndCountsIntakes() {
        val items = medicinesFor(Demo.record, widget, lastDay, utc)
        assertEquals(2, items.size)
        val (draft, dolo) = items
        assertTrue(draft.isDraft)
        assertEquals("7680587170018", draft.code)
        assertFalse(dolo.isDraft)
        assertEquals("7680567200025", dolo.code)
        assertEquals(emptyList(), dolo.takenToday)
        assertEquals(Demo.record.entries.filter { it.widgetId == widget }.map { it.at }.sorted()[2], dolo.lastTaken)
    }

    @Test
    fun scanChoicesUseFrenchNamesOnlyForFrench() {
        val db = MedicationDb()
        val match = db.recognize(PhotoReading(listOf("7680623550019"), ""))
        val info = match.candidates.single()
        for (locale in listOf("fr", "fr-CH")) {
            assertEquals(info.nameFr, scanChoices(match, locale).single().name)
        }
        for (locale in listOf("en", "de", "uk", "ru")) {
            assertEquals(info.nameDe, scanChoices(match, locale).single().name)
        }
    }

    @Test
    fun scanConfirmTakeAndTypeThroughTheRepository() = runTest {
        val repo = inMemoryPatientRepository()
        repo.start(Demo.request)
        val id = Demo.request.id
        val at = Demo.record.entries.maxOf { it.at }
        fun list() = medicinesFor(repo.records.value.single(), widget, lastDay, utc)

        // a package photo read as an EAN -> exactly one choice -> saved as a draft (patient closed the sheet)
        val choice = scanChoices(MedicationDb().recognize(PhotoReading(listOf("7680623550019"), "")), "de").single()
        assertEquals(MedicationSource.SCAN, choice.source)
        assertEquals(emptyList(), repo.addMedication(id, widget, choice, at))
        assertTrue(list().single().isDraft)

        // confirming keeps the printed strength unless the patient writes dose and frequency
        assertEquals(emptyList(), repo.confirm(id, widget, list().single(), "1 g, 3x daily", at + 1))
        val confirmed = list().single()
        assertFalse(confirmed.isDraft)
        assertEquals(emptyList(), confirmed.draftIds, "draft removed")
        assertEquals("1 g, 3x daily", confirmed.dose)
        assertEquals(listOf(at + 1), confirmed.takenToday)

        assertEquals(emptyList(), repo.takeNow(id, widget, confirmed, at + 2))
        assertEquals(listOf(at + 1, at + 2), list().single().takenToday)
        assertTrue(repo.records.value.single().entries.all { (it.payload as Medication).confirmed })

        // nothing recognised: the patient types it; an empty name is refused by the record's check
        assertEquals(listOf(EntryProblem.EMPTY_MEDICATION_NAME), repo.addMedication(id, widget, typedMedication("  ", ""), at))
        assertEquals(emptyList(), repo.addMedication(id, widget, typedMedication("Vitamin D", ""), at))
        assertEquals(listOf("ACETALGIN Filmtabl 1 g 16 Stk", "Vitamin D"), list().map { it.name })
        assertEquals(null, list().last().dose)
    }
}

class SideEffectQuestionsTest {
    @Test
    fun confirmedMedicinesWithLeafletDataGetThreeQuestionsOnceADay() = kotlinx.coroutines.test.runTest {
        val repo = inMemoryPatientRepository()
        repo.start(Demo.request)
        val id = Demo.request.id
        val day = LocalDate(2026, 11, 21)
        val at = Demo.record.entries.maxOf { it.at }
        val db = MedicationDb()
        // the demo: DOLO SPEDIFEN (ibuprofen) confirmed, PANTOPRAZOL scanned but not confirmed
        for (e in Demo.record.entries.filter { it.widgetId == "m" }) repo.add(id, e)
        fun questions() = repo.records.value.single().let { r -> sideEffectQuestions(r, "m", medicinesFor(r, "m", day, TimeZone.UTC), db, day, TimeZone.UTC) }

        val q = questions().single()
        assertEquals("7680567200025", q.item.code)
        assertEquals(listOf("stomach_pain", "heartburn", "nausea"), q.effects.map { it.key })
        assertFalse(q.answeredToday)

        assertEquals(emptyList(), repo.saveSideEffects(id, "m", q.item, mapOf("stomach_pain" to true, "heartburn" to false, "nausea" to false), at))
        assertTrue(questions().single().answeredToday)
        val saved = repo.records.value.single().entries.last().payload as org.easypaper.model.SideEffectCheck
        assertEquals(mapOf("stomach_pain" to "yes", "heartburn" to "no", "nausea" to "no"), saved.answers)
    }
}
