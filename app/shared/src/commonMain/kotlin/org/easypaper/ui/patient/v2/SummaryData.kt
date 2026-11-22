package org.easypaper.ui.patient.v2

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.easypaper.model.BloodPressure
import org.easypaper.model.Measurement
import org.easypaper.model.PatientRecord
import org.easypaper.model.Water
import org.easypaper.ui.patient.localDate
import org.easypaper.ui.theme.Category
import kotlin.time.Instant

enum class SummaryMetric { Pressure, Water, Weight, Entries }

sealed interface SummaryValue {
    data class Pressure(val systolic: Int, val diastolic: Int) : SummaryValue
    data class WaterTotal(val ml: Int) : SummaryValue
    data class Weight(val kg: Double) : SummaryValue
    data class Entries(val count: Int) : SummaryValue
}

/** Null means no measurement; zero means no logged water/entries on that local day. */
data class SummaryDay(val date: LocalDate, val value: Double?, val samples: Int)
data class SummaryData(
    val latest: OwnedEntry?,
    val value: SummaryValue?,
    val metric: SummaryMetric,
    val days: List<SummaryDay>,
)

sealed interface SummaryTime {
    data class Today(val clock: String) : SummaryTime
    data object Yesterday : SummaryTime
    data class Earlier(val date: LocalDate) : SummaryTime
}

fun summaryTime(at: Long, today: LocalDate, timeZone: TimeZone): SummaryTime {
    val local = Instant.fromEpochMilliseconds(at).toLocalDateTime(timeZone)
    return when (local.date) {
        today -> SummaryTime.Today("${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}")
        today.plus(DatePeriod(days = -1)) -> SummaryTime.Yesterday
        else -> SummaryTime.Earlier(local.date)
    }
}

private fun weight(entry: OwnedEntry): Double? = (entry.entry.payload as? Measurement)?.takeIf {
    it.loinc == Measurement.WEIGHT_LOINC && it.unit == "kg" && it.value.isFinite()
}?.value

/** Seven inclusive local calendar days, across personal and doctor records; never mix measurement units. */
fun summaryData(records: List<PatientRecord>, category: Category, today: LocalDate, timeZone: TimeZone): SummaryData {
    val entries = categoryEntries(records, category).filter { it.entry.localDate(timeZone) <= today }
    val latest = entries.firstOrNull()
    val metric = when {
        category == Category.BloodPressure -> SummaryMetric.Pressure
        category == Category.Water -> SummaryMetric.Water
        category == Category.Measurements && latest?.let(::weight) != null -> SummaryMetric.Weight
        else -> SummaryMetric.Entries
    }
    val days = summarySeries(entries, metric, today, timeZone, 7)
    val value = if (latest == null) null else when (metric) {
        SummaryMetric.Pressure -> (latest.entry.payload as? BloodPressure)?.let { SummaryValue.Pressure(it.systolic, it.diastolic) }
        SummaryMetric.Water -> SummaryValue.WaterTotal(days.last().value!!.toInt())
        SummaryMetric.Weight -> SummaryValue.Weight(weight(latest)!!)
        SummaryMetric.Entries -> SummaryValue.Entries(days.sumOf { it.samples })
    }
    return SummaryData(latest, value, metric, days)
}

private fun summarySeries(entries: List<OwnedEntry>, metric: SummaryMetric, today: LocalDate,
                          timeZone: TimeZone, count: Int): List<SummaryDay> {
    val byDay = entries.groupBy { it.entry.localDate(timeZone) }
    return (1 - count..0).map { offset ->
        val date = today.plus(DatePeriod(days = offset))
        val day = byDay[date].orEmpty()
        val values = day.mapNotNull { entry -> when (metric) {
            SummaryMetric.Pressure -> (entry.entry.payload as? BloodPressure)?.systolic?.toDouble()
            SummaryMetric.Weight -> weight(entry)
            SummaryMetric.Water -> (entry.entry.payload as? Water)?.ml?.toDouble()
            SummaryMetric.Entries -> 1.0
        } }
        SummaryDay(date, when (metric) {
            SummaryMetric.Water, SummaryMetric.Entries -> values.sum()
            else -> values.takeIf { it.isNotEmpty() }?.average()
        }, values.size)
    }
}

enum class SummaryDirection { Lower, Same, Higher }
data class SummaryTrend(val category: Category, val metric: SummaryMetric, val days: List<SummaryDay>,
                        val previousAverage: Double, val currentAverage: Double) {
    val direction: SummaryDirection get() = when {
        currentAverage < previousAverage -> SummaryDirection.Lower
        currentAverage > previousAverage -> SummaryDirection.Higher
        else -> SummaryDirection.Same
    }
}

/** Two rolling seven-day windows. Requires a full 14-day history and samples in both windows.
 * Water is the daily logged total (including zero); measurements average recorded samples only.
 * Pressure explicitly compares systolic readings, never a combined or diagnostic score.
 */
fun summaryTrend(records: List<PatientRecord>, category: Category, today: LocalDate, timeZone: TimeZone): SummaryTrend? {
    val metric = when (category) {
        Category.BloodPressure -> SummaryMetric.Pressure
        Category.Water -> SummaryMetric.Water
        Category.Measurements -> SummaryMetric.Weight
        else -> return null
    }
    val entries = categoryEntries(records, category).filter { owned ->
        owned.entry.localDate(timeZone) <= today && when (metric) {
            SummaryMetric.Pressure -> owned.entry.payload is BloodPressure
            SummaryMetric.Water -> owned.entry.payload is Water
            SummaryMetric.Weight -> weight(owned) != null
            else -> false
        }
    }
    val start = today.plus(DatePeriod(days = -13))
    if (entries.none { it.entry.localDate(timeZone) <= start }) return null
    val days = summarySeries(entries, metric, today, timeZone, 14)
    val previous = days.take(7)
    val current = days.takeLast(7)
    if (previous.sumOf { it.samples } == 0 || current.sumOf { it.samples } == 0) return null
    val average: (List<SummaryDay>) -> Double = { week ->
        if (metric == SummaryMetric.Water) week.sumOf { it.value ?: 0.0 } / 7
        else week.sumOf { (it.value ?: 0.0) * it.samples } / week.sumOf { it.samples }
    }
    return SummaryTrend(category, metric, days, average(previous), average(current))
}

enum class SummaryTip { Pressure, Medicines, Invite }

fun summaryTips(settings: PatientSettings, hasDoctorRequest: Boolean): List<SummaryTip> = SummaryTip.entries.filter {
    it.name !in settings.dismissedTipIds && when (it) {
        SummaryTip.Pressure -> Category.BloodPressure !in settings.pinnedCategories
        SummaryTip.Medicines -> Category.Medicines !in settings.pinnedCategories
        SummaryTip.Invite -> !hasDoctorRequest
    }
}
