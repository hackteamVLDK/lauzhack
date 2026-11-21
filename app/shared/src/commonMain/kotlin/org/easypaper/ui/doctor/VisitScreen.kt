package org.easypaper.ui.doctor

import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.easypaper.report.jpegRotationDegrees
import org.easypaper.model.Food
import org.easypaper.model.DiaryNote
import easypaper.app.shared.generated.resources.entry_photo
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import easypaper.app.shared.generated.resources.ai_failed
import easypaper.app.shared.generated.resources.ai_not_used
import easypaper.app.shared.generated.resources.ai_write
import easypaper.app.shared.generated.resources.ai_writing
import easypaper.app.shared.generated.resources.chart_title
import org.easypaper.ai.SummaryResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.cancel
import easypaper.app.shared.generated.resources.clinic_form
import easypaper.app.shared.generated.resources.confirm
import easypaper.app.shared.generated.resources.confirm_summary
import easypaper.app.shared.generated.resources.confirmed_at
import easypaper.app.shared.generated.resources.date_short
import easypaper.app.shared.generated.resources.done
import easypaper.app.shared.generated.resources.draft
import easypaper.app.shared.generated.resources.edit
import easypaper.app.shared.generated.resources.edited_by_you
import easypaper.app.shared.generated.resources.entries
import easypaper.app.shared.generated.resources.facts_without_ai
import easypaper.app.shared.generated.resources.field_bp
import easypaper.app.shared.generated.resources.field_complaint
import easypaper.app.shared.generated.resources.field_fluid
import easypaper.app.shared.generated.resources.field_history
import easypaper.app.shared.generated.resources.field_meds
import easypaper.app.shared.generated.resources.field_questions
import easypaper.app.shared.generated.resources.fields_decided
import easypaper.app.shared.generated.resources.months_short
import easypaper.app.shared.generated.resources.more_sources
import easypaper.app.shared.generated.resources.ic_check_circle
import easypaper.app.shared.generated.resources.ic_remove
import easypaper.app.shared.generated.resources.no_source
import easypaper.app.shared.generated.resources.original_entry
import easypaper.app.shared.generated.resources.remove
import easypaper.app.shared.generated.resources.removed
import easypaper.app.shared.generated.resources.save
import easypaper.app.shared.generated.resources.save_hint
import easypaper.app.shared.generated.resources.save_record
import easypaper.app.shared.generated.resources.saved_at
import easypaper.app.shared.generated.resources.section_entries
import easypaper.app.shared.generated.resources.summary
import easypaper.app.shared.generated.resources.undo
import easypaper.app.shared.generated.resources.weekdays_short
import easypaper.app.shared.generated.resources.written_from
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.model.Entry
import org.easypaper.report.Report
import org.easypaper.ui.components.BackAction
import org.easypaper.ui.components.CategoryRow
import org.easypaper.ui.components.DraftBadge
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.LargeTitleScaffold
import org.easypaper.ui.components.PlainButton
import org.easypaper.ui.components.ProminentButton
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.components.Sheet
import org.easypaper.ui.components.TextAreaRow
import org.easypaper.ui.components.TintedButton
import org.easypaper.ui.components.category
import org.easypaper.ui.format.currentUtcOffsetMinutes
import org.easypaper.ui.format.dateRange
import org.easypaper.ui.format.lastDay
import org.easypaper.ui.format.longDate
import org.easypaper.ui.patient.entryText
import org.easypaper.ui.patient.localDate
import org.easypaper.ui.patient.timeOfDay
import org.easypaper.ui.theme.EasyPaperTheme
import org.easypaper.ui.theme.tabular
import org.easypaper.visit.FieldState
import org.easypaper.visit.FormField
import org.easypaper.visit.FormFieldKey
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Links under a summary line before "N more" (docs/design.md wireframe). */
private const val LINKS_SHOWN = 4

@OptIn(ExperimentalTime::class)
private fun now(): Long = Clock.System.now().toEpochMilliseconds()

/** The AI summary of this report from the server: the result, whether it is being written, whether the last try failed. */
class VisitAi(val result: SummaryResult?, val busy: Boolean, val failed: Boolean, val onWrite: () -> Unit)

