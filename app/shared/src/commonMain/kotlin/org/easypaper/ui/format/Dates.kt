package org.easypaper.ui.format

import androidx.compose.runtime.Composable
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.date_long
import easypaper.app.shared.generated.resources.date_day_month
import easypaper.app.shared.generated.resources.date_range_one_month
import easypaper.app.shared.generated.resources.date_range_two_months
import easypaper.app.shared.generated.resources.months_long
import easypaper.app.shared.generated.resources.months_short
import easypaper.app.shared.generated.resources.weekdays
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** Minutes the device's time zone is ahead of UTC right now (CET: 60), for days of facts in local time. */
fun currentUtcOffsetMinutes(): Int = TimeZone.currentSystemDefault().offsetAt(Clock.System.now()).totalSeconds / 60

/** Today in the device's time zone. */
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** Where today falls in a request of [days] days that starts on [start]. */
sealed interface RequestDay {
    data class NotStarted(val startsOn: LocalDate) : RequestDay
    data class Running(val day: Int, val of: Int) : RequestDay
    data class Finished(val endedOn: LocalDate) : RequestDay
}

fun requestDay(today: LocalDate, start: LocalDate, days: Int): RequestDay {
    val last = start.plus(DatePeriod(days = days - 1))
    return when {
        today < start -> RequestDay.NotStarted(start)
        today > last -> RequestDay.Finished(last)
        else -> RequestDay.Running(day = (today.toEpochDays() - start.toEpochDays()).toInt() + 1, of = days)
    }
}

/** Last day of a request: start + days - 1. */
fun lastDay(start: LocalDate, days: Int): LocalDate = start.plus(DatePeriod(days = days - 1))

/**
 * "Saturday, 21 November" from a locale pattern and name lists; [weekdays] start on Monday,
 * [months] start in January (genitive where the language needs it, e.g. Ukrainian or Russian).
 */
fun formatLongDate(date: LocalDate, pattern: String, weekdays: List<String>, months: List<String>): String =
    pattern.replace("%1\$s", weekdays.getOrElse(date.dayOfWeek.ordinal) { "" })
        .replace("%2\$d", date.day.toString())
        .replace("%3\$s", months.getOrElse(date.month.ordinal) { "" })

/**
 * "17–21 Nov" in one month, "30 Nov – 2 Dec" across months. Name lists may still be empty: on the web
 * stringArrayResource loads them after the first frame (an index there crashed the doctor's web app).
 */
fun formatDateRange(from: LocalDate, to: LocalDate, oneMonth: String, twoMonths: String, months: List<String>): String =
    if (from.month == to.month && from.year == to.year) {
        oneMonth.replace("%1\$d", from.day.toString()).replace("%2\$d", to.day.toString())
            .replace("%3\$s", months.getOrElse(to.month.ordinal) { "" })
    } else {
        twoMonths.replace("%1\$d", from.day.toString()).replace("%2\$s", months.getOrElse(from.month.ordinal) { "" })
            .replace("%3\$d", to.day.toString()).replace("%4\$s", months.getOrElse(to.month.ordinal) { "" })
    }

/** "14 Mar" (EN), "14. Mär." (DE) from the locale's day-month pattern ("%1$d %2$s") and short month names. */
fun formatDayMonth(date: LocalDate, pattern: String, months: List<String>): String =
    pattern.replace("%1\$d", date.day.toString()).replace("%2\$s", months.getOrElse(date.month.ordinal) { "" })

@Composable
fun longDate(date: LocalDate): String = formatLongDate(
    date,
    stringResource(Res.string.date_long),
    stringArrayResource(Res.array.weekdays),
    stringArrayResource(Res.array.months_long),
)

@Composable
fun dateRange(from: LocalDate, to: LocalDate): String = formatDateRange(
    from, to,
    stringResource(Res.string.date_range_one_month),
    stringResource(Res.string.date_range_two_months),
    stringArrayResource(Res.array.months_short),
)

/** Day before month, with the order and punctuation supplied by the current app language. */
@Composable
fun dayMonth(date: LocalDate): String = stringResource(
    Res.string.date_day_month, date.day,
    stringArrayResource(Res.array.months_short).getOrElse(date.month.ordinal) { "" },
)
