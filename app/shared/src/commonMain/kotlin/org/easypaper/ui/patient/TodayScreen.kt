package org.easypaper.ui.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.add_bp
import easypaper.app.shared.generated.resources.add_meal
import easypaper.app.shared.generated.resources.add_water
import easypaper.app.shared.generated.resources.answer_questions
import easypaper.app.shared.generated.resources.answered_at
import easypaper.app.shared.generated.resources.ask_question
import easypaper.app.shared.generated.resources.bp_progress
import easypaper.app.shared.generated.resources.decimal_separator
import easypaper.app.shared.generated.resources.footer_privacy
import easypaper.app.shared.generated.resources.ic_add
import easypaper.app.shared.generated.resources.ic_check_circle
import easypaper.app.shared.generated.resources.ic_remove
import easypaper.app.shared.generated.resources.nothing_today
import easypaper.app.shared.generated.resources.pulse
import easypaper.app.shared.generated.resources.today_empty_message
import easypaper.app.shared.generated.resources.today_empty_title
import easypaper.app.shared.generated.resources.undo
import easypaper.app.shared.generated.resources.unit_mmhg
import easypaper.app.shared.generated.resources.water_amount
import easypaper.app.shared.generated.resources.water_today
import easypaper.app.shared.generated.resources.write_feeling
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.model.DiaryWidget
import org.easypaper.model.Entry
import org.easypaper.model.FoodWidget
import org.easypaper.model.PatientRecord
import org.easypaper.ui.components.CategoryRow
import org.easypaper.ui.components.EmptyState
import org.easypaper.ui.components.GrayButton
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.LargeTitleScaffold
import org.easypaper.ui.components.PlainButton
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.components.TintedButton
import org.easypaper.ui.components.category
import org.easypaper.ui.format.today
import org.easypaper.ui.theme.EasyPaperTheme
import org.easypaper.ui.theme.tabular
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** What the patient can do from "Today". */
class TodayActions(
    val add: (widgetId: String) -> Unit,
    val addWater: (widgetId: String) -> Unit,
    val removeEntry: (entryId: String) -> Unit,
)

/**
 * "Today" (docs/design.md wireframe): blood pressure, water and the questionnaire each in their own
 * section with their action; food, medicines, how you feel and questions for the doctor together.
 */