/**
 * The doctor's visit screen (docs/design.md wireframe): the summary with links to the originals, the
 * clinic form confirmed field by field, and every entry. Nothing is saved until each field is decided.
 */
@Composable
fun VisitScreen(
    report: Report,
    back: BackAction?,
    ai: VisitAi? = null,
    photos: Map<String, ByteArray> = emptyMap(),
    /** Keeps the doctor's decisions per report while they look at other screens (ShellState.visits). */
    store: MutableMap<String, VisitState>? = null,
) {
    val wording = rememberFormWording()
    val offset = remember { currentUtcOffsetMinutes() }
    val state = remember(report.id, wording) {
        val kept = store?.get(report.id)
        // Keep what the doctor decided; rebuild only an untouched state whose texts were made before the language loaded.
        if (kept != null && (kept.wording == wording || kept.decided > 0 || kept.saved != null)) kept
        else VisitState(report, wording, offset).also { store?.put(report.id, it) }
    }
    val timeZone = remember { TimeZone.currentSystemDefault() }
    var original by remember { mutableStateOf<Entry?>(null) }
    var editing by remember { mutableStateOf<FormFieldKey?>(null) }
    val request = report.record.request
    val aiResult = ai?.result
    LaunchedEffect(aiResult) { aiResult?.let(state::applyAi) }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(
            title = request.complaint,
            back = back,
            header = { VisitHeader(report) },
        ) {
            item(key = "summary") { SummarySection(state, ai, timeZone, onOpen = { original = it }, onEdit = { editing = FormFieldKey.HISTORY }) }
            pressureSeries(report.record.entries)?.let { series ->
                item(key = "chart") {
                    SectionTitle(stringResource(Res.string.chart_title))
                    PressureChart(series)
                }
            }
            item(key = "form") { ClinicFormSection(state, onEdit = { editing = it }) }
            item(key = "entries") { EntriesSection(report, timeZone, onOpen = { original = it }) }
        }
        OriginalSheet(report, original, timeZone, photos, onDismiss = { original = null })
        EditSheet(state, editing, onDismiss = { editing = null })
    }
}

@Composable
private fun VisitHeader(report: Report) {
    val request = report.record.request
    val start = runCatching { LocalDate.parse(request.startDate) }.getOrNull()
    val count = report.record.entries.size
    val line = listOfNotNull(
        start?.let { dateRange(it, lastDay(it, request.days)) },
        pluralStringResource(Res.plurals.entries, count, count),
    ).joinToString(", ")
    Text(line, style = EasyPaperTheme.typography.subheadline, color = EasyPaperTheme.colors.secondaryLabel)
}

