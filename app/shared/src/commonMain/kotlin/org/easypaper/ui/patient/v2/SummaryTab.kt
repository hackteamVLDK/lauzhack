package org.easypaper.ui.patient.v2

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import easypaper.app.shared.generated.resources.*
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.easypaper.diary.progress
import org.easypaper.model.PatientRecord
import org.easypaper.ui.components.*
import org.easypaper.ui.format.currentUtcOffsetMinutes
import org.easypaper.ui.format.dateRange
import org.easypaper.ui.format.longDate
import org.easypaper.ui.patient.localDate
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
fun SummaryTab(records: List<PatientRecord>, settings: PatientSettings, today: LocalDate, timeZone: TimeZone,
               onProfile: () -> Unit, onDeveloper: () -> Unit, onPlan: (String) -> Unit,
               onCategory: (Category) -> Unit, onGallery: () -> Unit, onOpenInvite: () -> Unit) {
    val active = records.activeDoctor(today)
    val pinned = settings.pinnedCategories
    val summaries = remember(records, today, timeZone) {
        Category.entries.associateWith { summaryData(records, it, today, timeZone) }
    }
    val trends = remember(records, today, timeZone) {
        Category.entries.mapNotNull { summaryTrend(records, it, today, timeZone) }
    }
    val tips = summaryTips(settings, records.any { it.request.id != PERSONAL_REQUEST_ID && it.request.doctorName.isNotBlank() })
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
    val entryDates = remember(records, timeZone) {
        records.flatMap { record -> record.entries.map { it.localDate(timeZone) } }
    }
    val diaryStreak = remember(entryDates, today) { streak(entryDates, today) }
    // The streak card greets once a day: on the first Summary visit of the day. Leaving Summary (another tab) or
    // restarting the app the same day does not bring it back; remember is dropped when the tab leaves composition.
    val todayKey = today.toString()
    val showStreak = remember(todayKey) { settings.streakSeen.value != todayKey }
    LaunchedEffect(todayKey) { if (settings.streakSeen.value != todayKey) settings.streakSeen.update(todayKey) }
    val entriesToday = remember(entryDates, today) { entryDates.count { it == today } }
    Box(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars),
        contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = spacing.contentMaxWidth).fillMaxWidth(),
            contentPadding = PaddingValues(start = spacing.screenMargin, end = spacing.screenMargin,
                top = spacing.s, bottom = spacing.xxl), verticalArrangement = Arrangement.spacedBy(spacing.l)) {
            item(key = "greeting") {
                SummaryHeader(settings.name.value, greeting, today, onProfile, onDeveloper)
            }
            if (showStreak) item(key = "streak") {
                StreakCard(diaryStreak, entriesToday)
            }
            if (active != null) item(key = "plan") {
                PlanCard(active, today, onClick = { onPlan(active.request.id) })
            }
            item(key = "edit") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.p2_summary_pinned), style = type.title2, color = colors.label,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    if (pinned.isNotEmpty()) PlainButton(
                        stringResource(if (editing) Res.string.p2_done else Res.string.p2_edit_widgets), { editing = !editing })
                }
            }
            if (pinned.isEmpty()) item(key = "empty") {
                Text(stringResource(Res.string.p2_empty_widgets), style = type.body, color = colors.secondaryLabel)
            }
            items(pinned, key = { it.name }) { category ->
                SummaryPinnedCard(category, summaries.getValue(category), today, timeZone, editing,
                    onOpen = { onCategory(category) }, onEdit = { editing = true },
                    onRemove = { settings.removeWidget(category) },
                    onPrevious = { settings.moveWidget(category, -1) }, onNext = { settings.moveWidget(category, 1) },
                    canPrevious = pinned.first() != category, canNext = pinned.last() != category,
                    entrance = entrances.getOrPut(category) { MutableTransitionState(false).apply { targetState = true } },
                    modifier = Modifier.animateItem(
                        fadeInSpec = EasyPaperTheme.motion.fade(),
                        placementSpec = if (EasyPaperTheme.motion.reduceMotion) null else EasyPaperTheme.motion.snappy.spec(),
                        fadeOutSpec = EasyPaperTheme.motion.fade()))
            }
            if (!editing && pinned.size < Category.entries.size) item(key = "add") {
                AddWidgetTile(onGallery, Modifier.fillMaxWidth())
            }
            if (trends.isNotEmpty()) {
                item(key = "trends-title") { SummarySectionTitle(stringResource(Res.string.p2_summary_trends)) }
                items(trends, key = { "trend-${it.category.name}" }) { trend ->
                    SummaryTrendCard(trend, onOpen = { onCategory(trend.category) })
                }
            }
            if (tips.isNotEmpty()) {
                item(key = "tips-title") { SummarySectionTitle(stringResource(Res.string.p2_summary_more)) }
                items(tips, key = { "tip-${it.name}" }) { tip ->
                    SummaryTipCard(tip, onDismiss = { settings.dismissTip(tip) }, onAction = {
                        when (tip) {
                            SummaryTip.Pressure -> settings.addWidget(Category.BloodPressure)
                            SummaryTip.Medicines -> settings.addWidget(Category.Medicines)
                            SummaryTip.Invite -> onOpenInvite()
                        }
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SummaryHeader(name: String?, greeting: String, today: LocalDate, onProfile: () -> Unit, onDeveloper: () -> Unit) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(longDate(today), style = EasyPaperTheme.typography.footnote, color = colors.secondaryLabel)
            val title = if (name.isNullOrBlank()) greeting else stringResource(Res.string.p2_greeting_name, greeting, name.trim())
            WordFitText(title, style = EasyPaperTheme.typography.largeTitle, color = colors.label,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onDeveloper).semantics { heading() })
        }
        AvatarButton(initials(name), onProfile)
    }
}

@Composable
private fun AddWidgetTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val shape = EasyPaperTheme.shapes.widget
    val label = stringResource(Res.string.p2_summary_add)
    Row(modifier.clip(shape).drawBehind {
        val dash = spacing.xs.toPx()
        drawOutline(shape.createOutline(size, layoutDirection, this), colors.separator,
            style = Stroke(spacing.glyphStroke.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))))
    }.clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = label }
        .heightIn(min = spacing.prominentButtonHeight).padding(spacing.m),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(painterResource(Res.drawable.ic_add), null, Modifier.size(spacing.rowIcon), tint = colors.link)
        Spacer(Modifier.width(spacing.xs))
        Text(label, style = EasyPaperTheme.typography.headline, color = colors.link)
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
