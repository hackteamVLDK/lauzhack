package org.easypaper.ui.doctor

import org.easypaper.ai.SummaryLine
import org.easypaper.ai.SummaryResult
import org.easypaper.demo.Demo
import org.easypaper.model.Entry
import org.easypaper.model.PatientRecord
import org.easypaper.model.Water
import org.easypaper.visit.FieldState
import org.easypaper.visit.FormFieldKey
import org.easypaper.visit.demoVisitReport
import org.easypaper.visit.prefillClinicForm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisitStateTest {
    private val wording = ResourceFormWording(
        bloodPressure = "%1\$s: %2\$s/%3\$s mmHg, readings: %4\$d",
        pulse = ", pulse %1\$s/min",
        fluid = "%1\$d ml per day on average, days: %2\$d",
        notConfirmed = "not confirmed by the patient",
        none = "No entries",
        rangeOneMonth = "%1\$d–%2\$d %3\$s",
        rangeTwoMonths = "%1\$d %2\$s – %3\$d %4\$s",
        monthsShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
    )

    @Test
    fun theReportsSummaryLinksEveryLineToOriginals() {
        val summary = visitSummary(demoVisitReport(), ai = null)
        assertEquals(SummarySource.Report, summary.source)
        assertEquals(6, summary.lines.size)
        assertTrue(summary.lines.all { it.supported })
        assertEquals(Demo.record.entries.map { it.id }.toSet(), summary.sourceIds.toSet())
        assertFalse(summary.text.contains('['))
    }

    @Test
    fun withoutASummaryTheFactsAreShownWithTheirSources() {
        val summary = visitSummary(Demo.report, ai = null)
        assertEquals(SummarySource.Facts, summary.source)
        assertTrue(summary.lines.isNotEmpty())
        assertTrue(summary.lines.filter { it.supported }.all { it.sources.isNotEmpty() })
        assertTrue(summary.lines.any { "Blood pressure" in it.text })
    }

    private val aiResult = SummaryResult(
        summary = "",
        lines = listOf(
            SummaryLine("Blood pressure 125–142/81–91 mmHg [e01, e02]", listOf("e01", "e02"), supported = true),
            SummaryLine("Sleeps badly", emptyList(), supported = false),
            SummaryLine("Headache on 4 days [e06, e99]", listOf("e06"), supported = false),
        ),
        usedAi = true,
        photoDescriptions = emptyMap(),
        usage = emptyList(),
    )

    @Test
    fun aiLinesWithoutAllTheirSourcesAreShownAsUnsupported() {
        val summary = visitSummary(Demo.report, aiResult)
        assertEquals(SummarySource.Ai, summary.source)
        assertEquals("Blood pressure 125–142/81–91 mmHg", summary.lines[0].text)
        assertEquals(listOf("e01", "e02"), summary.lines[0].sources.map { it.id })
        assertTrue(summary.lines[0].supported)
        assertFalse(summary.lines[1].supported)
        assertFalse(summary.lines[2].supported) // e99 does not exist
    }

    @Test
    fun theHistoryFieldFollowsTheSummaryAndNothingIsSavedUntilEveryFieldIsDecided() {
        val state = VisitState(demoVisitReport(), wording)
        assertEquals(state.summary.text, state.form.field(FormFieldKey.HISTORY).value)
        assertNull(state.save(now = 1))

        state.confirm(FormFieldKey.COMPLAINT, now = 100)
        assertEquals(100L, state.confirmedAt[FormFieldKey.COMPLAINT])
        state.edit(FormFieldKey.HISTORY, "Morning headache, temples, 5 of 5 days")
        state.confirm(FormFieldKey.HISTORY, now = 200)
        for (key in listOf(FormFieldKey.BLOOD_PRESSURE, FormFieldKey.MEDICATIONS, FormFieldKey.FLUID_INTAKE)) state.confirm(key, now = 300)
        assertNull(state.save(now = 2)) // patient's questions still undecided
        assertEquals(5, state.decided)

        state.remove(FormFieldKey.PATIENT_QUESTIONS)
        val saved = assertNotNull(state.save(now = 400))
        assertEquals(saved, state.saved)
        assertEquals(5, saved.fields.size)
        assertTrue(saved.fields.single { it.key == FormFieldKey.HISTORY }.editedByDoctor)

        // Any later change takes the saved state back: the doctor saves again.
        state.reopen(FormFieldKey.COMPLAINT)
        assertNull(state.saved)
        assertNull(state.confirmedAt[FormFieldKey.COMPLAINT])
    }

    @Test
    fun anAiSummaryReplacesOnlyTheHistoryDraft() {
        val state = VisitState(Demo.report, wording)
        state.confirm(FormFieldKey.COMPLAINT, now = 1)
        state.confirm(FormFieldKey.HISTORY, now = 2)
        state.applyAi(aiResult)
        assertEquals(SummarySource.Ai, state.summary.source)
        val history = state.form.field(FormFieldKey.HISTORY)
        assertEquals(FieldState.DRAFT, history.state)
        assertTrue(history.value.startsWith("Blood pressure 125–142/81–91 mmHg"))
        assertEquals(listOf("e01", "e02", "e06"), history.sourceIds)
        assertEquals(FieldState.CONFIRMED, state.form.field(FormFieldKey.COMPLAINT).state)
    }

    @Test
    fun fieldTextsComeFromTheLanguagesTemplates() {
        val form = prefillClinicForm(Demo.report, wording, utcOffsetMinutes = 60)
        assertEquals("17–21 Nov: 125–142/81–91 mmHg, readings: 10, pulse 68–80/min", form.field(FormFieldKey.BLOOD_PRESSURE).value)
        assertEquals("990 ml per day on average, days: 5", form.field(FormFieldKey.FLUID_INTAKE).value)
        assertTrue("PANTOPRAZOL Streuli Filmtabl 20 mg 15 Stk (not confirmed by the patient)" in form.field(FormFieldKey.MEDICATIONS).value)
        assertEquals("Tag 5 von 7", "Tag %1\$d von %2\$d".fill(5, 7))
    }

    @Test
    fun pressureChartSeriesIsInTimeOrderWithARoundedRange() {
        val series = assertNotNull(pressureSeries(Demo.record.entries.shuffled(kotlin.random.Random(3))))
        assertEquals(10, series.readings.size)
        assertEquals(series.readings.sortedBy { it.first }, series.readings)
        assertEquals(80, series.yMin) // lowest diastolic 81
        assertEquals(150, series.yMax) // highest systolic 142
        assertNull(pressureSeries(Demo.record.entries.filter { it.widgetId != "bp" }))
    }

    @Test
    fun anAnswerWithoutAiIsShownAsFacts() {
        val summary = visitSummary(Demo.report, aiResult.copy(usedAi = false, note = "AI is off on this server"))
        assertEquals(SummarySource.Facts, summary.source)
    }

    @Test
    fun aSummaryThatCameWithTheReportLosesAdvice() {
        val report = demoVisitReport().copy(summary = "You should take ibuprofen [e27]" + NL + "Water 990 mL per day [e25]")
        val summary = visitSummary(report, ai = null)
        assertEquals(listOf("Water 990 mL per day"), summary.lines.map { it.text })
    }

    @Test
    fun waterDaysFollowTheLocalTimeZone() {
        val request = Demo.request
        // 00:30 and 08:00 on 21 Nov in CET (UTC+1): one local day, two UTC days.
        val entries = listOf(
            Entry("w1", "w", 1_795_217_400_000L, Water(250)), // 2026-11-20T23:30Z
            Entry("w2", "w", 1_795_244_400_000L, Water(125)), // 2026-11-21T07:00Z
        )
        val report = org.easypaper.report.Report(id = "r-tz", createdAt = 0, record = PatientRecord(request, entries))
        assertEquals("375 ml per day on average, days: 1", VisitState(report, wording, utcOffsetMinutes = 60).form.field(FormFieldKey.FLUID_INTAKE).value)
        assertEquals("187 ml per day on average, days: 2", VisitState(report, wording, utcOffsetMinutes = 0).form.field(FormFieldKey.FLUID_INTAKE).value)
    }

    private companion object { const val NL = "\n" }
}