@Composable
private fun SectionTitle(text: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val spacing = EasyPaperTheme.spacing
    Row(
        Modifier.fillMaxWidth().padding(start = spacing.xxs, end = spacing.xxs, top = spacing.sectionGap, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(text, style = EasyPaperTheme.typography.title2, color = EasyPaperTheme.colors.label, modifier = Modifier.semantics { heading() })
        trailing?.invoke(this)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummarySection(state: VisitState, ai: VisitAi?, timeZone: TimeZone, onOpen: (Entry) -> Unit, onEdit: () -> Unit) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val summary = state.summary
    val history = state.form.field(FormFieldKey.HISTORY)
    val weekdays = stringArrayResource(Res.array.weekdays_short)
    SectionTitle(stringResource(Res.string.summary)) {
        DraftBadge(stringResource(if (summary.source == SummarySource.Facts) Res.string.facts_without_ai else Res.string.draft))
        val count = state.report.record.entries.size
        Text(stringResource(Res.string.written_from, count.toString()), style = EasyPaperTheme.typography.footnote, color = colors.secondaryLabel)
    }
    // What the doctor confirms is what they see: once edited, the history text replaces the summary lines.
    if (history.edited) {
        GroupedSection {
            Column(Modifier.fillMaxWidth().padding(spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(history.value, style = EasyPaperTheme.typography.callout, color = colors.label)
                Text(stringResource(Res.string.edited_by_you), style = EasyPaperTheme.typography.footnote, color = colors.secondaryLabel)
            }
        }
    } else GroupedSection {
        summary.lines.forEachIndexed { index, line ->
            if (index > 0) RowSeparator(spacing.m)
            Column(Modifier.fillMaxWidth().padding(horizontal = spacing.m, vertical = spacing.s)) {
                Text(line.text, style = EasyPaperTheme.typography.callout, color = colors.label)
                if (line.supported) {
                    var expanded by remember(line) { mutableStateOf(false) }
                    val shown = if (expanded) line.sources else line.sources.take(LINKS_SHOWN)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                        for (entry in shown) SourceLink(entry, weekdays, timeZone, onOpen)
                        val more = line.sources.size - shown.size
                        if (more > 0) {
                            Box(
                                Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = { expanded = true }),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(stringResource(Res.string.more_sources, more), style = EasyPaperTheme.typography.footnote,
                                    color = colors.link)
                            }
                        }
                    }
                } else {
                    NoSource()
                }
            }
        }
    }
    Spacer(Modifier.height(spacing.s))
    DecisionRow(history, state, primary = Res.string.confirm_summary, onEdit = onEdit)
    if (ai != null) AiRow(ai)
}

/** "Write summary with AI": progress in the button while the server works; what happened when it did not. */
@Composable
private fun AiRow(ai: VisitAi) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    Column(Modifier.padding(top = spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            TintedButton(
                stringResource(if (ai.busy) Res.string.ai_writing else Res.string.ai_write),
                onClick = ai.onWrite,
                enabled = !ai.busy,
            )
            if (ai.busy) CircularProgressIndicator(Modifier.size(spacing.rowIcon), color = colors.tint, strokeWidth = spacing.xxs / 2)
        }
        val message = when {
            ai.failed -> Res.string.ai_failed
            ai.result != null && !ai.result.usedAi -> Res.string.ai_not_used
            else -> null
        }
        if (message != null) {
            Text(stringResource(message), style = EasyPaperTheme.typography.footnote,
                color = if (ai.failed) colors.destructive else colors.secondaryLabel)
        }
    }
}

/** "Mon 08:10": opens the original entry. */
@Composable
private fun SourceLink(entry: Entry, weekdays: List<String>, timeZone: TimeZone, onOpen: (Entry) -> Unit) {
    val label = (weekdays.getOrNull(entry.localDate(timeZone).dayOfWeek.ordinal) ?: "") + " " + timeOfDay(entry.at, timeZone)
    Box(
        Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = { onOpen(entry) }),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = EasyPaperTheme.typography.footnote.tabular(), color = EasyPaperTheme.colors.link)
    }
}

/** An AI statement without an existing entry behind it: said in words, not only by color. */
@Composable
private fun NoSource() {
    val colors = EasyPaperTheme.colors
    Row(Modifier.padding(top = EasyPaperTheme.spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(Res.drawable.ic_remove), contentDescription = null, tint = colors.destructive,
            modifier = Modifier.size(EasyPaperTheme.spacing.rowIcon))
        Text(stringResource(Res.string.no_source), style = EasyPaperTheme.typography.footnote, color = colors.destructive)
    }
}

/** Confirm / Edit / Remove for a field still to decide; the decision with Undo once made. */
@Composable
private fun DecisionRow(field: FormField, state: VisitState, primary: StringResource = Res.string.confirm, onEdit: () -> Unit) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val timeZone = remember { TimeZone.currentSystemDefault() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
        when (field.state) {
            FieldState.DRAFT -> {
                TintedButton(stringResource(primary), onClick = { state.confirm(field.key, now()) }, enabled = field.value.isNotBlank())
                PlainButton(stringResource(Res.string.edit), onClick = onEdit)
                PlainButton(stringResource(Res.string.remove), onClick = { state.remove(field.key) })
            }
            FieldState.CONFIRMED -> {
                Icon(painterResource(Res.drawable.ic_check_circle), contentDescription = null, tint = colors.confirmed,
                    modifier = Modifier.size(spacing.rowIcon))
                val at = state.confirmedAt[field.key]?.let { timeOfDay(it, timeZone) } ?: ""
                Text(stringResource(Res.string.confirmed_at, at).trim(), style = EasyPaperTheme.typography.subheadline, color = colors.confirmed)
                PlainButton(stringResource(Res.string.undo), onClick = { state.reopen(field.key) })
            }
            FieldState.REJECTED -> {
                Text(stringResource(Res.string.removed), style = EasyPaperTheme.typography.subheadline, color = colors.secondaryLabel)
                PlainButton(stringResource(Res.string.undo), onClick = { state.reopen(field.key) })
            }
        }
    }
}

