package org.easypaper.diary

import kotlinx.serialization.Serializable
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.CareRequest
import org.easypaper.model.DiaryWidget
import org.easypaper.model.DoctorQuestionsWidget
import org.easypaper.model.FoodWidget
import org.easypaper.model.MedicationsWidget
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.WaterWidget
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** One notification slot: patient's local [date] and [time] ("HH:mm"), and the widgets to fill then. */
@Serializable
data class Reminder(val date: String, val time: String, val widgetIds: List<String>)

/**
 * When to remind the patient, for every day of the request, required widgets only: blood pressure
 * timesPerDay times spread evenly from [wake] to [sleep], medications at [wake], water and food at midday,
 * questionnaire / diary / questions to the doctor at [sleep]. Texts are the UI's job; the platform turns these
 * slots into notifications in the phone's time zone and may skip one when the day's progress is complete.
 */
@OptIn(ExperimentalTime::class)
fun CareRequest.reminders(wake: String = "08:00", sleep: String = "21:00"): List<Reminder> {
    val from = minutes(wake)
    val to = minutes(sleep)
    require(from < to) { "Wake-up time must be before bedtime" }
    val midday = round5((from + to) / 2)
    val slots = mutableMapOf<Int, MutableList<String>>()
    fun at(minute: Int, widgetId: String) { slots.getOrPut(minute) { mutableListOf() } += widgetId }
    for (w in widgets.filter { it.required }) when (w) {
        is BloodPressureWidget -> {
            val n = w.timesPerDay.coerceAtLeast(1)
            if (n == 1) at(from, w.id) else for (k in 0 until n) at(round5(from + k * (to - from) / (n - 1)), w.id)
        }
        is MedicationsWidget -> at(from, w.id)
        is WaterWidget, is FoodWidget -> at(midday, w.id)
        is QuestionnaireWidget, is DiaryWidget, is DoctorQuestionsWidget -> at(to, w.id)
    }
    val start = Instant.parse(startDate + "T00:00:00Z").toEpochMilliseconds()
    return (0 until days).flatMap { day ->
        val date = Instant.fromEpochMilliseconds(start + day * 86_400_000L).toString().take(10)
        slots.entries.sortedBy { it.key }.map { (minute, ids) -> Reminder(date, hhmm(minute), ids.distinct()) }
    }
}

private fun minutes(time: String): Int {
    val m = Regex("""([01]\d|2[0-3]):([0-5]\d)""").matchEntire(time) ?: throw IllegalArgumentException("Time must be HH:mm, got '$time'")
    return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
}

private fun round5(minute: Int) = (minute + 2) / 5 * 5

private fun hhmm(minute: Int) = (minute / 60).toString().padStart(2, '0') + ":" + (minute % 60).toString().padStart(2, '0')
