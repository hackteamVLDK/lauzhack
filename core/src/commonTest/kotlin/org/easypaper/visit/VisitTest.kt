package org.easypaper.visit

import kotlinx.coroutines.test.runTest
import org.easypaper.demo.Demo
import org.easypaper.model.BloodPressure
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.DiaryWidget
import org.easypaper.model.EasyPaperJson
import org.easypaper.model.Entry
import org.easypaper.model.PatientRecord
import org.easypaper.report.MedicationFact
import org.easypaper.report.Report
import org.easypaper.report.factDigest
import org.easypaper.report.openReport
import org.easypaper.report.sealReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisitTest {
    private val demo = demoVisitReport()

    @Test
    fun demoReportOpensFromQrAndEverySummaryLineLeadsToOriginals() = runTest {
        val sealed = sealReport(demo)
        val opened = openReport(sealed.bytes, sealed.qr)
        assertEquals(demo, opened)

        val summary = parseSummary(opened)
        assertEquals(6, summary.lines.size)
        assertEquals(emptyList(), summary.unknownRefs)
        assertEquals(emptyList(), summary.uncitedLines)
        assertEquals(opened.record.entries.map { it.id }.toSet(), summary.citedIds.toSet()) // every entry is cited
    }

    @Test
    fun demoSummaryNumbersMatchTheFactDigest() {
        val digest = Demo.record.factDigest(DEMO_UTC_OFFSET_MINUTES)
        val bp = digest.bloodPressureStats()!!
        val bpLine = "${bp.systolic.first}–${bp.systolic.last}/${bp.diastolic.first}–${bp.diastolic.last} mmHg in ${bp.readings} readings"
        assertTrue(bpLine in DEMO_SUMMARY, bpLine)
        val water = digest.water.map { it.totalMl }
        assertTrue("Water ${water.min()}–${water.max()} mL per day, ${digest.fluidStats()!!.mlPerDay} mL on average" in DEMO_SUMMARY)
        val headacheDays = digest.questions.single { it.questionId == "q1" }.answers.count { it.value == "yes" }
        assertTrue("Headache on $headacheDays of ${Demo.request.days} days" in DEMO_SUMMARY)
    }

    @Test
    fun originalsFollowTheRequestOrderAndTime() {
        val originals = demo.record.originalsByWidget()
        assertEquals(demo.record.request.widgets.map { it.id }, originals.map { it.first.id })
        originals.forEach { (_, entries) -> assertEquals(entries.sortedBy { it.at }, entries) }
        assertEquals(10, originals.first { it.first.id == "bp" }.second.size)
    }

    private val record = PatientRecord(
        Demo.request,
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
    fun prefillComputesFactsFromTheDigest() {
        val form = prefillClinicForm(demo, utcOffsetMinutes = DEMO_UTC_OFFSET_MINUTES)
        assertEquals(
            listOf(
                FormFieldKey.COMPLAINT, FormFieldKey.HISTORY, FormFieldKey.BLOOD_PRESSURE,
                FormFieldKey.MEDICATIONS, FormFieldKey.FLUID_INTAKE, FormFieldKey.PATIENT_QUESTIONS,
            ),
            form.fields.map { it.key },
        )
        assertEquals(Demo.request.complaint, form.field(FormFieldKey.COMPLAINT).draft)
        assertEquals(
            "10 readings, 2026-11-17 – 2026-11-21: 125–142/81–91 mmHg, pulse 68–80/min",
            form.field(FormFieldKey.BLOOD_PRESSURE).draft,
        )
        assertEquals(10, form.field(FormFieldKey.BLOOD_PRESSURE).sourceIds.size)
        assertEquals("990 mL/day on average (5 days, 11 entries)", form.field(FormFieldKey.FLUID_INTAKE).draft)
        assertEquals(
            "DOLO SPEDIFEN forte Filmtabl 400 mg 10 Stk, 400 mg\n" +
                "PANTOPRAZOL Streuli Filmtabl 20 mg 15 Stk, 20 mg (not confirmed by patient)",
            form.field(FormFieldKey.MEDICATIONS).draft,
        )
        assertEquals(listOf("e27", "e28", "e29", "e30"), form.field(FormFieldKey.MEDICATIONS).sourceIds)
        assertEquals("– Can I take ibuprofen together with coffee?", form.field(FormFieldKey.PATIENT_QUESTIONS).draft)
        val history = form.field(FormFieldKey.HISTORY)
        assertFalse('[' in history.draft)
        assertEquals(parseSummary(demo).citedIds, history.sourceIds)
        assertTrue(form.fields.all { it.state == FieldState.DRAFT })
    }

    @Test
    fun fieldsOnlyForRequestedWidgets() {
        val request = Demo.request.copy(widgets = listOf(BloodPressureWidget("bp", "Blood pressure"), DiaryWidget("d", "Diary")))
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

        val decided = allRejected
            .reopen(FormFieldKey.COMPLAINT).confirm(FormFieldKey.COMPLAINT)
            .reopen(FormFieldKey.BLOOD_PRESSURE).confirm(FormFieldKey.BLOOD_PRESSURE)
        assertTrue(decided.canSave)
        val saved = decided.save(savedAt = 42)
        assertEquals(listOf(FormFieldKey.COMPLAINT, FormFieldKey.BLOOD_PRESSURE), saved.fields.map { it.key })
        assertEquals(demo.id, saved.reportId)
        assertEquals(10, saved.fields[1].sourceIds.size)
        val json = EasyPaperJson.encodeToString(SavedClinicForm.serializer(), saved)
        assertEquals(saved, EasyPaperJson.decodeFromString(SavedClinicForm.serializer(), json))
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
    fun medicineConfirmedOnceIsNotFlagged() {
        val lines = medicationLines(
            listOf(
                MedicationFact("Spedifen", "1", "400 mg", confirmed = true, dates = listOf("2026-11-18"), refs = listOf("b")),
                MedicationFact("spedifen ", "1", "400 mg", confirmed = false, dates = listOf("2026-11-17"), refs = listOf("a")),
                MedicationFact("Pantoprazol", "2", "20 mg", confirmed = false, dates = listOf("2026-11-19"), refs = listOf("c")),
            ),
        )
        assertEquals(listOf(false, true), lines.map { it.unconfirmed })
    }
}
