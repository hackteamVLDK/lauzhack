package org.easypaper.visit

import kotlinx.serialization.Serializable
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.DoctorQuestionsWidget
import org.easypaper.model.MedicationsWidget
import org.easypaper.model.WaterWidget
import org.easypaper.report.FactDigest
import org.easypaper.report.MedicationFact
import org.easypaper.report.Report
import org.easypaper.report.factDigest

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

// --- Pre-fill: facts from FactDigest (#13), no AI. The wording comes from the UI (localized). ---

/** Blood pressure over the whole period; dates are the patient's local days. */
data class BloodPressureStats(
    val readings: Int,
    val firstDate: String,
    val lastDate: String,
    val systolic: IntRange,
    val diastolic: IntRange,
    val pulse: IntRange?,
)

/** [unconfirmed]: the patient never confirmed this medicine (e.g. a scan they did not check). */
data class MedicationLine(val name: String, val dose: String?, val unconfirmed: Boolean)

/** Average over the days that have at least one entry. */
data class FluidStats(val mlPerDay: Int, val days: Int, val entries: Int)

/** Turns facts into the field text in the doctor's language. */
interface FormWording {
    fun bloodPressure(stats: BloodPressureStats): String
    fun medications(items: List<MedicationLine>): String
    fun fluidIntake(stats: FluidStats): String
    fun patientQuestions(items: List<String>): String
    fun noEntries(): String
}

/** Default wording (English, compact clinical register); the doctor screen passes a localized one. */
object EnglishFormWording : FormWording {
    override fun bloodPressure(stats: BloodPressureStats) = buildString {
        append("${stats.readings} readings, ${stats.firstDate} – ${stats.lastDate}: ")
        append("${stats.systolic.range()}/${stats.diastolic.range()} mmHg")
        stats.pulse?.let { append(", pulse ${it.range()}/min") }
    }

    override fun medications(items: List<MedicationLine>) = items.joinToString("\n") { item ->
        listOfNotNull(item.name, item.dose).joinToString(", ") + if (item.unconfirmed) " (not confirmed by patient)" else ""
    }

    override fun fluidIntake(stats: FluidStats) = "${stats.mlPerDay} mL/day on average (${stats.days} days, ${stats.entries} entries)"

    override fun patientQuestions(items: List<String>) = items.joinToString("\n") { "– $it" }

    override fun noEntries() = "No entries"

    private fun IntRange.range() = if (first == last) "$first" else "$first–$last"
}

/**
 * Builds the form from the report. Fields exist only for widgets the doctor requested; complaint and
 * history are always there (history = the AI summary without link markers, sourced by its links).
 */
fun prefillClinicForm(report: Report, wording: FormWording = EnglishFormWording, utcOffsetMinutes: Int = 0): ClinicForm {
    val widgets = report.record.request.widgets
    val digest = report.record.factDigest(utcOffsetMinutes)
    val summary = parseSummary(report)
    fun field(key: FormFieldKey, refs: List<String>, text: () -> String) =
        if (refs.isEmpty()) FormField(key, wording.noEntries(), emptyList()) else FormField(key, text(), refs)

    val fields = buildList {
        add(FormField(FormFieldKey.COMPLAINT, report.record.request.complaint, emptyList()))
        add(FormField(FormFieldKey.HISTORY, summary.lines.joinToString("\n") { it.text }, summary.citedIds))
        if (widgets.any { it is BloodPressureWidget }) {
            add(field(FormFieldKey.BLOOD_PRESSURE, digest.bloodPressure.flatMap { it.refs }) { wording.bloodPressure(digest.bloodPressureStats()!!) })
        }
        if (widgets.any { it is MedicationsWidget }) {
            add(field(FormFieldKey.MEDICATIONS, digest.medications.flatMap { it.refs }) { wording.medications(medicationLines(digest.medications)) })
        }
        if (widgets.any { it is WaterWidget }) {
            add(field(FormFieldKey.FLUID_INTAKE, digest.water.flatMap { it.refs }) { wording.fluidIntake(digest.fluidStats()!!) })
        }
        if (widgets.any { it is DoctorQuestionsWidget }) {
            val questions = digest.notes.filter { it.kind == "doctor_question" && !it.text.isNullOrBlank() }
            add(field(FormFieldKey.PATIENT_QUESTIONS, questions.map { it.ref }) { wording.patientQuestions(questions.map { it.text!! }) })
        }
    }
    return ClinicForm(report.id, fields)
}

internal fun FactDigest.bloodPressureStats(): BloodPressureStats? {
    if (bloodPressure.isEmpty()) return null
    val pulses = bloodPressure.mapNotNull { it.pulse }
    return BloodPressureStats(
        readings = bloodPressure.sumOf { it.count },
        firstDate = bloodPressure.first().date,
        lastDate = bloodPressure.last().date,
        systolic = bloodPressure.minOf { it.systolic.min }..bloodPressure.maxOf { it.systolic.max },
        diastolic = bloodPressure.minOf { it.diastolic.min }..bloodPressure.maxOf { it.diastolic.max },
        pulse = if (pulses.isEmpty()) null else pulses.minOf { it.min }..pulses.maxOf { it.max },
    )
}

internal fun FactDigest.fluidStats(): FluidStats? =
    if (water.isEmpty()) null
    else FluidStats(mlPerDay = water.sumOf { it.totalMl } / water.size, days = water.size, entries = water.sumOf { it.refs.size })

/**
 * One line per medicine (by name and dose). The digest keeps confirmed and unconfirmed apart; here a medicine is
 * flagged only when the patient confirmed it nowhere.
 */
internal fun medicationLines(facts: List<MedicationFact>): List<MedicationLine> =
    facts.groupBy { it.name.trim().lowercase() to it.dose?.trim()?.lowercase() }
        .values.map { same -> MedicationLine(same.first().name.trim(), same.first().dose?.trim(), same.none { it.confirmed }) }
