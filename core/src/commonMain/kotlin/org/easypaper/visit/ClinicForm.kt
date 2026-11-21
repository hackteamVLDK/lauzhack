package org.easypaper.visit

import kotlinx.serialization.Serializable
import org.easypaper.model.BloodPressure
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.DoctorQuestion
import org.easypaper.model.DoctorQuestionsWidget
import org.easypaper.model.Entry
import org.easypaper.model.Medication
import org.easypaper.model.MedicationSource
import org.easypaper.model.MedicationsWidget
import org.easypaper.model.Water
import org.easypaper.model.WaterWidget
import org.easypaper.model.WidgetSpec
import org.easypaper.report.Report
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Fields of the clinic intake form. The UI shows a localized label per key. */
enum class FormFieldKey { COMPLAINT, HISTORY, BLOOD_PRESSURE, MEDICATIONS, FLUID_INTAKE, PATIENT_QUESTIONS }

/** The doctor's decision on a field. Only CONFIRMED values are ever saved. */
enum class FieldState { DRAFT, CONFIRMED, REJECTED }

/** One pre-filled field: [draft] as computed from the record, [value] as the doctor has it now. */
data class FormField(
    val key: FormFieldKey,
    val draft: String,
    val sourceIds: List<String>,
    val value: String = draft,
    val state: FieldState = FieldState.DRAFT,
) {
    val edited: Boolean get() = value != draft
}

/**
 * The pre-filled clinic form. Immutable: every action returns a new form.
 * Nothing is saved until the doctor decided on every field, and only confirmed fields are saved.
 */
data class ClinicForm(val reportId: String, val fields: List<FormField>) {
    init {
        require(fields.map { it.key }.toSet().size == fields.size) { "one field per key" }
    }

    fun field(key: FormFieldKey): FormField = fields.first { it.key == key }

    /** Changing the text takes the field back to DRAFT: the doctor confirms what they see. */
    fun edit(key: FormFieldKey, value: String): ClinicForm = update(key) { it.copy(value = value, state = FieldState.DRAFT) }

    fun confirm(key: FormFieldKey): ClinicForm = update(key) {
        require(it.value.isNotBlank()) { "an empty field cannot be confirmed, reject it instead" }
        it.copy(state = FieldState.CONFIRMED)
    }

    fun reject(key: FormFieldKey): ClinicForm = update(key) { it.copy(state = FieldState.REJECTED) }

    /** Takes back a decision. */
    fun reopen(key: FormFieldKey): ClinicForm = update(key) { it.copy(state = FieldState.DRAFT) }

    /** Fields still waiting for the doctor's decision. */
    val undecided: List<FormFieldKey> get() = fields.filter { it.state == FieldState.DRAFT }.map { it.key }

    /** Every field decided and at least one confirmed. */
    val canSave: Boolean get() = undecided.isEmpty() && fields.any { it.state == FieldState.CONFIRMED }

    fun save(savedAt: Long): SavedClinicForm {
        check(canSave) { "cannot save: undecided fields $undecided or nothing confirmed" }
        return SavedClinicForm(
            reportId = reportId,
            savedAt = savedAt,
            fields = fields.filter { it.state == FieldState.CONFIRMED }
                .map { SavedField(it.key, it.value.trim(), it.sourceIds, it.edited) },
        )
    }

    private fun update(key: FormFieldKey, change: (FormField) -> FormField): ClinicForm {
        require(fields.any { it.key == key }) { "no field $key in this form" }
        return copy(fields = fields.map { if (it.key == key) change(it) else it })
    }
}

/** What goes to the clinic system: confirmed fields only, each with the entries it came from. */
@Serializable
data class SavedClinicForm(val reportId: String, val savedAt: Long, val fields: List<SavedField>)

@Serializable
data class SavedField(val key: FormFieldKey, val value: String, val sourceIds: List<String>, val editedByDoctor: Boolean)

// --- Pre-fill: facts from the record, no AI. The wording comes from the UI (localized). ---

data class BloodPressureStats(
    val readings: Int,
    val firstAt: Long,
    val lastAt: Long,
    val systolic: IntRange,
    val diastolic: IntRange,
    val pulse: IntRange?,
)

data class MedicationLine(val name: String, val dose: String?, val unconfirmedScan: Boolean)

/** Average over the days that have at least one entry. */
data class FluidStats(val mlPerDay: Int, val days: Int, val entries: Int)

/** Turns facts into the field text in the doctor's language. */
interface FormWording {
    fun bloodPressure(stats: BloodPressureStats, utcOffsetMinutes: Int): String
    fun medications(items: List<MedicationLine>): String
    fun fluidIntake(stats: FluidStats): String
    fun patientQuestions(items: List<String>): String
    fun noEntries(): String
}

/** Default wording (English, compact clinical register); the doctor screen passes a localized one. */
object EnglishFormWording : FormWording {
    override fun bloodPressure(stats: BloodPressureStats, utcOffsetMinutes: Int) = buildString {
        append("${stats.readings} readings, ${localDate(stats.firstAt, utcOffsetMinutes)} – ${localDate(stats.lastAt, utcOffsetMinutes)}: ")
        append("${stats.systolic.range()}/${stats.diastolic.range()} mmHg")
        stats.pulse?.let { append(", pulse ${it.range()}/min") }
    }

