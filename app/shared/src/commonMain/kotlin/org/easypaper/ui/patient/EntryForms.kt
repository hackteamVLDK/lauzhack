package org.easypaper.ui.patient

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.bp_missing
import easypaper.app.shared.generated.resources.diastolic
import easypaper.app.shared.generated.resources.feeling_placeholder
import easypaper.app.shared.generated.resources.ic_remove
import easypaper.app.shared.generated.resources.meal_placeholder
import easypaper.app.shared.generated.resources.no
import easypaper.app.shared.generated.resources.problem_diastolic
import easypaper.app.shared.generated.resources.problem_empty
import easypaper.app.shared.generated.resources.problem_measurement
import easypaper.app.shared.generated.resources.problem_order
import easypaper.app.shared.generated.resources.problem_other
import easypaper.app.shared.generated.resources.problem_pulse
import easypaper.app.shared.generated.resources.problem_systolic
import easypaper.app.shared.generated.resources.problem_water
import easypaper.app.shared.generated.resources.pulse_optional
import easypaper.app.shared.generated.resources.question_placeholder
import easypaper.app.shared.generated.resources.questions_required
import easypaper.app.shared.generated.resources.save
import easypaper.app.shared.generated.resources.systolic
import easypaper.app.shared.generated.resources.unit_mmhg
import easypaper.app.shared.generated.resources.unit_per_min
import easypaper.app.shared.generated.resources.yes
import org.easypaper.diary.EntryProblem
import org.easypaper.model.Answers
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.DiaryNote
import org.easypaper.model.DiaryWidget
import org.easypaper.model.DoctorQuestion
import org.easypaper.model.DoctorQuestionsWidget
import org.easypaper.model.EntryPayload
import org.easypaper.model.Food
import org.easypaper.model.FoodWidget
import org.easypaper.model.QuestionKind
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.WidgetSpec
import easypaper.app.shared.generated.resources.m_missing
import easypaper.app.shared.generated.resources.m_kg
import easypaper.app.shared.generated.resources.m_unit_example
import easypaper.app.shared.generated.resources.m_unit
import easypaper.app.shared.generated.resources.m_value
import easypaper.app.shared.generated.resources.m_name_example
import easypaper.app.shared.generated.resources.m_name
import easypaper.app.shared.generated.resources.m_other
import easypaper.app.shared.generated.resources.m_weight
import androidx.compose.ui.semantics.Role
import org.easypaper.ui.components.TextFieldRow
import org.easypaper.model.MeasurementsWidget
import org.easypaper.model.Measurement
import org.easypaper.ui.components.ChoiceList
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.NumberFieldRow
import org.easypaper.ui.components.ProminentButton
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.components.ScaleInput
import org.easypaper.ui.components.SegmentedControl
import org.easypaper.ui.components.TextAreaRow
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.easypaper.ui.components.GrayButton
import org.easypaper.ui.capture.rememberPhotoSource
import easypaper.app.shared.generated.resources.take_photo
import easypaper.app.shared.generated.resources.remove_photo
import easypaper.app.shared.generated.resources.photo_optional
import easypaper.app.shared.generated.resources.photo
import easypaper.app.shared.generated.resources.choose_photo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The form for one widget, shown in a sheet. [problems] come back from the repository's check after
 * [onSave]; the patient sees each one as a sentence that says what to change.
 */
@Composable
fun ColumnScope.EntryForm(
    widget: WidgetSpec,
    problems: List<EntryProblem>,
    saving: Boolean,
    onSave: (payload: EntryPayload, photo: ByteArray?) -> Unit,
) {
    when (widget) {
        is BloodPressureWidget -> PressureForm(problems, saving) { onSave(it, null) }
        is QuestionnaireWidget -> QuestionnaireForm(widget, problems, saving) { onSave(it, null) }
        // Food and how you feel take a photo if the patient wants one; the text may then stay empty.
        is FoodWidget -> TextForm(Res.string.meal_placeholder, problems, saving, withPhoto = true) { text, photo ->
            onSave(Food(text = text.ifBlank { null }), photo)
        }
        is DiaryWidget -> TextForm(Res.string.feeling_placeholder, problems, saving, withPhoto = true) { text, photo ->
            onSave(DiaryNote(text), photo)
        }
        is DoctorQuestionsWidget -> TextForm(Res.string.question_placeholder, problems, saving) { text, _ -> onSave(DoctorQuestion(text), null) }
        is MeasurementsWidget -> MeasurementsForm(problems, saving) { onSave(it, null) }
        else -> Unit // water has no form (stepper), medicines have their own screen (task 31)
    }
}

