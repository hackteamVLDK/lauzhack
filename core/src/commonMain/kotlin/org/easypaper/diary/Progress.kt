package org.easypaper.diary

import kotlinx.serialization.Serializable
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.PatientRecord
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class WidgetProgress(val widgetId: String, val title: String, val done: Int, val expected: Int) {
    val complete: Boolean get() = done >= expected
}

@Serializable
data class DayProgress(val date: String, val dayNumber: Int, val widgets: List<WidgetProgress>) {
    val complete: Boolean get() = widgets.all { it.complete }
}

private const val DAY = 86_400_000L

/**
 * What the patient has filled in for every day of the request: required widgets only; blood pressure is expected
 * [BloodPressureWidget.timesPerDay] times a day, every other required widget once. Days are the patient's local
 * calendar days ([utcOffsetMinutes]); entries outside the request period are not counted.
 */
@OptIn(ExperimentalTime::class)
fun PatientRecord.progress(utcOffsetMinutes: Int = 0): List<DayProgress> {
    val start = Instant.parse(request.startDate + "T00:00:00Z").toEpochMilliseconds()
    fun dayIndex(at: Long) = (at + utcOffsetMinutes * 60_000L - start).floorDiv(DAY)
    val counts = entries.groupingBy { it.widgetId to dayIndex(it.at) }.eachCount()
    val required = request.widgets.filter { it.required }
    return (0 until request.days).map { day ->
        DayProgress(
            date = Instant.fromEpochMilliseconds(start + day * DAY).toString().take(10),
            dayNumber = day + 1,
            widgets = required.map { w ->
                val expected = if (w is BloodPressureWidget) w.timesPerDay else 1
                WidgetProgress(w.id, w.title, counts[w.id to day.toLong()] ?: 0, expected)
            },
        )
    }
}
