package org.easypaper.diary

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
import org.easypaper.scan.SIDE_EFFECTS_BY_KEY
import org.easypaper.model.MedicationsWidget
import org.easypaper.model.QuestionKind
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.Water
import org.easypaper.model.WaterWidget

/** Input problems; the UI turns them into localized hints. */
enum class EntryProblem {
    UNKNOWN_WIDGET, WRONG_WIDGET_TYPE,
    SYSTOLIC_OUT_OF_RANGE, DIASTOLIC_OUT_OF_RANGE, PULSE_OUT_OF_RANGE, DIASTOLIC_NOT_BELOW_SYSTOLIC,
    WATER_OUT_OF_RANGE, EMPTY_TEXT, FOOD_EMPTY, EMPTY_MEDICATION_NAME, UNKNOWN_QUESTION, BAD_ANSWER,
    EMPTY_MEASUREMENT_NAME, MEASUREMENT_OUT_OF_RANGE,
}

// Typo guards: values a measuring device or a glass can physically give, NOT medical norms.
internal val SYSTOLIC = 50..300
internal val DIASTOLIC = 20..200
internal val PULSE = 20..250
internal val WATER_ML = 1..5000
internal val WEIGHT_KG = 1.0..500.0
internal val ANY_MEASUREMENT = 0.0..1_000_000.0 // own units vary; only a typo guard against absurd numbers

/** Checks one entry against this request before it is saved. Empty list = fine. */
fun CareRequest.check(entry: Entry): List<EntryProblem> {
    val widget = widgets.firstOrNull { it.id == entry.widgetId } ?: return listOf(EntryProblem.UNKNOWN_WIDGET)
    val p = entry.payload
    val fits = when (p) {
        is Answers -> widget is QuestionnaireWidget
        is BloodPressure -> widget is BloodPressureWidget
        is Water -> widget is WaterWidget
        is Food -> widget is FoodWidget
        is Medication -> widget is MedicationsWidget
        is DiaryNote -> widget is DiaryWidget
        is DoctorQuestion -> widget is DoctorQuestionsWidget
        is Measurement -> widget is MeasurementsWidget
        is SideEffectCheck -> widget is MedicationsWidget
    }
    if (!fits) return listOf(EntryProblem.WRONG_WIDGET_TYPE)
    return p.problems(widget as? QuestionnaireWidget)
}

/**
 * The value rules of a payload, with or without a doctor's request (v2 diary, #47). Answers need the
 * questionnaire they answer; without one every answer is an unknown question.
 */
fun EntryPayload.problems(questionnaire: QuestionnaireWidget? = null): List<EntryProblem> {
    val p = this
    return buildList {
        when (p) {
            is BloodPressure -> {
                if (p.systolic !in SYSTOLIC) add(EntryProblem.SYSTOLIC_OUT_OF_RANGE)
                if (p.diastolic !in DIASTOLIC) add(EntryProblem.DIASTOLIC_OUT_OF_RANGE)
                if (p.pulse != null && p.pulse !in PULSE) add(EntryProblem.PULSE_OUT_OF_RANGE)
                if (p.diastolic >= p.systolic) add(EntryProblem.DIASTOLIC_NOT_BELOW_SYSTOLIC)
            }
            is Water -> if (p.ml !in WATER_ML) add(EntryProblem.WATER_OUT_OF_RANGE)
            is Food -> if (p.text.isNullOrBlank() && p.photoRef == null) add(EntryProblem.FOOD_EMPTY)
            is DiaryNote -> if (p.text.isBlank() && p.photoRef == null) add(EntryProblem.EMPTY_TEXT)
            is DoctorQuestion -> if (p.text.isBlank()) add(EntryProblem.EMPTY_TEXT)
            is Medication -> if (p.name.isBlank()) add(EntryProblem.EMPTY_MEDICATION_NAME)
            is SideEffectCheck -> {
                if (p.medication.isBlank()) add(EntryProblem.EMPTY_MEDICATION_NAME)
                if (p.answers.isEmpty()) add(EntryProblem.BAD_ANSWER)
                for ((key, value) in p.answers) {
                    if (key !in SIDE_EFFECTS_BY_KEY) add(EntryProblem.UNKNOWN_QUESTION)
                    else if (value != "yes" && value != "no") add(EntryProblem.BAD_ANSWER)
                }
            }
            is Measurement -> {
                if (p.name.isBlank()) add(EntryProblem.EMPTY_MEASUREMENT_NAME)
                val limit = if (p.loinc == Measurement.WEIGHT_LOINC) WEIGHT_KG else ANY_MEASUREMENT
                if (!p.value.isFinite() || p.value !in limit) add(EntryProblem.MEASUREMENT_OUT_OF_RANGE)
            }
            is Answers -> {
                val questions = questionnaire?.questions.orEmpty().associateBy { it.id }
                for ((id, value) in p.values) {
                    val q = questions[id]
                    if (q == null) { add(EntryProblem.UNKNOWN_QUESTION); continue }
                    val ok = when (q.kind) {
                        QuestionKind.YES_NO -> value == "yes" || value == "no"
                        QuestionKind.SCALE -> value.toIntOrNull() in 0..10
                        QuestionKind.CHOICE -> value in q.options
                        QuestionKind.TEXT -> value.isNotBlank()
                    }
                    if (!ok) add(EntryProblem.BAD_ANSWER)
                }
            }
        }
    }.distinct()
}
