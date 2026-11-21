package org.easypaper.diary

import org.easypaper.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemindersTest {
    private fun request(bpTimes: Int, extra: List<WidgetSpec> = emptyList()) = CareRequest(
        id = "r", createdAt = 0, doctorName = "Dr", complaint = "c", startDate = "2026-11-30", days = 2, locale = "en",
        widgets = listOf(
            QuestionnaireWidget("q", "Q", questions = listOf(Question("q1", "?", QuestionKind.YES_NO))),
            BloodPressureWidget("bp", "BP", timesPerDay = bpTimes),
            WaterWidget("w", "Water"),
            MedicationsWidget("m", "Meds"),
            DiaryWidget("d", "Diary"), // optional by default
            DoctorQuestionsWidget("dq", "Questions"), // optional by default
        ) + extra,
    )

    @Test
    fun twoReadingsADay() {
        val r = request(2).reminders()
        assertEquals(
            listOf(
                Reminder("2026-11-30", "08:00", listOf("bp", "m")),
                Reminder("2026-11-30", "14:30", listOf("w")),
                Reminder("2026-11-30", "21:00", listOf("q", "bp")),
            ),
            r.take(3),
        )
        assertEquals(listOf("2026-12-01"), r.drop(3).map { it.date }.distinct()) // crosses the month
        assertEquals(6, r.size)
    }

    @Test
    fun oneAndThreeReadings() {
        assertEquals(listOf("08:00" to listOf("bp", "m"), "14:30" to listOf("w"), "21:00" to listOf("q")),
            request(1).reminders().take(3).map { it.time to it.widgetIds })
        // 3 readings: 08:00, 14:30 (= midday, shares the slot with water), 21:00
        assertEquals(listOf("08:00" to listOf("bp", "m"), "14:30" to listOf("bp", "w"), "21:00" to listOf("q", "bp")),
            request(3).reminders().take(3).map { it.time to it.widgetIds })
    }

    @Test
    fun ownTimesAndRequiredOptionalWidgets() {
        val r = request(4, listOf(DiaryWidget("d2", "Mood", required = true), FoodWidget("f", "Food"))).reminders(wake = "06:30", sleep = "22:45")
        val day = r.filter { it.date == "2026-11-30" }
        // 4 readings between 06:30 and 22:45: every 325 min -> 06:30, 11:55, 17:20, 22:45; midday 14:35
        assertEquals(listOf("06:30", "11:55", "14:35", "17:20", "22:45"), day.map { it.time })
        assertEquals(listOf("w", "f"), day.single { it.time == "14:35" }.widgetIds)
        assertEquals(listOf("q", "bp", "d2"), day.last().widgetIds) // optional diary "d" and questions "dq" not reminded
    }

    @Test
    fun badTimesRejected() {
        for ((wake, sleep) in listOf("8:00" to "21:00", "08:00" to "24:00", "21:00" to "08:00", "08:00" to "08:00", "08.00" to "21:00")) {
            assertFailsWith<IllegalArgumentException>("$wake-$sleep") { request(2).reminders(wake, sleep) }
        }
    }

    @Test
    fun slotsStayInsideTheWindow() {
        // review of #25: 08:02-21:03 used to give 08:00 and 21:05
        val day = request(2).reminders(wake = "08:02", sleep = "21:03").filter { it.date == "2026-11-30" }
        assertEquals("08:05", day.first().time)
        assertEquals("21:00", day.last().time)
        val late = request(2).reminders(wake = "09:00", sleep = "23:59").filter { it.date == "2026-11-30" }
        assertEquals("23:55", late.last().time) // never "24:00"
        assertEquals(listOf("09:00", "16:25", "23:55"), late.map { it.time }) // midday of 09:00..23:55 = 16:27.5 -> 16:25
    }

    @Test
    fun narrowWindowIsRefusedNotMerged() {
        assertFailsWith<IllegalArgumentException> { request(3).reminders(wake = "08:00", sleep = "08:04") }
        assertFailsWith<IllegalArgumentException> { request(3).reminders(wake = "08:00", sleep = "08:09") } // 08:00..08:05: no room for 3
        val tight = request(3).reminders(wake = "08:00", sleep = "08:10").filter { it.date == "2026-11-30" }
        assertEquals(listOf("08:00", "08:05", "08:10"), tight.filter { "bp" in it.widgetIds }.map { it.time })
        assertEquals(3, tight.sumOf { r -> r.widgetIds.count { it == "bp" } }) // all three readings kept
    }
}
