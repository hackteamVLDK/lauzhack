package org.easypaper.ui.patient

import androidx.compose.runtime.Composable
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.decimal_separator
import easypaper.app.shared.generated.resources.no
import easypaper.app.shared.generated.resources.not_confirmed
import easypaper.app.shared.generated.resources.photo
import easypaper.app.shared.generated.resources.pulse
import easypaper.app.shared.generated.resources.unit_ml
import easypaper.app.shared.generated.resources.unit_mmhg
import easypaper.app.shared.generated.resources.yes
import org.easypaper.model.Answers
import org.easypaper.model.BloodPressure
import org.easypaper.model.CareRequest
import org.easypaper.model.DiaryNote
import org.easypaper.model.DoctorQuestion
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.Medication
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.Water
import org.jetbrains.compose.resources.stringResource

/**
 * One entry as the patient wrote it: values with units, their own words, nothing added or judged.
 * [request] gives questionnaire answers their question text; without it only the answers are listed.
 */
@Composable
fun entryText(entry: Entry, request: CareRequest?): String = when (val p = entry.payload) {
    is BloodPressure -> buildString {
        append("${p.systolic}/${p.diastolic} ").append(stringResource(Res.string.unit_mmhg))
        p.pulse?.let { append(", ").append(stringResource(Res.string.pulse, it.toString())) }
    }
    is Water -> stringResource(Res.string.unit_ml, p.ml)
    is Food -> p.text?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.photo)
    is DiaryNote -> p.text.ifBlank { stringResource(Res.string.photo) }
    is DoctorQuestion -> p.text
    // The scanned package name often contains the strength already ("… 20 mg 15 Stk"): show the dose once.
    is Medication -> listOfNotNull(p.name, p.dose?.takeIf { it !in p.name }).joinToString(", ") +
        if (p.confirmed) "" else " (" + stringResource(Res.string.not_confirmed) + ")"
    is Answers -> {
        val questions = request?.widgets?.filterIsInstance<QuestionnaireWidget>()?.flatMap { it.questions }?.associateBy { it.id }.orEmpty()
        val yes = stringResource(Res.string.yes)
        val no = stringResource(Res.string.no)
        p.values.entries.joinToString("\n") { (id, value) ->
            val shown = when (value) { "yes" -> yes; "no" -> no; else -> value }
            questions[id]?.let { "${it.text} $shown" } ?: shown
        }
    }
}

/** The decimal separator of the app language. */
@Composable
fun decimalSeparator(): Char = stringResource(Res.string.decimal_separator).firstOrNull() ?: '.'
