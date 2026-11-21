package org.easypaper.report

import kotlinx.serialization.Serializable
import org.easypaper.model.Answers
import org.easypaper.model.BloodPressure
import org.easypaper.model.DiaryNote
import org.easypaper.model.DoctorQuestion
import org.easypaper.model.Food
import org.easypaper.model.Medication
import org.easypaper.model.PatientRecord
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.Water
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Facts computed from a [PatientRecord] without any AI: numbers, dates, the patient's own words and the ids
 * of the entries they come from. No thresholds, ratings or advice. Input for the AI summary (#7), which then
 * reads a short digest instead of every entry, and for the doctor screen (#10).
 */
@Serializable
data class FactDigest(
    val requestId: String,
    val complaint: String,
    val firstDate: String?,
    val lastDate: String?,
    val entryCount: Int,
    val bloodPressure: List<BloodPressureDay>,
    val water: List<WaterDay>,
    val medications: List<MedicationFact>,
    val questions: List<QuestionFact>,
    val notes: List<NoteFact>,
)

/** avg is rounded half up. */
@Serializable
data class IntRange3(val min: Int, val max: Int, val avg: Int)

@Serializable
data class BloodPressureDay(
    val date: String,
    val count: Int,
    val systolic: IntRange3,
    val diastolic: IntRange3,
    val pulse: IntRange3?,
    val refs: List<String>,
)

@Serializable
data class WaterDay(val date: String, val totalMl: Int, val refs: List<String>)

@Serializable
data class MedicationFact(
    val name: String,
    val code: String?,
    val dose: String?,
    val confirmed: Boolean,
    val dates: List<String>,
    val refs: List<String>,
)

@Serializable
data class QuestionFact(val questionId: String, val text: String, val answers: List<DatedValue>)

@Serializable
data class DatedValue(val date: String, val value: String, val ref: String)

@Serializable
data class NoteFact(val kind: String, val date: String, val text: String?, val photoRef: String?, val ref: String)

/** [utcOffsetMinutes]: the patient's offset, so an entry lands on the patient's own calendar day. */
@OptIn(ExperimentalTime::class)
fun PatientRecord.factDigest(utcOffsetMinutes: Int = 0): FactDigest {
    val sorted = entries.sortedBy { it.at }
    fun day(at: Long) = Instant.fromEpochMilliseconds(at + utcOffsetMinutes * 60_000L).toString().take(10)

    val bp = sorted.filter { it.payload is BloodPressure }.groupBy { day(it.at) }.map { (date, list) ->
        val values = list.map { it.payload as BloodPressure }
        BloodPressureDay(
            date, list.size,
            range(values.map { it.systolic })!!, range(values.map { it.diastolic })!!,
            range(values.mapNotNull { it.pulse }),
            list.map { it.id },
        )
    }
    val water = sorted.filter { it.payload is Water }.groupBy { day(it.at) }.map { (date, list) ->
        WaterDay(date, list.sumOf { (it.payload as Water).ml }, list.map { it.id })
    }
    val meds = sorted.filter { it.payload is Medication }
        .groupBy { (it.payload as Medication).let { m -> listOf(m.name, m.code, m.dose, m.confirmed) } }
        .map { (_, list) ->
            val m = list.first().payload as Medication
            MedicationFact(m.name, m.code, m.dose, m.confirmed, list.map { day(it.at) }.distinct(), list.map { it.id })
        }
        .sortedBy { !it.confirmed } // confirmed first, then what the patient has not checked yet

    val questionTexts = request.widgets.filterIsInstance<QuestionnaireWidget>().flatMap { it.questions }.associate { it.id to it.text }
    val answers = sorted.filter { it.payload is Answers }
        .flatMap { e -> (e.payload as Answers).values.map { (q, v) -> q to DatedValue(day(e.at), v, e.id) } }
    val questions = answers.groupBy({ it.first }, { it.second })
        .map { (q, values) -> QuestionFact(q, questionTexts[q] ?: q, values) }

    val notes = sorted.mapNotNull { e ->
        when (val p = e.payload) {
            is DiaryNote -> NoteFact("diary", day(e.at), p.text, p.photoRef, e.id)
            is Food -> NoteFact("food", day(e.at), p.text, p.photoRef, e.id)
            is DoctorQuestion -> NoteFact("doctor_question", day(e.at), p.text, null, e.id)
            else -> null
        }
    }
    return FactDigest(
        request.id, request.complaint, sorted.firstOrNull()?.let { day(it.at) }, sorted.lastOrNull()?.let { day(it.at) },
        entries.size, bp, water, meds, questions, notes,
    )
}

private fun range(values: List<Int>): IntRange3? =
    if (values.isEmpty()) null
    else IntRange3(values.min(), values.max(), (2 * values.sum() + values.size) / (2 * values.size))

/** Compact English text for an AI prompt: facts and [entryId] references only. */
fun FactDigest.toText(): String = buildString {
    fun refs(ids: List<String>) = ids.joinToString(",", "[", "]")
    fun IntRange3.show() = "$min-$max (avg $avg)"
    appendLine("Complaint: $complaint. Entries: $entryCount, from ${firstDate ?: "-"} to ${lastDate ?: "-"}.")
    for (d in bloodPressure) {
        append("Blood pressure ${d.date}: ${d.count} readings, systolic ${d.systolic.show()}, diastolic ${d.diastolic.show()}")
        d.pulse?.let { append(", pulse ${it.show()}") }
        appendLine(" mmHg ${refs(d.refs)}")
    }
    for (d in water) appendLine("Water ${d.date}: ${d.totalMl} mL ${refs(d.refs)}")
    for (m in medications) {
        val what = listOfNotNull(m.name, m.dose?.takeIf { it !in m.name }, m.code?.let { "GTIN $it" }).joinToString(", ")
        val state = if (m.confirmed) "confirmed by patient" else "NOT confirmed by patient"
        appendLine("Medication ($state): $what; recorded on ${m.dates.joinToString(", ")} ${refs(m.refs)}")
    }
    for (q in questions) {
        appendLine("Question \"${q.text}\": " + q.answers.joinToString("; ") { "${it.date} ${it.value} [${it.ref}]" })
    }
    for (n in notes) {
        val label = when (n.kind) { "diary" -> "Diary"; "food" -> "Food"; else -> "Question to the doctor" }
        val photo = n.photoRef?.let { " (photo $it)" } ?: ""
        appendLine("$label ${n.date}: ${n.text ?: ""}$photo [${n.ref}]")
    }
}.trimEnd()
