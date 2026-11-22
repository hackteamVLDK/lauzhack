package org.easypaper.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Bump only together with a migration and a note to the UI/report owners. */
const val SCHEMA_VERSION = 1

/** The one JSON config for requests, records and reports. Polymorphic types use the "type" field. */
val EasyPaperJson = Json {
    ignoreUnknownKeys = true // older apps read newer optional fields
    encodeDefaults = true
}

/** What the doctor builds for one complaint; the patient fills it for [days] days from [startDate]. */
@Serializable
data class CareRequest(
    val schemaVersion: Int = SCHEMA_VERSION,
    val id: String,
    val createdAt: Long, // epoch millis UTC
    val doctorName: String,
    val complaint: String,
    val startDate: String, // ISO date, e.g. "2026-11-21"
    val days: Int,
    val locale: String, // BCP 47, e.g. "ru", "en"
    val widgets: List<WidgetSpec>,
    val personToken: String? = null,
) {
    init {
        require(schemaVersion in 1..SCHEMA_VERSION) { "Unsupported schemaVersion $schemaVersion, app knows $SCHEMA_VERSION" }
        require(days > 0) { "days must be > 0" }
        require(widgets.map { it.id }.toSet().size == widgets.size) { "widget ids must be unique" }
    }
}

@Serializable
sealed interface WidgetSpec {
    val id: String
    val title: String
    val required: Boolean
}

@Serializable
@SerialName("questionnaire")
data class QuestionnaireWidget(
    override val id: String,
    override val title: String,
    override val required: Boolean = true,
    val questions: List<Question>,
) : WidgetSpec

@Serializable
data class Question(
    val id: String,
    val text: String,
    val kind: QuestionKind,
    val options: List<String> = emptyList(), // for CHOICE; for SCALE: ["min", "max"] labels, values 0..10
)

@Serializable
enum class QuestionKind {
    @SerialName("yesno") YES_NO,
    @SerialName("scale") SCALE,
    @SerialName("choice") CHOICE,
    @SerialName("text") TEXT,
}

@Serializable
@SerialName("blood_pressure")
data class BloodPressureWidget(
    override val id: String,
    override val title: String,
    override val required: Boolean = true,
    val timesPerDay: Int = 2,
) : WidgetSpec

@Serializable
@SerialName("water")
data class WaterWidget(
    override val id: String,
    override val title: String,
    override val required: Boolean = true,
    val targetMl: Int? = null,
) : WidgetSpec

@Serializable
@SerialName("food")
data class FoodWidget(
    override val id: String,
    override val title: String,
    override val required: Boolean = true,
    val photo: Boolean = true, // photo is offered, never mandatory
) : WidgetSpec

@Serializable
@SerialName("medications")
data class MedicationsWidget(
    override val id: String,
    override val title: String,
    override val required: Boolean = true,
    val scan: Boolean = true, // barcode/OCR scan offered
) : WidgetSpec

@Serializable
@SerialName("diary")
data class DiaryWidget(
    override val id: String,
    override val title: String,
    override val required: Boolean = false,
) : WidgetSpec

@Serializable
@SerialName("doctor_questions")
data class DoctorQuestionsWidget(
    override val id: String,
    override val title: String,
    override val required: Boolean = false,
) : WidgetSpec

/** The person's own measurements (weight and anything else they track); never required (#52). */
@Serializable
@SerialName("measurements")
data class MeasurementsWidget(
    override val id: String,
    override val title: String,
    override val required: Boolean = false,
) : WidgetSpec

/** One thing the patient recorded. Photos are only referenced by [photoRef]; bytes live in a separate store. */
@Serializable
data class Entry(
    val id: String,
    val widgetId: String,
    val at: Long, // epoch millis UTC
    val payload: EntryPayload,
)

@Serializable
sealed interface EntryPayload

/** questionId -> answer: "yes"/"no", "0".."10", the chosen option, or free text. */
@Serializable
@SerialName("answers")
data class Answers(val values: Map<String, String>) : EntryPayload

@Serializable
@SerialName("blood_pressure")
data class BloodPressure(val systolic: Int, val diastolic: Int, val pulse: Int? = null) : EntryPayload

@Serializable
@SerialName("water")
data class Water(val ml: Int) : EntryPayload

@Serializable
@SerialName("food")
data class Food(val text: String? = null, val photoRef: String? = null) : EntryPayload

@Serializable
@SerialName("medication")
data class Medication(
    val name: String,
    val code: String? = null, // GTIN / DataMatrix content
    val dose: String? = null,
    val source: MedicationSource,
    val confirmed: Boolean, // patient confirmed what the scan found
    /** The registry card the patient confirmed (task 71); null for medicines typed in or kept in the patient's words. */
    val product: MedicineProduct? = null,
    /** How much the patient says they took and when; null = only added to their list, not taken. */
    val intake: Intake? = null,
    /** The patient's own words when no registry card was confirmed ("cold powder, can't remember the name"). */
    val patientWords: String? = null,
    /** Ingredients copied from the label of a tea or supplement, as printed; never checked against a registry. */
    val labelIngredients: String? = null,
) : EntryPayload {
    /**
     * Whether this entry records an intake. A task-71 entry (registry card or the patient's words) is an intake only
     * with [intake]: "only add it to my list" is not one. Older entries keep their meaning: confirmed = taken.
     */
    val isIntake: Boolean get() = confirmed && !((product != null || patientWords != null) && intake == null)
}

@Serializable
enum class MedicationSource {
    @SerialName("scan") SCAN,
    @SerialName("manual") MANUAL,
}

@Serializable
@SerialName("diary_note")
data class DiaryNote(val text: String, val photoRef: String? = null) : EntryPayload

@Serializable
@SerialName("doctor_question")
data class DoctorQuestion(val text: String) : EntryPayload

/**
 * A number the person measured themselves (#52): weight, waist, blood sugar from their own meter… [name] as they
 * call it, [unit] as written; [loinc] for a known kind (weight: 29463-7), so the report can code it.
 */
/**
 * The person's answers about side effects listed for a medicine they take (#53): side effect key -> "yes"/"no".
 * Asked once a day for confirmed medicines; a record of what was noticed, never a judgement.
 */
@Serializable
@SerialName("side_effects")
data class SideEffectCheck(val medication: String, val gtin: String? = null, val answers: Map<String, String>) : EntryPayload

@Serializable
@SerialName("measurement")
data class Measurement(val name: String, val value: Double, val unit: String, val loinc: String? = null) : EntryPayload {
    companion object {
        const val WEIGHT_LOINC = "29463-7"
        fun weight(kg: Double) = Measurement("Weight", kg, "kg", WEIGHT_LOINC)
    }
}

/** Request + everything the patient entered: the input of the report (task #9). */
@Serializable
data class PatientRecord(
    val request: CareRequest,
    val entries: List<Entry> = emptyList(),
)
