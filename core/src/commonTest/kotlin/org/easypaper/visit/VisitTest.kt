package org.easypaper.visit

import kotlinx.coroutines.test.runTest
import org.easypaper.model.BloodPressure
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.DiaryWidget
import org.easypaper.model.EasyPaperJson
import org.easypaper.model.Entry
import org.easypaper.model.Medication
import org.easypaper.model.MedicationSource
import org.easypaper.model.PatientRecord
import org.easypaper.model.Water
import org.easypaper.report.Report
import org.easypaper.report.openReport
import org.easypaper.report.sealReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisitTest {
    private val demo = demoReport()

    @Test
    fun demoReportOpensFromQrAndEverySummaryLineLeadsToOriginals() = runTest {
        val sealed = sealReport(demo)
        val opened = openReport(sealed.bytes, sealed.qr)
        assertEquals(demo, opened)

        val summary = parseSummary(opened)
        assertEquals(8, summary.lines.size)
        assertEquals(emptyList(), summary.unknownRefs)
        assertEquals(emptyList(), summary.uncitedLines)
        val ids = opened.record.entries.map { it.id }.toSet()
        assertTrue(summary.citedIds.all { it in ids })
    }

    @Test
    fun originalsFollowTheRequestOrderAndTime() {
        val originals = demo.record.originalsByWidget()
        assertEquals(demo.record.request.widgets.map { it.id }, originals.map { it.first.id })
        originals.forEach { (_, entries) -> assertEquals(entries.sortedBy { it.at }, entries) }
        assertEquals(10, originals.first { it.first.id == "bp" }.second.size)
    }

    private val record = PatientRecord(
        demo.record.request,
        listOf(
            Entry("e1", "bp", 1_000, BloodPressure(130, 85, 70)),
            Entry("e2", "bp", 2_000, BloodPressure(120, 80)),
        ),
    )

    @Test
    fun summaryLinksAreSplitFromText() {
        val parsed = parseSummary("Blood pressure 120–130/80–85 mmHg [e1, e2].\n\nFeels tired [e9]\nSleeps badly", record)
        assertEquals(3, parsed.lines.size)

        val first = parsed.lines[0]
        assertEquals("Blood pressure 120–130/80–85 mmHg.", first.text)
        assertEquals(listOf("e1", "e2"), first.sources.map { it.id })
        assertTrue(first.cited)

        val second = parsed.lines[1]
        assertEquals(listOf(SummaryPart.Text("Feels tired "), SummaryPart.MissingSource("e9")), second.parts)
        assertFalse(second.cited)
        assertEquals(listOf("e9"), parsed.unknownRefs)

        assertEquals(listOf(parsed.lines[1], parsed.lines[2]), parsed.uncitedLines)
        assertEquals(listOf("e1", "e2"), parsed.citedIds)
    }

    @Test
    fun bracketsThatAreNotLinksStayText() {
        val line = parseSummary("Pain [left side, sometimes both] [e1]", record).lines.single()
        assertEquals("Pain [left side, sometimes both]", line.text)
        assertEquals(listOf("e1"), line.sources.map { it.id })
    }

    @Test
    fun noSummaryMeansOriginalsOnly() {
        assertTrue(parseSummary(null, record).lines.isEmpty())
        assertTrue(parseSummary("  \n ", record).lines.isEmpty())
    }

    @Test
    fun prefillComputesFactsFromTheRecord() {
        val form = prefillClinicForm(demo, utcOffsetMinutes = DEMO_UTC_OFFSET_MINUTES)
        assertEquals(
            listOf(
                FormFieldKey.COMPLAINT, FormFieldKey.HISTORY, FormFieldKey.BLOOD_PRESSURE,
                FormFieldKey.MEDICATIONS, FormFieldKey.FLUID_INTAKE, FormFieldKey.PATIENT_QUESTIONS,
            ),
            form.fields.map { it.key },
        )
        assertEquals("Headache in the morning for two weeks", form.field(FormFieldKey.COMPLAINT).draft)
        assertEquals(
            "10 readings, 2026-11-22 – 2026-11-26: 121–145/78–92 mmHg, pulse 64–80/min",
            form.field(FormFieldKey.BLOOD_PRESSURE).draft,
        )
        assertEquals(10, form.field(FormFieldKey.BLOOD_PRESSURE).sourceIds.size)
        assertEquals("1100 mL/day on average (5 days, 14 entries)", form.field(FormFieldKey.FLUID_INTAKE).draft)
        assertEquals(
            "DOLO SPEDIFEN forte Filmtabl 400 mg 10 Stk, 400 mg\n" +
                "Magnesium, 300 mg in the evening\n" +
                "ACETALGIN Filmtabl 1 g 16 Stk, 1 g (scan not confirmed by patient)",
            form.field(FormFieldKey.MEDICATIONS).draft,
        )
        assertEquals(2, form.field(FormFieldKey.PATIENT_QUESTIONS).draft.lines().size)
        val history = form.field(FormFieldKey.HISTORY)
        assertFalse('[' in history.draft)
        assertEquals(parseSummary(demo).citedIds, history.sourceIds)
        assertTrue(form.fields.all { it.state == FieldState.DRAFT })
    }

    @Test
    fun fieldsOnlyForRequestedWidgets() {
        val request = demo.record.request.copy(widgets = listOf(BloodPressureWidget("bp", "Blood pressure"), DiaryWidget("diary", "Diary")))
        val form = prefillClinicForm(Report(id = "r", createdAt = 0, record = PatientRecord(request)))
        assertEquals(listOf(FormFieldKey.COMPLAINT, FormFieldKey.HISTORY, FormFieldKey.BLOOD_PRESSURE), form.fields.map { it.key })
        assertEquals("No entries", form.field(FormFieldKey.BLOOD_PRESSURE).draft)
        assertEquals("", form.field(FormFieldKey.HISTORY).draft) // no AI summary yet
    }

    @Test
    fun nothingIsSavedWithoutConfirmation() {
        val form = prefillClinicForm(demo)
        assertFalse(form.canSave)
        assertFailsWith<IllegalStateException> { form.save(savedAt = 1) }

        // All rejected: still nothing confirmed, nothing to save.
        val allRejected = form.fields.fold(form) { f, field -> f.reject(field.key) }
        assertFalse(allRejected.canSave)
        assertFailsWith<IllegalStateException> { allRejected.save(savedAt = 1) }

        val decided = form.fields.fold(form) { f, field -> f.reject(field.key) }
            .reopen(FormFieldKey.COMPLAINT).confirm(FormFieldKey.COMPLAINT)
            .reopen(FormFieldKey.BLOOD_PRESSURE).confirm(FormFieldKey.BLOOD_PRESSURE)
        assertTrue(decided.canSave)
        val saved = decided.save(savedAt = 42)
        assertEquals(listOf(FormFieldKey.COMPLAINT, FormFieldKey.BLOOD_PRESSURE), saved.fields.map { it.key })
        assertEquals(demo.id, saved.reportId)
        assertEquals(10, saved.fields[1].sourceIds.size)
        assertEquals(saved, EasyPaperJson.decodeFromString(SavedClinicForm.serializer(), EasyPaperJson.encodeToString(SavedClinicForm.serializer(), saved)))
    }

    @Test
    fun editingTakesBackTheConfirmation() {
        val confirmed = prefillClinicForm(demo).confirm(FormFieldKey.COMPLAINT)
        assertEquals(FieldState.CONFIRMED, confirmed.field(FormFieldKey.COMPLAINT).state)

        val edited = confirmed.edit(FormFieldKey.COMPLAINT, "Morning headache, 2 weeks, temples")
        val field = edited.field(FormFieldKey.COMPLAINT)
        assertEquals(FieldState.DRAFT, field.state)
        assertTrue(field.edited)
        assertTrue(FormFieldKey.COMPLAINT in edited.undecided)

        assertFailsWith<IllegalArgumentException> { edited.edit(FormFieldKey.HISTORY, " ").confirm(FormFieldKey.HISTORY) }
        val saved = edited.fields.fold(edited) { f, it -> if (it.key == FormFieldKey.COMPLAINT) f.confirm(it.key) else f.reject(it.key) }.save(1)
        assertTrue(saved.fields.single().editedByDoctor)
        assertEquals("Morning headache, 2 weeks, temples", saved.fields.single().value)
    }

    @Test
    fun medicationScannedButLaterConfirmedIsNotFlagged() {
        val lines = medicationLines(
            listOf(
                Entry("a", "meds", 1, Medication("Spedifen", "1", "400 mg", MedicationSource.SCAN, confirmed = false)),
                Entry("b", "meds", 2, Medication("spedifen ", "1", "400 mg", MedicationSource.SCAN, confirmed = true)),
            ),
        )
        assertFalse(lines.single().unconfirmedScan)
    }

    @Test
    fun localDateUsesThePatientsOffset() {
        val lateEvening = 1_795_390_200_000L // 2026-11-22T23:30Z
        assertEquals("2026-11-22", localDate(lateEvening, 0))
        assertEquals("2026-11-23", localDate(lateEvening, 60))
        assertEquals(1100, fluidStats(demo.record.entries.filter { it.payload is Water }, DEMO_UTC_OFFSET_MINUTES).mlPerDay)
    }
}
