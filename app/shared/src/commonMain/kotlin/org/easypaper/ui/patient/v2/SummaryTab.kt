package org.easypaper.ui.patient.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import easypaper.app.shared.generated.resources.*
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.easypaper.diary.progress
import org.easypaper.model.BloodPressure
import org.easypaper.model.PatientRecord
import org.easypaper.model.Water
import org.easypaper.ui.components.*
import org.easypaper.ui.format.currentUtcOffsetMinutes
import org.easypaper.ui.format.dateRange
import org.easypaper.ui.patient.localDate
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
fun SummaryTab(records: List<PatientRecord>, settings: PatientSettings, today: LocalDate, timeZone: TimeZone,
               onProfile: () -> Unit, onDeveloper: () -> Unit, onPlan: (String) -> Unit,
               onCategory: (Category) -> Unit, onAdd: (Category) -> Unit, onGallery: () -> Unit) {
    val active = records.activeDoctor(today)
    val pinned = settings.pinnedCategories
    // Keep entrance state above lazy rows: scrolling and reordering must not replay the spring.
    val entrances = remember { pinned.associateWith { MutableTransitionState(true) }.toMutableMap() }
    LaunchedEffect(pinned) { entrances.keys.retainAll(pinned.toSet()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    // The last widget removed hides "Done": leave edit mode, so the next added widget opens normally.
    LaunchedEffect(pinned.isEmpty()) { if (pinned.isEmpty()) editing = false }
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    val hour by produceState(Clock.System.now().toLocalDateTime(timeZone).hour, timeZone) {
        while (true) {
            delay(60_000)
            value = Clock.System.now().toLocalDateTime(timeZone).hour
        }
    }
    val greeting = stringResource(when (hour) {
        in 5..11 -> Res.string.p2_good_morning
        in 12..17 -> Res.string.p2_good_afternoon
        else -> Res.string.p2_good_evening
    })
    Box(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars),
        contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = spacing.contentMaxWidth).fillMaxWidth(),
            contentPadding = PaddingValues(start = spacing.screenMargin, end = spacing.screenMargin,
                top = spacing.s, bottom = spacing.xxl), verticalArrangement = Arrangement.spacedBy(spacing.l)) {
            item(key = "greeting") {
                SummaryHeader(settings.name.value, greeting, onProfile, onDeveloper, onGallery)
            }
            item(key = "counts") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.xxl)) {
                    SummaryCount(stringResource(Res.string.p2_widget_count_value, pinned.size),
                        stringResource(Res.string.p2_widgets_label), Modifier.weight(1f))
                    SummaryCount(stringResource(Res.string.p2_widget_count_value,
                        records.sumOf { record -> record.entries.count { it.localDate(timeZone) == today } }),
                        stringResource(Res.string.p2_entries_today_label), Modifier.weight(1f))
                }
            }
            if (active != null) item(key = "plan") {
                PlanCard(active, today, onClick = { onPlan(active.request.id) })
            }
            if (pinned.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = spacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.xl)) {
                        Text(stringResource(Res.string.p2_empty_widgets), style = type.title1, color = colors.label,
                            textAlign = TextAlign.Center)
                        RoundIconButton(Res.drawable.ic_add, stringResource(Res.string.p2_add_widgets), onGallery, large = true)
                    }
                }
            } else {
                item(key = "edit") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.p2_your_widgets), style = type.title2, color = colors.label,
                            modifier = Modifier.weight(1f).semantics { heading() })
                        PlainButton(stringResource(if (editing) Res.string.p2_done else Res.string.p2_edit_widgets), { editing = !editing })
                    }
                }
                items(pinned.chunked(2), key = { row -> row.joinToString { it.name } }) { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                        row.forEach { category ->
                            key(category) {
                                SummaryWidget(category, records, today, timeZone, editing,
                                    onOpen = { onCategory(category) }, onAdd = { onAdd(category) }, onEdit = { editing = true },
                                    onRemove = { settings.removeWidget(category) },
                                    onPrevious = { settings.moveWidget(category, -1) }, onNext = { settings.moveWidget(category, 1) },
                                    canPrevious = pinned.first() != category, canNext = pinned.last() != category,
                                    entrance = entrances.getOrPut(category) { MutableTransitionState(false).apply { targetState = true } },
                                    modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item(key = "gallery-title") {
                    PlainButton(stringResource(Res.string.p2_add_widgets), onGallery)
                }
                item(key = "categories") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                        items(Category.entries, key = { it.name }) { category ->
                            PastelCategoryCard(category, { settings.addWidget(category) }, Modifier.width(spacing.categoryCardWidth),
                                added = category in pinned)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SummaryHeader(name: String?, greeting: String, onProfile: () -> Unit,
                          onDeveloper: () -> Unit, onGallery: () -> Unit) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (name.isNullOrBlank()) stringResource(Res.string.p2_hi)
                else stringResource(Res.string.p2_hi_name, name.trim()), style = EasyPaperTheme.typography.title3,
                color = colors.secondaryLabel, modifier = Modifier.weight(1f))
            AvatarButton(initials(name), onProfile)
            RoundIconButton(Res.drawable.ic_add, stringResource(Res.string.p2_add_widgets), onGallery)
        }
        Text(greeting, style = EasyPaperTheme.typography.largeTitle, color = colors.label,
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onDeveloper).semantics { heading() })
    }
}

