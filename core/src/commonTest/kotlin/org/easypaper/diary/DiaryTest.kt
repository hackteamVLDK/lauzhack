package org.easypaper.diary

import org.easypaper.diary.EntryProblem.*
import org.easypaper.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryTest {
    private val DAY = 86_400_000L
    private val HOUR = 3_600_000L
    private val start = 1_794_873_600_000L // 2026-11-17T00:00:00Z

    private val request = CareRequest(
        id = "r", createdAt = 0, doctorName = "Dr", complaint = "c", startDate = "2026-11-17", days = 3, locale = "en",
        widgets = listOf(
            QuestionnaireWidget(
                "q", "Questions",
                questions = listOf(
                    Question("q1", "Pain?", QuestionKind.YES_NO),
                    Question("q2", "Intensity", QuestionKind.SCALE),
                    Question("q3", "Where?", QuestionKind.CHOICE, listOf("left", "right")),
                    Question("q4", "Notes", QuestionKind.TEXT),
                ),
            ),
            BloodPressureWidget("bp", "BP", timesPerDay = 2),
            WaterWidget("w", "Water"),
            FoodWidget("f", "Food", required = false),
            MedicationsWidget("m", "Meds"),
            DiaryWidget("d", "Diary"), // required = false by default
            DoctorQuestionsWidget("dq", "Questions to the doctor"),
        ),
    )

    private fun e(widget: String, day: Int, hour: Int, payload: EntryPayload) =
        Entry("$widget-$day-$hour", widget, start + day * DAY + hour * HOUR, payload)

    @Test
    fun progressPerDayMatchesHandCount() {
        val record = PatientRecord(
            request,
            listOf(
                e("bp", 0, 8, BloodPressure(120, 80)), e("bp", 0, 20, BloodPressure(125, 82)),
                e("w", 0, 10, Water(250)), e("w", 0, 15, Water(250)),
                e("q", 0, 21, Answers(mapOf("q1" to "no"))),
                e("m", 0, 9, Medication("X", source = MedicationSource.MANUAL, confirmed = true)),
                e("bp", 1, 8, BloodPressure(120, 80)),
                e("f", 1, 12, Food("soup")),
                e("bp", -1, 8, BloodPressure(120, 80)), // before the start
                e("w", 3, 8, Water(100)), // after the end
            ),
        )
        val progress = record.progress()
        assertEquals(listOf("2026-11-17", "2026-11-18", "2026-11-19"), progress.map { it.date })
        assertEquals(listOf(1, 2, 3), progress.map { it.dayNumber })
        // required widgets only: food, diary and questions to the doctor are optional
        assertEquals(listOf("q", "bp", "w", "m"), progress[0].widgets.map { it.widgetId })
        assertEquals(listOf(1, 2, 2, 1), progress[0].widgets.map { it.done })
        assertEquals(listOf(1, 2, 1, 1), progress[0].widgets.map { it.expected })
        assertTrue(progress[0].complete)
        assertEquals(listOf(0, 1, 0, 0), progress[1].widgets.map { it.done })
        assertFalse(progress[1].complete) // one of two pressure readings, nothing else
        assertEquals(listOf(0, 0, 0, 0), progress[2].widgets.map { it.done })
    }

    @Test
    fun progressUsesThePatientsLocalDay() {
        val late = PatientRecord(request, listOf(e("w", 0, 23, Water(200)))) // 23:00 UTC on day 1
        assertEquals(1, late.progress()[0].widgets.single { it.widgetId == "w" }.done)
        val east = late.progress(utcOffsetMinutes = 120) // 01:00 next day at UTC+2
        assertEquals(listOf(0, 1), east.take(2).map { day -> day.widgets.single { it.widgetId == "w" }.done })
    }

    @Test
    fun correctEntriesOfEveryTypePass() {
        val ok = listOf(
            e("q", 0, 9, Answers(mapOf("q1" to "yes", "q2" to "10", "q3" to "left", "q4" to "after lunch"))),
            e("bp", 0, 9, BloodPressure(135, 85, 70)),
            e("w", 0, 9, Water(300)),
            e("f", 0, 9, Food(photoRef = "p1")),
            e("m", 0, 9, Medication("Ibuprofen", source = MedicationSource.SCAN, confirmed = false)),
            e("d", 0, 9, DiaryNote("tired")),
            e("dq", 0, 9, DoctorQuestion("Can I do sport?")),
        )
        ok.forEach { assertEquals(emptyList(), request.check(it), "flagged ${it.payload}") }
    }

    @Test
    fun everyProblemIsCaught() {
        fun problems(widget: String, payload: EntryPayload) = request.check(e(widget, 0, 9, payload))
        assertEquals(listOf(UNKNOWN_WIDGET), problems("nope", Water(100)))
        assertEquals(listOf(WRONG_WIDGET_TYPE), problems("w", BloodPressure(120, 80)))
        assertEquals(listOf(SYSTOLIC_OUT_OF_RANGE), problems("bp", BloodPressure(1200, 80)))
        assertEquals(listOf(DIASTOLIC_OUT_OF_RANGE, DIASTOLIC_NOT_BELOW_SYSTOLIC), problems("bp", BloodPressure(120, 800)))
        assertEquals(listOf(PULSE_OUT_OF_RANGE), problems("bp", BloodPressure(120, 80, 700)))
        assertEquals(listOf(DIASTOLIC_NOT_BELOW_SYSTOLIC), problems("bp", BloodPressure(90, 120)))
        assertEquals(listOf(WATER_OUT_OF_RANGE), problems("w", Water(0)))
        assertEquals(listOf(WATER_OUT_OF_RANGE), problems("w", Water(25000)))
        assertEquals(listOf(FOOD_EMPTY), problems("f", Food(text = "  ")))
        assertEquals(listOf(EMPTY_TEXT), problems("d", DiaryNote(" ")))
        assertEquals(listOf(EMPTY_TEXT), problems("dq", DoctorQuestion("")))
        assertEquals(listOf(EMPTY_MEDICATION_NAME), problems("m", Medication(" ", source = MedicationSource.MANUAL, confirmed = true)))
        assertEquals(listOf(UNKNOWN_QUESTION), problems("q", Answers(mapOf("q9" to "yes"))))
        for (bad in listOf("q1" to "maybe", "q2" to "11", "q2" to "-1", "q2" to "a", "q3" to "middle", "q4" to " ")) {
            assertEquals(listOf(BAD_ANSWER), problems("q", Answers(mapOf(bad))), "accepted $bad")
        }
    }
}
