package org.easypaper.ui.doctor

import kotlinx.datetime.LocalDate
import org.easypaper.invite.DraftError
import org.easypaper.invite.InviteResult
import org.easypaper.invite.inviteLink
import org.easypaper.invite.parseInvite
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.QuestionKind
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.WaterWidget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RequestBuilderStateTest {
    private val today = LocalDate(2026, 11, 21)
    private val titles = mapOf(
        WidgetKind.Questionnaire to "Daily questions", WidgetKind.BloodPressure to "Blood pressure", WidgetKind.Water to "Water",
        WidgetKind.Food to "Food", WidgetKind.Medicines to "Medicines", WidgetKind.Diary to "How you feel",
        WidgetKind.DoctorQuestions to "Question for the doctor",
    )
    private val ends = listOf("none", "worst")

    private fun filled() = RequestBuilderState(
        doctorName = "Dr. Anna Keller",
        startQuestions = listOf(QuestionDraft("Headache today?", QuestionKind.YES_NO), QuestionDraft("How strong?", QuestionKind.SCALE)),
    ).apply { complaint = "Headache in the morning for two weeks" }

    @Test
    fun theDefaultRequestIsWhatTheDoctorSees() {
        val request = assertNotNull(filled().create(today, titles, "en", now = 1, scaleEnds = ends, id = "r1"))
        assertEquals(listOf("q", "bp", "m", "d", "dq"), request.widgets.map { it.id })
        assertEquals(listOf("Daily questions", "Blood pressure", "Medicines", "How you feel", "Question for the doctor"), request.widgets.map { it.title })
        assertEquals("2026-11-21", request.startDate)
        assertEquals(7, request.days)
        assertEquals(2, assertIs<BloodPressureWidget>(request.widgets[1]).timesPerDay)
        val questions = assertIs<QuestionnaireWidget>(request.widgets[0]).questions
        assertEquals(listOf(QuestionKind.YES_NO, QuestionKind.SCALE), questions.map { it.kind })
        assertEquals(ends, questions[1].options) // the patient sees the doctor's words at both ends of the scale
        assertEquals(emptyList(), questions[0].options)
    }

    @Test
    fun optionsChangeTheRequest() {
        val state = filled()
        state.enabled[WidgetKind.Water] = true
        state.enabled[WidgetKind.Diary] = false
        repeat(6) { state.changeWaterTarget(1) } // 6 × 250 ml
        state.changeBpTimes(1); state.changeBpTimes(1) // capped at 3
        state.changeStart(2)
        state.changeDays(-10) // capped at 1
        val request = assertNotNull(state.create(today, titles, "de", now = 1, id = "r2"))
        assertEquals(listOf("q", "bp", "w", "m", "dq"), request.widgets.map { it.id })
        assertEquals(1500, assertIs<WaterWidget>(request.widgets[2]).targetMl)
        assertEquals(BP_TIMES_MAX, assertIs<BloodPressureWidget>(request.widgets[1]).timesPerDay)
        assertEquals("2026-11-23", request.startDate)
        assertEquals(1, request.days)
        assertEquals("de", request.locale)

        state.changeWaterTarget(-100)
        assertNull(assertIs<WaterWidget>(assertNotNull(state.create(today, titles, "de", now = 1)).widgets[2]).targetMl)
    }

    @Test
    fun problemsAreListedInsteadOfARequest() {
        val state = RequestBuilderState(startQuestions = listOf(QuestionDraft("  ", QuestionKind.YES_NO)))
        assertNull(state.create(today, titles, "en", now = 1))
        assertEquals(listOf(DraftError.BLANK_DOCTOR, DraftError.BLANK_COMPLAINT, DraftError.QUESTIONNAIRE_INCOMPLETE), state.errors)

        WidgetKind.entries.forEach { state.enabled[it] = false }
        state.doctorName = "Dr. Keller"; state.complaint = "Cough"
        assertNull(state.create(today, titles, "en", now = 1))
        assertEquals(listOf(DraftError.NO_WIDGETS), state.errors)
    }

    @Test
    fun theInviteCarriesExactlyThisRequest() {
        val request = assertNotNull(filled().create(today, titles, "en", now = 1, scaleEnds = ends))
        assertEquals(request, assertIs<InviteResult.Ok>(parseInvite(inviteLink(request))).request)
    }
}