@Composable
private fun ColumnScope.PressureForm(problems: List<EntryProblem>, saving: Boolean, onSave: (EntryPayload) -> Unit) {
    var systolic by remember { mutableStateOf("") }
    var diastolic by remember { mutableStateOf("") }
    var pulse by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    val mmHg = stringResource(Res.string.unit_mmhg)
    GroupedSection {
        NumberFieldRow(stringResource(Res.string.systolic), systolic, { systolic = it; missing = false }, unit = mmHg, placeholder = "120")
        RowSeparator(EasyPaperTheme.spacing.m)
        NumberFieldRow(stringResource(Res.string.diastolic), diastolic, { diastolic = it; missing = false }, unit = mmHg, placeholder = "80")
        RowSeparator(EasyPaperTheme.spacing.m)
        NumberFieldRow(stringResource(Res.string.pulse_optional), pulse, { pulse = it }, unit = stringResource(Res.string.unit_per_min), placeholder = "70")
    }
    Problems(if (missing) listOf(Res.string.bp_missing) else problems.map { it.message() })
    SaveButton(saving) {
        val bp = bloodPressureFromFields(systolic, diastolic, pulse)
        if (bp == null) missing = true else onSave(bp)
    }
}

@Composable
private fun ColumnScope.QuestionnaireForm(widget: QuestionnaireWidget, problems: List<EntryProblem>, saving: Boolean, onSave: (EntryPayload) -> Unit) {
    val answers = remember { mutableStateMapOf<String, String>() }
    val yes = stringResource(Res.string.yes)
    val no = stringResource(Res.string.no)
    val spacing = EasyPaperTheme.spacing
    widget.questions.forEachIndexed { index, question ->
        if (index > 0) Spacer(Modifier.height(spacing.l))
        Text(question.text, style = EasyPaperTheme.typography.headline, color = EasyPaperTheme.colors.label,
            modifier = Modifier.padding(start = spacing.m, end = spacing.m, bottom = spacing.xs))
        when (question.kind) {
            QuestionKind.YES_NO -> SegmentedControl(
                options = listOf(yes, no),
                selected = when (answers[question.id]) { "yes" -> 0; "no" -> 1; else -> null },
                onSelect = { answers[question.id] = if (it == 0) "yes" else "no" },
            )
            QuestionKind.SCALE -> ScaleInput(
                value = answers[question.id]?.toIntOrNull(),
                onValueChange = { answers[question.id] = it.toString() },
                minLabel = question.options.getOrElse(0) { "" },
                maxLabel = question.options.getOrElse(1) { "" },
            )
            QuestionKind.CHOICE -> ChoiceList(question.options, answers[question.id], onSelect = { answers[question.id] = it })
            QuestionKind.TEXT -> GroupedSection {
                TextAreaRow(answers[question.id].orEmpty(), { answers[question.id] = it }, placeholder = question.text)
            }
        }
    }
    // Yes/no and number questions need an answer; a choice may not apply that day, text is optional.
    val required = widget.questions.filter { it.kind == QuestionKind.YES_NO || it.kind == QuestionKind.SCALE }
    val complete = required.all { answers[it.id] != null }
    if (!complete) {
        Text(stringResource(Res.string.questions_required), style = EasyPaperTheme.typography.footnote,
            color = EasyPaperTheme.colors.secondaryLabel, modifier = Modifier.padding(start = spacing.m, end = spacing.m, top = spacing.l))
    }
    Problems(problems.map { it.message() })
    SaveButton(saving, enabled = complete) {
        onSave(Answers(answers.filterValues { it.isNotBlank() }.mapValues { it.value.trim() }))
    }
}

@Composable
private fun ColumnScope.TextForm(
    placeholder: StringResource,
    problems: List<EntryProblem>,
    saving: Boolean,
    withPhoto: Boolean = false,
    onSave: (text: String, photo: ByteArray?) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    GroupedSection {
        TextAreaRow(text, { text = it }, placeholder = stringResource(placeholder))
    }
    if (withPhoto) PhotoPicker(photo, onPhoto = { photo = it })
    Problems(problems.map { it.message() })
    SaveButton(saving, enabled = text.isNotBlank() || photo != null) { onSave(text.trim(), photo) }
}

/**
 * "Take photo" / "Choose photo" where the platform has them (task 28: already shrunk, no metadata), a preview of
 * the chosen photo and "Remove photo". A photo is always optional.
 */
