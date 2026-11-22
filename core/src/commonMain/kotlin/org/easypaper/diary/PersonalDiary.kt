package org.easypaper.diary

import kotlinx.serialization.Serializable
import org.easypaper.model.Answers
import org.easypaper.model.BloodPressure
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.CareRequest
import org.easypaper.model.DiaryNote
import org.easypaper.model.DiaryWidget
import org.easypaper.model.DoctorQuestion
import org.easypaper.model.DoctorQuestionsWidget
import org.easypaper.model.Entry
import org.easypaper.model.EntryPayload
import org.easypaper.model.Food
import org.easypaper.model.FoodWidget
import org.easypaper.model.Measurement
import org.easypaper.model.MeasurementsWidget
import org.easypaper.model.Medication
import org.easypaper.model.SideEffectCheck
import org.easypaper.model.MedicationsWidget
import org.easypaper.model.PatientRecord
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.Water
import org.easypaper.model.WaterWidget
import org.easypaper.model.WidgetSpec
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What a record is about, independent of any doctor's request (patient app v2, docs/patient-app-v2.md). */
@Serializable
enum class EntryCategory { BLOOD_PRESSURE, WATER, FOOD, MEDICINES, SYMPTOMS, DIARY, QUESTIONS, MEASUREMENTS }

fun EntryPayload.category(): EntryCategory = when (this) {
    is BloodPressure -> EntryCategory.BLOOD_PRESSURE
    is Water -> EntryCategory.WATER
    is Food -> EntryCategory.FOOD
    is Medication -> EntryCategory.MEDICINES
    is Answers -> EntryCategory.SYMPTOMS
    is DiaryNote -> EntryCategory.DIARY
    is DoctorQuestion -> EntryCategory.QUESTIONS
    is Measurement -> EntryCategory.MEASUREMENTS
    is SideEffectCheck -> EntryCategory.MEDICINES
}

fun WidgetSpec.category(): EntryCategory = when (this) {
    is BloodPressureWidget -> EntryCategory.BLOOD_PRESSURE
    is WaterWidget -> EntryCategory.WATER
    is FoodWidget -> EntryCategory.FOOD
    is MedicationsWidget -> EntryCategory.MEDICINES
    is QuestionnaireWidget -> EntryCategory.SYMPTOMS
    is DiaryWidget -> EntryCategory.DIARY
    is DoctorQuestionsWidget -> EntryCategory.QUESTIONS
    is MeasurementsWidget -> EntryCategory.MEASUREMENTS
}

private const val OWN = "own:"
private const val PLAN = "plan:"

/** widgetId of a record that belongs to the person only: its category. */
fun ownWidgetId(category: EntryCategory): String = OWN + category.name.lowercase()

/** widgetId of a record made for one widget of a plan (answers to a doctor's questionnaire need its questions). */
fun planWidgetId(planId: String, widgetId: String): String = "$PLAN$planId#$widgetId"

private fun Entry.plan(): Pair<String, String>? =
    widgetId.takeIf { it.startsWith(PLAN) }?.removePrefix(PLAN)?.let { it.substringBefore('#') to it.substringAfter('#', "") }

/**
 * Everything the person recorded, and the doctors' requests as plans on top (v2, #47). Records belong to the
 * person: a plan only selects them by category and period, so one blood pressure reading counts for every plan
 * that asks for blood pressure on that day.
 */
@Serializable
data class Diary(val entries: List<Entry> = emptyList(), val plans: List<CareRequest> = emptyList()) {
    init {
        require(entries.map { it.id }.toSet().size == entries.size) { "entry ids must be unique" }
        require(plans.map { it.id }.toSet().size == plans.size) { "plan ids must be unique" }
    }
}

/** Checks a record before it is saved: a plan's widget rules for plan records, the value rules for own ones. */
fun Diary.check(entry: Entry): List<EntryProblem> {
    entry.plan()?.let { (planId, widgetId) ->
        val plan = plans.firstOrNull { it.id == planId } ?: return listOf(EntryProblem.UNKNOWN_WIDGET)
        return plan.check(entry.copy(widgetId = widgetId))
    }
    if (!entry.widgetId.startsWith(OWN)) return listOf(EntryProblem.UNKNOWN_WIDGET)
    if (entry.widgetId != ownWidgetId(entry.payload.category())) return listOf(EntryProblem.WRONG_WIDGET_TYPE)
    return entry.payload.problems()
}

/** First and last day of a plan, ISO dates. */
@OptIn(ExperimentalTime::class)
fun CareRequest.period(): Pair<String, String> = startDate to plusDays(startDate, days - 1)

private const val DAY = 86_400_000L

@OptIn(ExperimentalTime::class)
private fun plusDays(isoDate: String, days: Int): String =
    Instant.fromEpochMilliseconds(Instant.parse(isoDate + "T00:00:00Z").toEpochMilliseconds() + days * DAY).toString().take(10)

@OptIn(ExperimentalTime::class)
private fun localDay(at: Long, utcOffsetMinutes: Int): String = Instant.fromEpochMilliseconds(at + utcOffsetMinutes * 60_000L).toString().take(10)

/**
 * The plan as the record the existing screens, progress, fact digest and handover work with: the person's records
 * of the plan's period (local days) whose category the plan asks for, under the plan's widget ids. Questionnaire
 * answers count only for the plan they were given for.
 */