    override fun medications(items: List<MedicationLine>) = items.joinToString("\n") { item ->
        listOfNotNull(item.name, item.dose).joinToString(", ") + if (item.unconfirmedScan) " (scan not confirmed by patient)" else ""
    }

    override fun fluidIntake(stats: FluidStats) = "${stats.mlPerDay} mL/day on average (${stats.days} days, ${stats.entries} entries)"

    override fun patientQuestions(items: List<String>) = items.joinToString("\n") { "– $it" }

    override fun noEntries() = "No entries"

    private fun IntRange.range() = if (first == last) "$first" else "$first–$last"
}

/** "YYYY-MM-DD" of an instant in the patient's time zone, given as an offset from UTC. */
@OptIn(ExperimentalTime::class)
fun localDate(epochMillis: Long, utcOffsetMinutes: Int): String =
    Instant.fromEpochMilliseconds(epochMillis + utcOffsetMinutes * 60_000L).toString().take(10)

/**
 * Builds the form from the report. Fields exist only for widgets the doctor requested; complaint and
 * history are always there (history = the AI summary without link markers, sourced by its links).
 */
fun prefillClinicForm(report: Report, wording: FormWording = EnglishFormWording, utcOffsetMinutes: Int = 0): ClinicForm {
    val record = report.record
    val widgets = record.request.widgets
    fun entriesOf(widgetType: (WidgetSpec) -> Boolean): List<Entry> {
        val ids = widgets.filter(widgetType).map { it.id }.toSet()
        return record.entries.filter { it.widgetId in ids }.sortedBy { it.at }
    }
    fun field(key: FormFieldKey, entries: List<Entry>, text: (List<Entry>) -> String) =
        if (entries.isEmpty()) FormField(key, wording.noEntries(), emptyList())
        else FormField(key, text(entries), entries.map { it.id })

    val summary = parseSummary(report)
    val fields = buildList {
        add(FormField(FormFieldKey.COMPLAINT, record.request.complaint, emptyList()))
        add(FormField(FormFieldKey.HISTORY, summary.lines.joinToString("\n") { it.text }, summary.citedIds))
        if (widgets.any { it is BloodPressureWidget }) {
            val bp = entriesOf { it is BloodPressureWidget }.filter { it.payload is BloodPressure }
            add(field(FormFieldKey.BLOOD_PRESSURE, bp) { wording.bloodPressure(bloodPressureStats(it), utcOffsetMinutes) })
        }
        if (widgets.any { it is MedicationsWidget }) {
            val meds = entriesOf { it is MedicationsWidget }.filter { it.payload is Medication }
            add(field(FormFieldKey.MEDICATIONS, meds) { wording.medications(medicationLines(it)) })
        }
        if (widgets.any { it is WaterWidget }) {
            val water = entriesOf { it is WaterWidget }.filter { it.payload is Water }
            add(field(FormFieldKey.FLUID_INTAKE, water) { wording.fluidIntake(fluidStats(it, utcOffsetMinutes)) })
        }
        if (widgets.any { it is DoctorQuestionsWidget }) {
            val questions = entriesOf { it is DoctorQuestionsWidget }.filter { it.payload is DoctorQuestion }
            add(field(FormFieldKey.PATIENT_QUESTIONS, questions) { list -> wording.patientQuestions(list.map { (it.payload as DoctorQuestion).text }) })
        }
    }
    return ClinicForm(report.id, fields)
}

internal fun bloodPressureStats(entries: List<Entry>): BloodPressureStats {
    val values = entries.map { it.payload as BloodPressure }
    val pulses = values.mapNotNull { it.pulse }
    return BloodPressureStats(
        readings = values.size,
        firstAt = entries.minOf { it.at },
        lastAt = entries.maxOf { it.at },
        systolic = values.minOf { it.systolic }..values.maxOf { it.systolic },
        diastolic = values.minOf { it.diastolic }..values.maxOf { it.diastolic },
        pulse = if (pulses.isEmpty()) null else pulses.min()..pulses.max(),
    )
}

/** One line per medicine (by name and dose), in order of first entry. */
internal fun medicationLines(entries: List<Entry>): List<MedicationLine> =
    entries.map { it.payload as Medication }
        .groupBy { it.name.trim().lowercase() to it.dose?.trim()?.lowercase() }
        .values.map { same ->
            val first = same.first()
            // Unconfirmed only when no entry of this medicine was confirmed or typed in by the patient.
            val unconfirmed = same.all { it.source == MedicationSource.SCAN && !it.confirmed }
            MedicationLine(first.name.trim(), first.dose?.trim(), unconfirmed)
        }

internal fun fluidStats(entries: List<Entry>, utcOffsetMinutes: Int): FluidStats {
    val byDay = entries.groupBy { localDate(it.at, utcOffsetMinutes) }
    val total = entries.sumOf { (it.payload as Water).ml }
    return FluidStats(mlPerDay = total / byDay.size, days = byDay.size, entries = entries.size)
}
