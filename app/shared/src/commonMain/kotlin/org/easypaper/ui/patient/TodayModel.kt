package org.easypaper.ui.patient

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.easypaper.model.BloodPressure
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.DiaryWidget
import org.easypaper.model.DoctorQuestionsWidget
import org.easypaper.model.Entry
import org.easypaper.model.FoodWidget
import org.easypaper.model.MeasurementsWidget
import org.easypaper.model.MedicationsWidget
import org.easypaper.model.PatientRecord
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.Water
import org.easypaper.model.WaterWidget
import org.easypaper.model.WidgetSpec
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What one widget of the request shows on "Today". */
sealed interface WidgetToday {
    val widget: WidgetSpec

    /** Readings of today, oldest first; [expected] = timesPerDay. */
    data class Pressure(override val widget: BloodPressureWidget, val readings: List<Entry>, val expected: Int) : WidgetToday {
        val latest: BloodPressure? get() = readings.lastOrNull()?.payload as? BloodPressure
        val latestAt: Long? get() = readings.lastOrNull()?.at
    }

    /** Water drunk today; [lastEntryId] is what "−" takes back. */
    data class Drink(override val widget: WaterWidget, val totalMl: Int, val targetMl: Int?, val lastEntryId: String?) : WidgetToday

    /** The latest answers of today, if any. */
    data class Questions(override val widget: QuestionnaireWidget, val answered: Entry?) : WidgetToday

    /** Food, medicines, diary: today's entries, oldest first. */
    data class Notes(override val widget: WidgetSpec, val entries: List<Entry>) : WidgetToday

    /** Questions for the doctor collect over all days. */
    data class Asked(override val widget: DoctorQuestionsWidget, val entries: List<Entry>) : WidgetToday
}

/** The patient's calendar day of an entry. */
@OptIn(ExperimentalTime::class)
fun Entry.localDate(timeZone: TimeZone): LocalDate = Instant.fromEpochMilliseconds(at).toLocalDateTime(timeZone).date

/** "08:10" in the patient's time zone. */
@OptIn(ExperimentalTime::class)
fun timeOfDay(epochMillis: Long, timeZone: TimeZone): String {
    val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    return t.hour.toString().padStart(2, '0') + ":" + t.minute.toString().padStart(2, '0')
}

/** Every widget of the request with what was recorded for it today, in the doctor's order. */
fun todayFor(record: PatientRecord, today: LocalDate, timeZone: TimeZone): List<WidgetToday> {
    val byWidget = record.entries.sortedBy { it.at }.groupBy { it.widgetId }
    return record.request.widgets.map { widget ->
        val all = byWidget[widget.id].orEmpty()
        val todays = all.filter { it.localDate(timeZone) == today }
        when (widget) {
            is BloodPressureWidget -> WidgetToday.Pressure(widget, todays.filter { it.payload is BloodPressure }, widget.timesPerDay)
            is WaterWidget -> {
                val water = todays.filter { it.payload is Water }
                WidgetToday.Drink(widget, water.sumOf { (it.payload as Water).ml }, widget.targetMl, water.lastOrNull()?.id)
            }
            is QuestionnaireWidget -> WidgetToday.Questions(widget, todays.lastOrNull())
            is DoctorQuestionsWidget -> WidgetToday.Asked(widget, all)
            is FoodWidget, is MedicationsWidget, is DiaryWidget, is MeasurementsWidget -> WidgetToday.Notes(widget, todays)
        }
    }
}

/**
 * Blood pressure from the three form fields; null while the upper or lower value is missing or not a
 * number. Range and order are checked by the request (EntryCheck) when saving.
 */
fun bloodPressureFromFields(systolic: String, diastolic: String, pulse: String): BloodPressure? {
    val sys = systolic.trim().toIntOrNull() ?: return null
    val dia = diastolic.trim().toIntOrNull() ?: return null
    val pul = if (pulse.isBlank()) null else pulse.trim().toIntOrNull() ?: return null
    return BloodPressure(sys, dia, pul)
}

/** "1.25" or "1,25" litres from mL, at most two decimals, no trailing zeros. */
fun litres(ml: Int, decimalSeparator: Char): String {
    val whole = ml / 1000
    val rest = (ml % 1000 + 5) / 10 // hundredths, rounded
    val (w, r) = if (rest == 100) whole + 1 to 0 else whole to rest
    if (r == 0) return w.toString()
    val decimals = r.toString().padStart(2, '0').trimEnd('0')
    return "$w$decimalSeparator$decimals"
}

/** Unique id for a new entry ("e" + 16 hex): no ':' or '/', safe in photo refs and QR strings. */
fun newEntryId(random: Random = Random.Default): String =
    "e" + random.nextBytes(8).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/** Amount added by one tap on "+" in the water row. */
const val WATER_STEP_ML = 250