@Composable
private fun PhotoPicker(photo: ByteArray?, onPhoto: (ByteArray?) -> Unit) {
    val spacing = EasyPaperTheme.spacing
    val source = rememberPhotoSource { onPhoto(it) }
    if (source.takePhoto == null && source.pickPhoto == null) return
    Column(Modifier.padding(top = spacing.s), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        val preview = remember(photo) { photo?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
        if (preview != null) {
            Image(
                preview,
                contentDescription = stringResource(Res.string.photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(spacing.xxl * 4, spacing.xxl * 3).clip(EasyPaperTheme.shapes.small),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            if (photo != null) {
                GrayButton(stringResource(Res.string.remove_photo), onClick = { onPhoto(null) })
            } else {
                source.takePhoto?.let { GrayButton(stringResource(Res.string.take_photo), onClick = it) }
                source.pickPhoto?.let { GrayButton(stringResource(Res.string.choose_photo), onClick = it) }
            }
        }
        Text(stringResource(Res.string.photo_optional), style = EasyPaperTheme.typography.footnote, color = EasyPaperTheme.colors.secondaryLabel)
    }
}

/**
 * Own measurements (task 61): the weight in kg, or anything else the person measures with its own name and unit
 * ("Waist 84 cm"). The value rules are the core's check (#52); here only "is it a number".
 */
@Composable
private fun ColumnScope.MeasurementsForm(problems: List<EntryProblem>, saving: Boolean, onSave: (EntryPayload) -> Unit) {
    var mode by remember { mutableStateOf(0) }
    var weight by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    val weightLabel = stringResource(Res.string.m_weight)
    val spacing = EasyPaperTheme.spacing
    SegmentedControl(listOf(weightLabel, stringResource(Res.string.m_other)), mode, { mode = it; missing = false },
        role = Role.RadioButton)
    Spacer(Modifier.height(spacing.s))
    GroupedSection {
        if (mode == 0) {
            NumberFieldRow(weightLabel, weight, { weight = it; missing = false }, unit = stringResource(Res.string.m_kg),
                placeholder = "72,5", decimal = true, maxLength = 5)
        } else {
            val nameLabel = stringResource(Res.string.m_name)
            val unitLabel = stringResource(Res.string.m_unit)
            TextFieldRow(name, { name = it; missing = false }, placeholder = stringResource(Res.string.m_name_example), description = nameLabel)
            RowSeparator(spacing.m)
            NumberFieldRow(stringResource(Res.string.m_value), value, { value = it; missing = false }, decimal = true, maxLength = 8)
            RowSeparator(spacing.m)
            TextFieldRow(unit, { unit = it; missing = false }, placeholder = stringResource(Res.string.m_unit_example), description = unitLabel)
        }
    }
    Problems(if (missing) listOf(Res.string.m_missing) else problems.map { it.message() })
    SaveButton(saving) {
        val payload = if (mode == 0) parseDecimal(weight)?.let { Measurement.weight(it) }
            else parseDecimal(value)?.let { Measurement(name.trim(), it, unit.trim()) }
        if (payload == null) missing = true else onSave(payload)
    }
}

/** "72,5" or "72.5" -> 72.5; null for an empty or broken number. */
internal fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

@Composable
private fun ColumnScope.SaveButton(saving: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Spacer(Modifier.height(EasyPaperTheme.spacing.xl))
    ProminentButton(stringResource(Res.string.save), onClick = onClick, enabled = enabled && !saving, modifier = Modifier.fillMaxWidth())
}

/** Input problems as sentences with an icon, announced to screen readers when they appear. */
@Composable
private fun Problems(messages: List<StringResource>) {
    if (messages.isEmpty()) return
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    Column(Modifier.padding(start = spacing.m, end = spacing.m, top = spacing.s).semantics { liveRegion = LiveRegionMode.Polite }) {
        for (message in messages.distinct()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(painterResource(Res.drawable.ic_remove), contentDescription = null, tint = colors.destructive,
                    modifier = Modifier.size(spacing.rowIcon).padding(end = spacing.xxs))
                Text(stringResource(message), style = EasyPaperTheme.typography.subheadline, color = colors.destructive)
            }
        }
    }
}

private fun EntryProblem.message(): StringResource = when (this) {
    EntryProblem.SYSTOLIC_OUT_OF_RANGE -> Res.string.problem_systolic
    EntryProblem.DIASTOLIC_OUT_OF_RANGE -> Res.string.problem_diastolic
    EntryProblem.PULSE_OUT_OF_RANGE -> Res.string.problem_pulse
    EntryProblem.DIASTOLIC_NOT_BELOW_SYSTOLIC -> Res.string.problem_order
    EntryProblem.WATER_OUT_OF_RANGE -> Res.string.problem_water
    EntryProblem.EMPTY_TEXT, EntryProblem.FOOD_EMPTY, EntryProblem.EMPTY_MEDICATION_NAME, EntryProblem.EMPTY_MEASUREMENT_NAME -> Res.string.problem_empty
    EntryProblem.MEASUREMENT_OUT_OF_RANGE -> Res.string.problem_measurement
    EntryProblem.UNKNOWN_WIDGET, EntryProblem.WRONG_WIDGET_TYPE, EntryProblem.UNKNOWN_QUESTION, EntryProblem.BAD_ANSWER -> Res.string.problem_other
}
