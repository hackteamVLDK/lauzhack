package org.easypaper.ui.doctor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.easypaper.invite.DraftError
import org.easypaper.invite.MAX_REQUEST_DAYS
import org.easypaper.invite.RequestDraft
import org.easypaper.invite.newRequestId
import org.easypaper.invite.toCareRequest
import org.easypaper.invite.validate
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.CareRequest
import org.easypaper.model.DiaryWidget
import org.easypaper.model.DoctorQuestionsWidget
import org.easypaper.model.FoodWidget
import org.easypaper.model.MedicationsWidget
import org.easypaper.model.Question
import org.easypaper.model.QuestionKind
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.WaterWidget
import org.easypaper.model.WidgetSpec

/** The kinds of widget a doctor can ask for, in the order the patient sees them. */
enum class WidgetKind(val id: String) {
    Questionnaire("q"), BloodPressure("bp"), Water("w"), Food("f"), Medicines("m"), Diary("d"), DoctorQuestions("dq")
}

/** One question of the daily questionnaire as the doctor types it. */
data class QuestionDraft(val text: String, val kind: QuestionKind)

/** Most common request: a short daily check, blood pressure twice a day, medicines, how the patient feels. */
val DEFAULT_KINDS = setOf(WidgetKind.Questionnaire, WidgetKind.BloodPressure, WidgetKind.Medicines, WidgetKind.Diary, WidgetKind.DoctorQuestions)

const val WATER_TARGET_STEP_ML = 250
const val WATER_TARGET_MAX_ML = 4000
const val BP_TIMES_MAX = 3

/**
 * What the doctor sets in "New request" (task 5). [build] turns it into the [RequestDraft] of core, whose
 * [validate] says what is missing; [create] gives the [CareRequest] that the invite carries.
 */
@Stable
class RequestBuilderState(doctorName: String = "", startQuestions: List<QuestionDraft> = emptyList()) {
    var complaint by mutableStateOf("")
    var doctorName by mutableStateOf(doctorName)
    /** Start = today + this many days. */
    var startOffsetDays by mutableStateOf(0)
    var days by mutableStateOf(7)
    val enabled = mutableStateMapOf<WidgetKind, Boolean>().apply { WidgetKind.entries.forEach { put(it, it in DEFAULT_KINDS) } }
    var bpTimesPerDay by mutableStateOf(2)
    /** 0 = no daily target. */
    var waterTargetMl by mutableStateOf(0)
    val questions = mutableStateListOf<QuestionDraft>().apply { addAll(startQuestions) }

    /** Errors after the doctor tried to create the invite; empty before. */
    var errors by mutableStateOf(emptyList<DraftError>())
        private set

    fun changeDays(by: Int) { days = (days + by).coerceIn(1, MAX_REQUEST_DAYS) }
    fun changeStart(by: Int) { startOffsetDays = (startOffsetDays + by).coerceIn(0, MAX_REQUEST_DAYS) }
    fun changeBpTimes(by: Int) { bpTimesPerDay = (bpTimesPerDay + by).coerceIn(1, BP_TIMES_MAX) }
    fun changeWaterTarget(by: Int) { waterTargetMl = (waterTargetMl + by * WATER_TARGET_STEP_ML).coerceIn(0, WATER_TARGET_MAX_ML) }

    fun startDate(today: LocalDate): LocalDate = today.plus(DatePeriod(days = startOffsetDays))

    /**
     * The draft in the doctor's language: [titles] names each widget as the patient will read it,
     * [locale] is the app language (BCP 47), [scaleEnds] the words at both ends of a 0–10 scale.
     */
    fun build(today: LocalDate, titles: Map<WidgetKind, String>, locale: String, scaleEnds: List<String> = emptyList()): RequestDraft {
        val widgets: List<WidgetSpec> = WidgetKind.entries.filter { enabled[it] == true }.map { kind ->
            val title = titles[kind].orEmpty()
            when (kind) {
                WidgetKind.Questionnaire -> QuestionnaireWidget(
                    kind.id, title,
                    questions = questions.mapIndexed { i, q ->
                        Question("q${i + 1}", q.text.trim(), q.kind, if (q.kind == QuestionKind.SCALE) scaleEnds else emptyList())
                    },
                )
                WidgetKind.BloodPressure -> BloodPressureWidget(kind.id, title, timesPerDay = bpTimesPerDay)
                WidgetKind.Water -> WaterWidget(kind.id, title, targetMl = waterTargetMl.takeIf { it > 0 })
                WidgetKind.Food -> FoodWidget(kind.id, title, required = false)
                WidgetKind.Medicines -> MedicationsWidget(kind.id, title)
                WidgetKind.Diary -> DiaryWidget(kind.id, title)
                WidgetKind.DoctorQuestions -> DoctorQuestionsWidget(kind.id, title)
            }
        }
        return RequestDraft(
            doctorName = doctorName,
            complaint = complaint,
            startDate = startDate(today).toString(),
            days = days,
            locale = locale,
            widgets = widgets,
        )
    }

    /** The request, or null with [errors] set to what the doctor has to fix. */
    fun create(
        today: LocalDate,
        titles: Map<WidgetKind, String>,
        locale: String,
        now: Long,
        scaleEnds: List<String> = emptyList(),
        id: String = newRequestId(),
    ): CareRequest? {
        val draft = build(today, titles, locale, scaleEnds)
        errors = draft.validate()
        return if (errors.isEmpty()) draft.toCareRequest(id, now) else null
    }
}