private val FormFieldKey.label: StringResource
    get() = when (this) {
        FormFieldKey.COMPLAINT -> Res.string.field_complaint
        FormFieldKey.HISTORY -> Res.string.field_history
        FormFieldKey.BLOOD_PRESSURE -> Res.string.field_bp
        FormFieldKey.MEDICATIONS -> Res.string.field_meds
        FormFieldKey.FLUID_INTAKE -> Res.string.field_fluid
        FormFieldKey.PATIENT_QUESTIONS -> Res.string.field_questions
    }

@Composable
private fun ClinicFormSection(state: VisitState, onEdit: (FormFieldKey) -> Unit) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val form = state.form
    SectionTitle(stringResource(Res.string.clinic_form)) {
        Spacer(Modifier.weight(1f))
        Text(stringResource(Res.string.fields_decided, state.decided, form.fields.size),
            style = EasyPaperTheme.typography.footnote.tabular(), color = colors.secondaryLabel)
    }
    GroupedSection(footer = if (form.canSave) null else stringResource(Res.string.save_hint)) {
        form.fields.forEachIndexed { index, field ->
            if (index > 0) RowSeparator(spacing.m)
            FieldRow(field, state, onEdit = { onEdit(field.key) })
        }
    }
    Spacer(Modifier.height(spacing.l))
    val saved = state.saved
    if (saved != null) {
        val timeZone = remember { TimeZone.currentSystemDefault() }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Icon(painterResource(Res.drawable.ic_check_circle), contentDescription = null, tint = colors.confirmed,
                modifier = Modifier.size(spacing.rowIcon))
            Text(stringResource(Res.string.saved_at, timeOfDay(saved.savedAt, timeZone)), style = EasyPaperTheme.typography.headline,
                color = colors.confirmed)
        }
    } else {
        ProminentButton(stringResource(Res.string.save_record), onClick = { state.save(now()) }, enabled = form.canSave,
            modifier = Modifier.fillMaxWidth())
    }
}

/** Label on the start, value and decision on the end (macOS Settings); stacked when narrow. */
@Composable
private fun FieldRow(field: FormField, state: VisitState, onEdit: () -> Unit) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val type = EasyPaperTheme.typography
    val background = if (field.state == FieldState.DRAFT) Modifier.background(colors.review) else Modifier
    BoxWithConstraints(Modifier.fillMaxWidth().then(background).padding(horizontal = spacing.m, vertical = spacing.s)) {
        val wide = maxWidth > spacing.contentMaxWidth * 2 / 3
        val label = @Composable {
            Text(stringResource(field.key.label), style = type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.label,
                modifier = if (wide) Modifier.width(spacing.sidebarWidth * 2 / 3) else Modifier)
        }
        val value = @Composable {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                val struck = field.state == FieldState.REJECTED
                Text(field.value, style = type.body, color = if (struck) colors.secondaryLabel else colors.label)
                DecisionRow(field, state, onEdit = onEdit)
            }
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) { label(); value() }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) { label(); value() }
        }
    }
}