@Composable
fun TodayScreen(
    record: PatientRecord?,
    today: LocalDate,
    timeZone: TimeZone,
    title: String,
    header: @Composable () -> Unit,
    onDeveloper: () -> Unit,
    actions: TodayActions,
) {
    LargeTitleScaffold(title = title, onTitleLongPress = onDeveloper, bottomInset = false, header = { header() }) {
        if (record == null) {
            item { EmptyState(stringResource(Res.string.today_empty_title), stringResource(Res.string.today_empty_message)) }
            return@LargeTitleScaffold
        }
        val widgets = todayFor(record, today, timeZone)
        val own = widgets.filter { it is WidgetToday.Pressure || it is WidgetToday.Drink || it is WidgetToday.Questions }
        val notes = widgets - own.toSet()
        for (widget in own) {
            item(key = widget.widget.id) {
                GroupedSection(Modifier.padding(bottom = EasyPaperTheme.spacing.sectionGap)) {
                    when (widget) {
                        is WidgetToday.Pressure -> PressureRow(widget, timeZone, actions)
                        is WidgetToday.Drink -> DrinkRow(widget, actions)
                        is WidgetToday.Questions -> QuestionsRow(widget, timeZone, actions)
                        else -> Unit
                    }
                }
            }
        }
        if (notes.isNotEmpty()) {
            item(key = "notes") {
                GroupedSection(footer = stringResource(Res.string.footer_privacy)) {
                    notes.forEachIndexed { index, widget ->
                        if (index > 0) RowSeparator()
                        when (widget) {
                            is WidgetToday.Notes -> NotesRow(widget, timeZone, actions)
                            is WidgetToday.Asked -> AskedRow(widget, actions)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PressureRow(today: WidgetToday.Pressure, timeZone: TimeZone, actions: TodayActions) {
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    val time = today.latestAt?.let { timeOfDay(it, timeZone) }
    CategoryRow(today.widget.category, today.widget.title, trailing = time?.let { { Footnote(it) } }) {
        today.latest?.let { bp ->
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(EasyPaperTheme.spacing.xs)) {
                Text("${bp.systolic}/${bp.diastolic}", style = type.title1.tabular(), color = colors.label)
                Text(stringResource(Res.string.unit_mmhg), style = type.subheadline, color = colors.secondaryLabel,
                    modifier = Modifier.padding(bottom = EasyPaperTheme.spacing.xxs))
                bp.pulse?.let {
                    Text(stringResource(Res.string.pulse, it.toString()), style = type.subheadline, color = colors.secondaryLabel,
                        modifier = Modifier.padding(bottom = EasyPaperTheme.spacing.xxs))
                }
            }
        }
        Footnote(stringResource(Res.string.bp_progress, today.readings.size, today.expected))
        Spacer(Modifier.height(EasyPaperTheme.spacing.xs))
        TintedButton(stringResource(Res.string.add_bp), onClick = { actions.add(today.widget.id) }, icon = Res.drawable.ic_add)
    }
}

@Composable
private fun DrinkRow(today: WidgetToday.Drink, actions: TodayActions) {
    val sep = stringResource(Res.string.decimal_separator).firstOrNull() ?: '.'
    val amount = litres(today.totalMl, sep)
    val label = today.targetMl?.let { stringResource(Res.string.water_amount, amount, litres(it, sep)) }
        ?: stringResource(Res.string.water_today, amount)
    CategoryRow(today.widget.category, today.widget.title) {
        Text(label, style = EasyPaperTheme.typography.title3.tabular(), color = EasyPaperTheme.colors.label)
        Spacer(Modifier.height(EasyPaperTheme.spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(EasyPaperTheme.spacing.xs)) {
            GrayButton(stringResource(Res.string.add_water), onClick = { actions.addWater(today.widget.id) }, icon = Res.drawable.ic_add)
            today.lastEntryId?.let { last ->
                GrayButton(stringResource(Res.string.undo), onClick = { actions.removeEntry(last) }, icon = Res.drawable.ic_remove)
            }
        }
    }
}

@Composable
private fun QuestionsRow(today: WidgetToday.Questions, timeZone: TimeZone, actions: TodayActions) {
    val colors = EasyPaperTheme.colors
    CategoryRow(today.widget.category, today.widget.title, onClick = { actions.add(today.widget.id) }) {
        val answered = today.answered
        if (answered != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EasyPaperTheme.spacing.xxs)) {
                // Text next to the check says it; the icon is decoration (color is never the only signal).
                Icon(painterResource(Res.drawable.ic_check_circle), contentDescription = null, tint = colors.confirmed,
                    modifier = Modifier.size(EasyPaperTheme.spacing.rowIcon))
                Text(stringResource(Res.string.answered_at, timeOfDay(answered.at, timeZone)),
                    style = EasyPaperTheme.typography.subheadline, color = colors.confirmed)
            }
        } else {
            Spacer(Modifier.height(EasyPaperTheme.spacing.xxs))
            TintedButton(stringResource(Res.string.answer_questions), onClick = { actions.add(today.widget.id) })
        }
    }
}

@Composable
private fun NotesRow(today: WidgetToday.Notes, timeZone: TimeZone, actions: TodayActions) {
    val action = when (today.widget) {
        is FoodWidget -> Res.string.add_meal
        is DiaryWidget -> Res.string.write_feeling
        else -> null // medicines are recorded on their own screen (task 31)
    }
    val last = today.entries.lastOrNull()
    CategoryRow(today.widget.category, today.widget.title, trailing = last?.let { { Footnote(timeOfDay(it.at, timeZone)) } }) {
        EntryLines(today.entries)
        if (action != null) PlainButton("+ " + stringResource(action), onClick = { actions.add(today.widget.id) })
    }
}

@Composable
private fun AskedRow(today: WidgetToday.Asked, actions: TodayActions) {
    CategoryRow(today.widget.category, today.widget.title) {
        EntryLines(today.entries, showNothing = false)
        PlainButton("+ " + stringResource(Res.string.ask_question), onClick = { actions.add(today.widget.id) })
    }
}

/** The patient's entries as plain lines (their own words, values with units). */
@Composable
private fun EntryLines(entries: List<Entry>, showNothing: Boolean = true) {
    val type = EasyPaperTheme.typography
    if (entries.isEmpty()) {
        if (showNothing) Text(stringResource(Res.string.nothing_today), style = type.body, color = EasyPaperTheme.colors.secondaryLabel)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(EasyPaperTheme.spacing.xxs)) {
        for (entry in entries) Text(entryText(entry, null), style = type.body, color = EasyPaperTheme.colors.label)
    }
}

@Composable
private fun Footnote(text: String) {
    Text(text, style = EasyPaperTheme.typography.footnote.tabular(), color = EasyPaperTheme.colors.secondaryLabel)
}

/** Keeps [today] stable for the whole composition; the date changes only when the screen is shown again. */
@Composable
fun rememberToday(): LocalDate = remember { today() }