@Composable
private fun SummaryCount(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        AnimatedValueText(value, style = EasyPaperTheme.typography.largeTitle)
        Text(label, style = EasyPaperTheme.typography.subheadline, color = EasyPaperTheme.colors.secondaryLabel)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SummaryWidget(category: Category, records: List<PatientRecord>, today: LocalDate, timeZone: TimeZone,
                          editing: Boolean, onOpen: () -> Unit, onAdd: () -> Unit, onEdit: () -> Unit,
                          onRemove: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
                          canPrevious: Boolean, canNext: Boolean, entrance: MutableTransitionState<Boolean>,
                          modifier: Modifier = Modifier) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    val motion = EasyPaperTheme.motion
    val foreground = category.softText(colors)
    val entries = categoryEntries(records, category)
    val latest = entries.firstOrNull()
    val pressure = latest?.entry?.payload as? BloodPressure
    val water = entries.filter { it.entry.localDate(timeZone) == today }.sumOf { (it.entry.payload as? Water)?.ml ?: 0 }
    AnimatedVisibility(entrance, modifier, enter = if (motion.reduceMotion) fadeIn(motion.fade())
        else fadeIn(motion.fade()) + scaleIn(motion.snappy.spec(), initialScale = 0.9f), exit = fadeOut(motion.fade())) {
        Column(Modifier.fillMaxSize().clip(EasyPaperTheme.shapes.widget).background(category.soft(colors))
            .combinedClickable(role = Role.Button, onClick = { if (!editing) onOpen() }, onLongClick = onEdit)
            .heightIn(min = spacing.widgetMinHeight).padding(spacing.s), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            if (editing) RoundIconButton(Res.drawable.ic_remove, stringResource(Res.string.p2_remove_widget, categoryTitle(category)),
                onRemove, Modifier.align(Alignment.End))
            Icon(painterResource(category.lineIcon), null, Modifier.size(spacing.xxl), tint = foreground)
            Text(categoryTitle(category), style = type.headline, color = foreground)
            when {
                category == Category.Water && latest != null -> {
                    AnimatedValueText(stringResource(Res.string.p2_widget_count_value, water), style = type.largeTitle, color = foreground)
                    Text(stringResource(Res.string.p2_widget_water_unit), style = type.subheadline, color = foreground)
                }
                pressure != null -> {
                    AnimatedValueText(stringResource(Res.string.p2_widget_pressure_value, pressure.systolic, pressure.diastolic),
                        style = type.largeTitle, color = foreground)
                    Text(stringResource(Res.string.p2_widget_pressure_unit), style = type.subheadline, color = foreground)
                }
                latest != null -> Text(patientEntryText(latest), style = type.body, color = foreground)
                else -> Text(stringResource(Res.string.p2_no_data), style = type.body, color = foreground)
            }
            Spacer(Modifier.weight(1f))
            if (editing) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    RoundIconButton(Res.drawable.ic_chevron_left, stringResource(Res.string.p2_move_widget_left, categoryTitle(category)),
                        onPrevious, enabled = canPrevious)
                    RoundIconButton(Res.drawable.ic_chevron_right, stringResource(Res.string.p2_move_widget_right, categoryTitle(category)),
                        onNext, enabled = canNext)
                }
            } else CapsuleButton(stringResource(if (category == Category.Water) Res.string.p2_water_quick else Res.string.p2_add_data), onAdd)
        }
    }
}

fun initials(name: String?): String = name.orEmpty().trim().split(' ').filter { it.isNotBlank() }
    .take(2).joinToString("") { it.take(1).uppercase() }

@Composable
fun PlanCard(record: PatientRecord, today: LocalDate, onClick: () -> Unit) {
    val days = record.progress(currentUtcOffsetMinutes())
    val widgets = days.flatMap { it.widgets }
    val expected = widgets.sumOf { it.expected }
    val done = widgets.sumOf { minOf(it.done, it.expected) }
    val todayProgress = days.firstOrNull { it.date == today.toString() }
    val period = record.requestPeriod()
    val status = when {
        period == null -> Res.string.p2_finished
        today < period.from -> Res.string.p2_upcoming
        today > period.to -> Res.string.p2_finished
        else -> Res.string.p2_active
    }
    GroupedSection {
        ListRow(record.request.doctorName, subtitle = record.request.complaint,
            trailing = { ProgressRing(if (expected == 0) 1f else done.toFloat() / expected) }, onClick = onClick)
        if (period != null) ListRow(stringResource(status), subtitle = dateRange(period.from, period.to), chevron = false)
        if (todayProgress != null) {
            ListRow(stringResource(Res.string.p2_day_of, todayProgress.dayNumber, record.request.days), chevron = false)
            val left = todayProgress.widgets.filterNot { it.complete }
            PatientMessage(if (left.isEmpty()) stringResource(Res.string.p2_all_done)
                else stringResource(Res.string.p2_left_today, left.joinToString(", ") { "${it.title} (${it.expected - it.done})" }))
        }
    }
}