fun Diary.recordFor(plan: CareRequest, utcOffsetMinutes: Int = 0): PatientRecord {
    val (first, last) = plan.period()
    val byCategory = plan.widgets.filter { it !is QuestionnaireWidget }.associateBy { it.category() }
    val entries = entries.mapNotNull { e ->
        val day = localDay(e.at, utcOffsetMinutes)
        if (day < first || day > last) return@mapNotNull null
        val widget = when (val ref = e.plan()) {
            null -> byCategory[e.payload.category()]
            else -> if (ref.first == plan.id) plan.widgets.firstOrNull { it.id == ref.second } else null
        } ?: return@mapNotNull null
        e.copy(widgetId = widget.id)
    }
    return PatientRecord(plan, entries)
}

/**
 * What the person chose to share (v2 "What to share"): records of [categories] from [from] to [to] (ISO local days)
 * as a record for the report. With a [plan] the plan's request frames it; without one a summary request is made of
 * the chosen categories, titled by [titles]. Without [photos] the photo references are left out.
 */
fun Diary.selection(
    categories: Set<EntryCategory>,
    from: String,
    to: String,
    plan: CareRequest? = null,
    photos: Boolean = true,
    utcOffsetMinutes: Int = 0,
    now: Long = 0,
    locale: String = "en",
    titles: (EntryCategory) -> String = { it.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase) },
): PatientRecord {
    require(from <= to) { "from must not be after to" }
    val request = plan ?: summaryRequest(categories, from, to, now, locale, titles)
    val chosen = Diary(entries.filter { e ->
        val day = localDay(e.at, utcOffsetMinutes)
        e.payload.category() in categories && day >= from && day <= to
    }, plans)
    val record = if (plan != null) {
        chosen.recordFor(plan.copy(startDate = from, days = daysBetween(from, to) + 1), utcOffsetMinutes).copy(request = plan)
    } else {
        PatientRecord(request, chosen.entries.mapNotNull { e ->
            val ref = e.plan()
            val widget = if (ref != null && e.payload is Answers) request.widgets.firstOrNull { it.id == "${ref.first}#${ref.second}" }
            else request.widgets.firstOrNull { it !is QuestionnaireWidget && it.category() == e.payload.category() }
            widget?.let { e.copy(widgetId = it.id) }
        })
    }
    return if (photos) record else record.copy(entries = record.entries.map { it.withoutPhoto() })
}

private fun Diary.summaryRequest(
    categories: Set<EntryCategory>, from: String, to: String, now: Long, locale: String, titles: (EntryCategory) -> String,
): CareRequest {
    val order = EntryCategory.entries.filter { it in categories }
    val widgets = order.flatMap { c ->
        when (c) {
            EntryCategory.BLOOD_PRESSURE -> listOf(BloodPressureWidget("bp", titles(c), required = false))
            EntryCategory.WATER -> listOf(WaterWidget("water", titles(c), required = false))
            EntryCategory.FOOD -> listOf(FoodWidget("food", titles(c), required = false))
            EntryCategory.MEDICINES -> listOf(MedicationsWidget("medicines", titles(c), required = false))
            EntryCategory.DIARY -> listOf(DiaryWidget("diary", titles(c), required = false))
            EntryCategory.QUESTIONS -> listOf(DoctorQuestionsWidget("questions", titles(c), required = false))
            EntryCategory.MEASUREMENTS -> listOf(MeasurementsWidget("measurements", titles(c)))
            // the questions a doctor asked travel with the answers, one questionnaire per plan
            EntryCategory.SYMPTOMS -> plans.flatMap { p ->
                p.widgets.filterIsInstance<QuestionnaireWidget>().map { it.copy(id = "${p.id}#${it.id}", required = false) }
            }
        }
    }
    return CareRequest(
        id = "diary-$from-$to", createdAt = now, doctorName = "", complaint = "", startDate = from,
        days = daysBetween(from, to) + 1, locale = locale, widgets = widgets,
    )
}

@OptIn(ExperimentalTime::class)
private fun daysBetween(from: String, to: String): Int =
    ((Instant.parse(to + "T00:00:00Z").toEpochMilliseconds() - Instant.parse(from + "T00:00:00Z").toEpochMilliseconds()) / DAY).toInt()

private fun Entry.withoutPhoto(): Entry = when (val p = payload) {
    is Food -> copy(payload = p.copy(photoRef = null))
    is DiaryNote -> copy(payload = p.copy(photoRef = null))
    else -> this
}

/**
 * The records kept per request before v2 become one diary: every request is a plan, its records keep their ids;
 * records of non-questionnaire widgets become the person's own (by category), answers stay tied to their plan.
 */
fun migrateToDiary(records: List<PatientRecord>): Diary {
    val entries = records.flatMap { r ->
        val widgets = r.request.widgets.associateBy { it.id }
        r.entries.mapNotNull { e ->
            val widget = widgets[e.widgetId] ?: return@mapNotNull null
            e.copy(widgetId = if (widget is QuestionnaireWidget) planWidgetId(r.request.id, widget.id) else ownWidgetId(e.payload.category()))
        }
    }.distinctBy { it.id }
    return Diary(entries, records.map { it.request }.distinctBy { it.id })
}
