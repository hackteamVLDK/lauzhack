package org.easypaper.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.ic_add
import easypaper.app.shared.generated.resources.ic_check_circle
import easypaper.app.shared.generated.resources.ic_remove
import org.easypaper.ui.theme.EasyPaperTheme
import org.easypaper.ui.theme.tabular
import org.jetbrains.compose.resources.painterResource
import kotlin.math.round

/**
 * iOS form row: label on the start, the value typed on the end (numbers right-aligned, tabular).
 * Use inside a [GroupedSection]; [unit] sits after the field ("mmHg").
 */
@Composable
fun NumberFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    placeholder: String = "",
) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    Row(
        modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight).padding(horizontal = spacing.m, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = type.body, color = colors.label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(spacing.s))
        Box(Modifier.widthIn(min = spacing.xxl * 3), contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty()) Text(placeholder, style = type.body.tabular(), color = colors.secondaryLabel)
            BasicTextField(
                value = value,
                onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(3)) },
                singleLine = true,
                textStyle = type.body.tabular().copy(color = colors.label, textAlign = TextAlign.End),
                cursorBrush = SolidColor(colors.tint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.widthIn(min = spacing.xxl * 3).semantics { contentDescription = label },
            )
        }
        if (unit != null) {
            Spacer(Modifier.width(spacing.xs))
            Text(unit, style = type.subheadline, color = colors.secondaryLabel)
        }
    }
}

/** One line of text in a grouped cell (a name, a short answer); [placeholder] says what to type. */
@Composable
fun TextFieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /** A secret (access code): shown as dots, no autocorrect. */
    secret: Boolean = false,
) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    Box(modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight).padding(horizontal = spacing.m, vertical = spacing.s),
        contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(placeholder, style = type.body, color = colors.secondaryLabel)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = type.body.copy(color = colors.label),
            cursorBrush = SolidColor(colors.tint),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        )
    }
}

/**
 * A value changed with − and + (iOS stepper), e.g. "7 days". The buttons are named for screen readers by
 * [lessLabel] / [moreLabel] and turn off at the ends.
 */
@Composable
fun StepperRow(
    label: String,
    value: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
    lessLabel: String,
    moreLabel: String,
    modifier: Modifier = Modifier,
    canLess: Boolean = true,
    canMore: Boolean = true,
) {
    val spacing = EasyPaperTheme.spacing
    val type = EasyPaperTheme.typography
    Row(
        modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight).padding(horizontal = spacing.m, vertical = spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = type.body, color = EasyPaperTheme.colors.label)
            Text(value, style = type.subheadline.tabular(), color = EasyPaperTheme.colors.secondaryLabel)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            GrayButton("", onClick = onLess, icon = Res.drawable.ic_remove, enabled = canLess,
                modifier = Modifier.semantics { contentDescription = lessLabel })
            GrayButton("", onClick = onMore, icon = Res.drawable.ic_add, enabled = canMore,
                modifier = Modifier.semantics { contentDescription = moreLabel })
        }
    }
}

/** Multi-line text in a grouped cell, for the patient's own words. */
@Composable
fun TextAreaRow(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    // The text field is the whole cell (padding and placeholder live in its decoration), so a tap anywhere in the
    // cell, also below a short text, puts the cursor there.
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = type.body.copy(color = colors.label),
        cursorBrush = SolidColor(colors.tint),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight * 2).semantics { contentDescription = placeholder },
        decorationBox = { field ->
            Box(Modifier.fillMaxWidth().padding(spacing.m)) {
                if (value.isEmpty()) Text(placeholder, style = type.body, color = colors.secondaryLabel)
                field()
            }
        },
    )
}

/** Compatibility for form questions that can still have no answer. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) =
    V2SegmentedControl(options, selected, onSelect, modifier)

/** A list of options in a grouped section with a check on the chosen one (iOS picker in a form). */
@Composable
fun ChoiceList(options: List<String>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    GroupedSection(modifier) {
        Column(Modifier.selectableGroup()) {
            options.forEachIndexed { index, option ->
                if (index > 0) RowSeparator(spacing.m)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight)
                        .selectable(
                            selected = option == selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        )
                        .padding(horizontal = spacing.m, vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(option, style = EasyPaperTheme.typography.body, color = colors.label, modifier = Modifier.weight(1f))
                    if (option == selected) {
                        // The selected state is announced by the radio role; the check is decoration.
                        Icon(painterResource(Res.drawable.ic_check_circle), contentDescription = null, tint = colors.tint,
                            modifier = Modifier.size(spacing.rowIcon))
                    }
                }
            }
        }
    }
}

/**
 * A 0–10 scale: the chosen number big (Title 1, tabular) over a slider in tint, the doctor's words
 * for both ends under it. [value] null = not answered yet.
 */
@Composable
fun ScaleInput(value: Int?, onValueChange: (Int) -> Unit, minLabel: String, maxLabel: String, modifier: Modifier = Modifier) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    GroupedSection(modifier) {
        Column(Modifier.fillMaxWidth().padding(spacing.m)) {
            Text(
                value?.toString() ?: "–",
                style = type.title1.tabular(),
                color = if (value == null) colors.secondaryLabel else colors.label,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Slider(
                value = (value ?: SCALE_MAX / 2).toFloat(),
                onValueChange = { onValueChange(round(it).toInt()) },
                valueRange = 0f..SCALE_MAX.toFloat(),
                steps = SCALE_MAX - 1,
                colors = SliderDefaults.colors(
                    thumbColor = colors.cellRaised,
                    activeTrackColor = colors.tint,
                    inactiveTrackColor = colors.fill,
                    activeTickColor = colors.tint,
                    inactiveTickColor = colors.separator,
                ),
                modifier = Modifier.semantics { contentDescription = "$minLabel – $maxLabel" },
            )
            Row(Modifier.fillMaxWidth()) {
                Text("0  $minLabel", style = type.footnote, color = colors.secondaryLabel, modifier = Modifier.weight(1f))
                Text("$maxLabel  $SCALE_MAX", style = type.footnote, color = colors.secondaryLabel, textAlign = TextAlign.End)
            }
        }
    }
}

private const val SCALE_MAX = 10
