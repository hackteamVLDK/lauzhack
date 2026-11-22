package org.easypaper.ui.patient.v2

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class DayMark(val date: LocalDate, val done: Boolean, val isToday: Boolean, val isFuture: Boolean)

data class Streak(val days: Int, val todayDone: Boolean, val week: List<DayMark>)

/** Consecutive diary days ending today, or yesterday while today's entry is still pending. */
fun streak(entryDates: Collection<LocalDate>, today: LocalDate): Streak {
    // Several entries count as one day; future dates cannot complete a diary day yet.
    val recorded = entryDates.filterTo(mutableSetOf()) { it <= today }
    val todayDone = today in recorded
    var cursor = if (todayDone) today else today.plus(DatePeriod(days = -1))
    var days = 0
    while (cursor in recorded) {
        days++
        cursor = cursor.plus(DatePeriod(days = -1))
    }
    val monday = today.plus(DatePeriod(days = -today.dayOfWeek.ordinal))
    val week = (0 until 7).map { offset ->
        val date = monday.plus(DatePeriod(days = offset))
        DayMark(date, done = date in recorded, isToday = date == today, isFuture = date > today)
    }
    return Streak(days, todayDone, week)
}