@Composable
private fun EntriesSection(report: Report, timeZone: TimeZone, onOpen: (Entry) -> Unit) {
    val spacing = EasyPaperTheme.spacing
    val record = report.record
    val byWidget = record.entries.sortedBy { it.at }.groupBy { it.widgetId }
    val dateShort = stringResource(Res.string.date_short)
    val monthsShort = stringArrayResource(Res.array.months_short)
    SectionTitle(stringResource(Res.string.section_entries))
    for (widget in record.request.widgets) {
        val entries = byWidget[widget.id] ?: continue
        GroupedSection(Modifier.padding(bottom = spacing.s)) {
            CategoryRow(widget.category, widget.title, trailing = {
                Text(pluralStringResource(Res.plurals.entries, entries.size, entries.size),
                    style = EasyPaperTheme.typography.footnote, color = EasyPaperTheme.colors.secondaryLabel)
            })
            for (entry in entries) {
                RowSeparator()
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { onOpen(entry) })
                        .padding(start = spacing.m + spacing.tile + spacing.s, end = spacing.m, top = spacing.xs, bottom = spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                ) {
                    val date = entry.localDate(timeZone)
                    Text(dateShort.fill(date.day, monthsShort[date.month.ordinal]) + " " + timeOfDay(entry.at, timeZone),
                        style = EasyPaperTheme.typography.footnote.tabular(), color = EasyPaperTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(top = spacing.xxs / 2))
                    Text(entryText(entry, record.request), style = EasyPaperTheme.typography.callout, color = EasyPaperTheme.colors.label)
                }
            }
        }
    }
}

@Composable
private fun OriginalSheet(report: Report, entry: Entry?, timeZone: TimeZone, photos: Map<String, ByteArray>, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf<Entry?>(null) }
    if (entry != null) shown = entry
    Sheet(visible = entry != null, title = stringResource(Res.string.original_entry), doneLabel = stringResource(Res.string.done), onDismiss = onDismiss) {
        val e = shown ?: return@Sheet
        val widget = report.record.request.widgets.firstOrNull { it.id == e.widgetId } ?: return@Sheet
        GroupedSection {
            CategoryRow(widget.category, widget.title, trailing = {
                Text(timeOfDay(e.at, timeZone), style = EasyPaperTheme.typography.footnote.tabular(), color = EasyPaperTheme.colors.secondaryLabel)
            }) {
                Text(entryText(e, report.record.request), style = EasyPaperTheme.typography.body, color = EasyPaperTheme.colors.label)
                Text(longDate(e.localDate(timeZone)), style = EasyPaperTheme.typography.footnote, color = EasyPaperTheme.colors.secondaryLabel)
            }
        }
        // the patient's photo of this entry, as received with the report (task 36)
        val ref = when (val p = e.payload) { is Food -> p.photoRef; is DiaryNote -> p.photoRef; else -> null }
        ref?.let { photos[it] }?.let { EntryPhoto(it, Modifier.padding(top = EasyPaperTheme.spacing.l)) }
    }
}

/** A stored JPEG shown upright: photos keep the EXIF orientation tag instead of rotated pixels (shrinkPhoto). */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun EntryPhoto(jpeg: ByteArray, modifier: Modifier = Modifier) {
    val bitmap = remember(jpeg) { runCatching { jpeg.decodeToImageBitmap() }.getOrNull() } ?: return
    val turn = remember(jpeg) { if (decoderTurnsPhotosUpright) 0 else jpegRotationDegrees(jpeg) }
    val sideways = turn == 90 || turn == 270
    val ratio = if (sideways) bitmap.height.toFloat() / bitmap.width else bitmap.width.toFloat() / bitmap.height
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(ratio).clip(EasyPaperTheme.shapes.group), contentAlignment = Alignment.Center) {
        Image(
            bitmap, contentDescription = stringResource(Res.string.entry_photo), contentScale = ContentScale.FillBounds,
            modifier = Modifier.requiredSize(if (sideways) maxHeight else maxWidth, if (sideways) maxWidth else maxHeight)
                .graphicsLayer { rotationZ = turn.toFloat() },
        )
    }
}

@Composable
private fun EditSheet(state: VisitState, key: FormFieldKey?, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf<FormFieldKey?>(null) }
    if (key != null) shown = key
    val field = shown?.let { state.form.field(it) }
    Sheet(
        visible = key != null,
        title = shown?.let { stringResource(it.label) }.orEmpty(),
        doneLabel = stringResource(Res.string.cancel),
        onDismiss = onDismiss,
    ) {
        val current = field ?: return@Sheet
        var text by remember(key) { mutableStateOf(current.value) }
        GroupedSection { TextAreaRow(text, { text = it }, placeholder = stringResource(current.key.label)) }
        Spacer(Modifier.height(EasyPaperTheme.spacing.xl))
        ProminentButton(stringResource(Res.string.save), onClick = { state.edit(current.key, text.trim()); onDismiss() },
            enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth())
    }
}
