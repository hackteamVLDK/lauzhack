package org.easypaper.ui.patient.v2

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreakTest {
    private val today = LocalDate(2026, 11, 18) // Wednesday

    @Test
    fun emptyDiaryHasNoStreakAndAMondayToSundayWeek() {
        val result = streak(emptyList(), today)
        assertEquals(0, result.days)
        assertFalse(result.todayDone)
        assertEquals((16..22).map { LocalDate(2026, 11, it) }, result.week.map { it.date })
        assertTrue(result.week.none { it.done })
        assertEquals(listOf(today), result.week.filter { it.isToday }.map { it.date })
        assertEquals((19..22).map { LocalDate(2026, 11, it) }, result.week.filter { it.isFuture }.map { it.date })
    }

    @Test
    fun entriesTodayCountAsOneDayRegardlessOfOrderAndDuplicates() {
        val result = streak(listOf(today, LocalDate(2026, 11, 17), today, LocalDate(2026, 11, 16)), today)
        assertEquals(3, result.days)
        assertTrue(result.todayDone)
        assertEquals(listOf(true, true, true, false, false, false, false), result.week.map { it.done })
        assertEquals(1, streak(listOf(today, today), today).days)
    }

    @Test
    fun yesterdayKeepsTheStreakUntilAnEntryIsAddedToday() {
        val result = streak(listOf(LocalDate(2026, 11, 16), LocalDate(2026, 11, 17)), today)
        assertEquals(2, result.days)
        assertFalse(result.todayDone)
        assertFalse(result.week.single { it.isToday }.done)
    }

    @Test
    fun aGapStopsTheStreakButEarlierEntriesRemainMarked() {
        val result = streak(listOf(today, LocalDate(2026, 11, 16), LocalDate(2026, 11, 15)), today)
        assertEquals(1, result.days)
        assertTrue(result.week.first().done)
        assertFalse(result.week[1].done)
        val expired = streak(listOf(LocalDate(2026, 11, 16), LocalDate(2026, 11, 15)), today)
        assertEquals(0, expired.days)
        assertFalse(expired.todayDone)
    }

    @Test
    fun monthAndLeapDayBoundariesUseCalendarDays() {
        val result = streak(listOf(LocalDate(2028, 2, 28), LocalDate(2028, 2, 29), LocalDate(2028, 3, 1)), LocalDate(2028, 3, 1))
        assertEquals(3, result.days)
        assertEquals(LocalDate(2028, 2, 28), result.week.first().date)
        assertEquals(LocalDate(2028, 3, 5), result.week.last().date)
    }

    @Test
    fun yearBoundaryAndYesterdayGraceCrossIntoThePreviousYear() {
        val dates = listOf(LocalDate(2026, 12, 30), LocalDate(2026, 12, 31))
        val result = streak(dates, LocalDate(2027, 1, 1))
        assertEquals(2, result.days)
        assertFalse(result.todayDone)
        assertEquals(LocalDate(2026, 12, 28), result.week.first().date)
        assertEquals(LocalDate(2027, 1, 3), result.week.last().date)
        assertEquals(3, streak(dates + LocalDate(2027, 1, 1), LocalDate(2027, 1, 1)).days)
    }

    @Test
    fun mondayAndSundayBothProduceTheContainingWeek() {
        val monday = LocalDate(2026, 11, 16)
        val sunday = LocalDate(2026, 11, 22)
        val mondayResult = streak(emptyList(), monday)
        val sundayResult = streak(emptyList(), sunday)
        assertEquals(mondayResult.week.map { it.date }, sundayResult.week.map { it.date })
        assertEquals(monday, mondayResult.week.first().date)
        assertTrue(mondayResult.week.first().isToday)
        assertTrue(sundayResult.week.last().isToday)
        assertTrue(sundayResult.week.none { it.isFuture })
    }

    @Test
    fun futureDatesNeverCompleteDaysOrExtendTheStreak() {
        val tomorrow = LocalDate(2026, 11, 19)
        val result = streak(listOf(today, tomorrow, LocalDate(2026, 11, 20)), today)
        assertEquals(1, result.days)
        assertTrue(result.todayDone)
        assertTrue(result.week.filter { it.isFuture }.none { it.done })
        assertEquals(0, streak(listOf(tomorrow), today).days)
    }
}
