package org.easypaper.ui.patient

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.cancel
import easypaper.app.shared.generated.resources.delete
import easypaper.app.shared.generated.resources.delete_entry_question
import easypaper.app.shared.generated.resources.entries
import easypaper.app.shared.generated.resources.history_empty_message
import easypaper.app.shared.generated.resources.history_empty_title
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.model.Entry
import org.easypaper.model.PatientRecord
import org.easypaper.ui.components.BackAction
import org.easypaper.ui.components.CategoryRow
import org.easypaper.ui.components.EmptyState
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.LargeTitleScaffold
import org.easypaper.ui.components.ListRow
import org.easypaper.ui.components.PlainButton
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.components.category
import org.easypaper.ui.format.longDate
import org.easypaper.ui.theme.EasyPaperTheme
import org.easypaper.ui.theme.tabular
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Days with entries, newest first. */
fun daysWithEntries(record: PatientRecord, timeZone: TimeZone): List<Pair<LocalDate, List<Entry>>> =
    record.entries.groupBy { it.localDate(timeZone) }.toList().sortedByDescending { it.first }

/** "History": every day with entries; a day opens its entries. */
@Composable
fun HistoryScreen(record: PatientRecord?, timeZone: TimeZone, title: String, onDeveloper: () -> Unit, onOpenDay: (LocalDate) -> Unit) {
    LargeTitleScaffold(title = title, onTitleLongPress = onDeveloper, bottomInset = false) {
        val days = record?.let { daysWithEntries(it, timeZone) }.orEmpty()
        if (days.isEmpty()) {
            item { EmptyState(stringResource(Res.string.history_empty_title), stringResource(Res.string.history_empty_message)) }
        } else {
            item {
                GroupedSection {
                    days.forEachIndexed { index, (date, entries) ->
                        if (index > 0) RowSeparator(EasyPaperTheme.spacing.m)
                        ListRow(
                            longDate(date),
                            subtitle = pluralStringResource(Res.plurals.entries, entries.size, entries.size),
                            onClick = { onOpenDay(date) },
                        )
                    }
                }
            }
        }
    }
}

/** One day: its entries by time, in the doctor's widget order; each can be deleted after a confirmation. */
@Composable
fun DayScreen(record: PatientRecord, date: LocalDate, timeZone: TimeZone, back: BackAction, onDelete: (String) -> Unit) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val entries = record.entries.filter { it.localDate(timeZone) == date }.groupBy { it.widgetId }
    LargeTitleScaffold(title = longDate(date), back = back, bottomInset = false) {
        for (widget in record.request.widgets) {
            val list = entries[widget.id]?.sortedBy { it.at } ?: continue
            item(key = widget.id) {
                GroupedSection(Modifier.padding(bottom = EasyPaperTheme.spacing.sectionGap)) {
                    CategoryRow(widget.category, widget.title)
                    for (entry in list) {
                        RowSeparator()
                        EntryRow(entry, record, timeZone, pendingDelete == entry.id,
                            onAskDelete = { pendingDelete = entry.id },
                            onCancel = { pendingDelete = null },
                            onConfirm = { pendingDelete = null; onDelete(entry.id) })
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            item { EmptyState(stringResource(Res.string.history_empty_title), stringResource(Res.string.history_empty_message)) }
        }
    }
}

@Composable
private fun EntryRow(
    entry: Entry,
    record: PatientRecord,
    timeZone: TimeZone,
    confirming: Boolean,
    onAskDelete: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    // Rows line up with the text of the category row above (tile + gap).
    Column(Modifier.fillMaxWidth().padding(start = spacing.m + spacing.tile + spacing.s, end = spacing.m, top = spacing.xs, bottom = spacing.xs)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text(timeOfDay(entry.at, timeZone), style = type.footnote.tabular(), color = colors.secondaryLabel,
                modifier = Modifier.padding(top = spacing.xxs / 2))
            Text(entryText(entry, record.request), style = type.body, color = colors.label, modifier = Modifier.weight(1f))
            if (!confirming) PlainButton(stringResource(Res.string.delete), onClick = onAskDelete)
        }
        if (confirming) {
            Text(stringResource(Res.string.delete_entry_question), style = type.subheadline, color = colors.label,
                modifier = Modifier.padding(top = spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
                DestructiveButton(stringResource(Res.string.delete), onConfirm)
                PlainButton(stringResource(Res.string.cancel), onClick = onCancel)
            }
        }
    }
}

/** A plain text button in the destructive color (HIG: delete actions are red, with the word "Delete"). */
@Composable
private fun DestructiveButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.minimumInteractiveComponentSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = onClick,
        ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = EasyPaperTheme.typography.body, color = EasyPaperTheme.colors.destructive)
    }
}
