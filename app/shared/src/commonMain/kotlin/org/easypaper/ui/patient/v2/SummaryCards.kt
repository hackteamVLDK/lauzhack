package org.easypaper.ui.patient.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.ui.components.CapsuleButton
import org.easypaper.ui.format.dayMonth
import org.easypaper.ui.patient.decimalSeparator
import org.easypaper.ui.patient.measurementNumber
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.easypaper.ui.theme.tabular
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SummarySectionTitle(title: String) {
    Text(title, style = EasyPaperTheme.typography.title2, color = EasyPaperTheme.colors.label,
        modifier = Modifier.semantics { heading() })
}

@Composable
private fun summaryValueText(value: SummaryValue?): Pair<String, String> = when (value) {
    is SummaryValue.Pressure -> stringResource(Res.string.p2_widget_pressure_value, value.systolic, value.diastolic) to
        stringResource(Res.string.p2_widget_pressure_unit)
    is SummaryValue.WaterTotal -> stringResource(Res.string.p2_widget_count_value, value.ml) to stringResource(Res.string.p2_summary_ml)
    is SummaryValue.Weight -> measurementNumber(value.kg, decimalSeparator()) to stringResource(Res.string.p2_summary_kg)
    is SummaryValue.Entries -> stringResource(Res.string.p2_widget_count_value, value.count) to stringResource(Res.string.p2_summary_entries)
    null -> stringResource(Res.string.p2_no_data) to ""
}

@Composable
private fun summaryTimeText(time: SummaryTime): String = when (time) {
    is SummaryTime.Today -> time.clock
    SummaryTime.Yesterday -> stringResource(Res.string.p2_summary_yesterday)
    is SummaryTime.Earlier -> stringResource(Res.string.p2_summary_date, dayMonth(time.date), time.date.year)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SummaryPinnedCard(category: Category, data: SummaryData, today: LocalDate, timeZone: TimeZone,
                               editing: Boolean, onOpen: () -> Unit, onEdit: () -> Unit,
                               onRemove: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
                               canPrevious: Boolean, canNext: Boolean, entrance: MutableTransitionState<Boolean>,
                               modifier: Modifier = Modifier) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    val motion = EasyPaperTheme.motion
    val title = categoryTitle(category)
    val (value, unit) = summaryValueText(data.value)
    val time = data.latest?.let { summaryTimeText(summaryTime(it.entry.at, today, timeZone)) }.orEmpty()
    val description = stringResource(Res.string.p2_summary_card_description, title, value, unit, time)
    AnimatedVisibility(entrance, modifier,
        enter = if (motion.reduceMotion) fadeIn(motion.fade()) else fadeIn(motion.fade()) + scaleIn(motion.snappy.spec(), initialScale = 0.9f),
        exit = fadeOut(motion.fade())) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            if (editing) SummaryGlyphButton(stringResource(Res.string.p2_remove_widget, title), onRemove,
                destructive = true)
            val interaction = if (editing) Modifier else Modifier.combinedClickable(
                role = Role.Button, onClick = onOpen, onLongClick = onEdit)
            Column(Modifier.weight(1f).clip(EasyPaperTheme.shapes.widget).background(colors.cell)
                .then(interaction).semantics(mergeDescendants = true) { contentDescription = description }
                .padding(spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Icon(painterResource(category.lineIcon), null, Modifier.size(spacing.rowIcon), tint = category.text(colors))
                    Text(title, style = type.headline, color = category.text(colors), modifier = Modifier.weight(1f))
                    if (time.isNotEmpty()) Text(time, style = type.caption1.tabular(), color = colors.secondaryLabel)
                    if (!editing) Icon(painterResource(Res.drawable.ic_chevron_right), null,
                        Modifier.size(spacing.tileIcon), tint = colors.secondaryLabel)
                }
                // FlowRow keeps large accessibility text and the graph from squeezing each other.
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.s),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xxs), itemVerticalAlignment = Alignment.Bottom) {
                            Text(value, style = if (data.value == null) type.body else type.largeTitle.tabular(), color = colors.label)
                            if (unit.isNotEmpty()) Text(unit, style = type.subheadline, color = colors.secondaryLabel)
                        }
                        if (data.value is SummaryValue.WaterTotal || data.value is SummaryValue.Entries) {
                            Text(stringResource(if (data.value is SummaryValue.WaterTotal) Res.string.p2_summary_today
                                else Res.string.p2_summary_seven_days), style = type.caption1, color = colors.secondaryLabel)
                        }
                    }
                    if (data.latest != null) SummaryMiniChart(data.days, data.metric, category,
                        Modifier.size(spacing.summaryChartWidth, spacing.summaryChartHeight))
                }
            }
            if (editing) Column {
                SummaryGlyphButton(stringResource(Res.string.p2_summary_move_up, title), onPrevious,
                    enabled = canPrevious, arrowUp = true)
                SummaryGlyphButton(stringResource(Res.string.p2_summary_move_down, title), onNext,
                    enabled = canNext, arrowUp = false)
            }
        }
    }
}

/** A 48-point target around a small circle/arrow; edit controls are siblings of the card. */
@Composable
private fun SummaryGlyphButton(label: String, onClick: () -> Unit, enabled: Boolean = true,
                               destructive: Boolean = false, arrowUp: Boolean? = null) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    Box(Modifier.size(spacing.rowMinHeight).clip(EasyPaperTheme.shapes.capsule)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label; if (!enabled) disabled() }, contentAlignment = Alignment.Center) {
        if (destructive) Box(Modifier.size(spacing.summaryRemoveSize).clip(EasyPaperTheme.shapes.capsule)
            .background(colors.destructive), contentAlignment = Alignment.Center) {
            Icon(painterResource(Res.drawable.ic_remove), null, Modifier.size(spacing.rowIcon), tint = colors.onTint)
        } else if (arrowUp != null) Icon(painterResource(Res.drawable.ic_chevron_left), null,
            Modifier.size(spacing.rowIcon).rotate(if (arrowUp) 90f else -90f),
            tint = if (enabled) colors.link else colors.secondaryLabel)
        else Canvas(Modifier.size(spacing.rowIcon)) {
            val inset = spacing.xxs.toPx()
            drawLine(colors.secondaryLabel, Offset(inset, inset), Offset(size.width - inset, size.height - inset), spacing.glyphStroke.toPx())
            drawLine(colors.secondaryLabel, Offset(size.width - inset, inset), Offset(inset, size.height - inset), spacing.glyphStroke.toPx())
        }
    }
}

/** Decorative chart: the card's spoken value and trend's explicit averages carry the data. */
@Composable
internal fun SummaryMiniChart(days: List<SummaryDay>, metric: SummaryMetric, category: Category,
                              modifier: Modifier = Modifier, averages: Pair<Double, Double>? = null) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val accent = category.text(colors)
    Canvas(modifier.clearAndSetSemantics {}) {
        val dots = metric == SummaryMetric.Pressure || metric == SummaryMetric.Weight
        val values = days.mapNotNull { it.value } + listOfNotNull(averages?.first, averages?.second)
        val minimum = if (dots) values.minOrNull() ?: 0.0 else 0.0
        val maximum = values.maxOrNull() ?: minimum
        val range = (maximum - minimum).takeIf { it > 0.0 } ?: 1.0
        val radius = spacing.summaryChartDotRadius.toPx()
        val plotHeight = (size.height - radius * 2).coerceAtLeast(0f)
        val y: (Double) -> Float = { value ->
            if (dots && maximum == minimum) size.height / 2
            else radius + plotHeight * (1 - (value - minimum) / range).toFloat()
        }
        val step = size.width / days.size.coerceAtLeast(1)
        val last = days.indexOfLast { it.value != null && (dots || it.value > 0) }
        days.forEachIndexed { index, day ->
            val value = day.value
            if (value != null) {
                val color = if (index == last) accent else colors.fill
                if (dots) drawCircle(color, radius, Offset(step * (index + 0.5f), y(value)))
                else if (value > 0) {
                    val top = y(value)
                    drawRoundRect(color, Offset(step * (index + 0.2f), top),
                        Size(step * 0.6f, (size.height - top).coerceAtLeast(0f)), CornerRadius(radius))
                }
            }
        }
        if (averages != null) {
            val dash = spacing.xxs.toPx()
            listOf(averages.first, averages.second).forEachIndexed { index, average ->
                drawLine(if (index == 1) accent else colors.secondaryLabel,
                    Offset(size.width * index / 2, y(average)), Offset(size.width * (index + 1) / 2, y(average)),
                    spacing.glyphStroke.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
            }
        }
    }
}

@Composable
internal fun SummaryTrendCard(trend: SummaryTrend, onOpen: () -> Unit) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    val copy = when (trend.metric) {
        SummaryMetric.Pressure -> when (trend.direction) {
            SummaryDirection.Lower -> Res.string.p2_summary_pressure_lower
            SummaryDirection.Same -> Res.string.p2_summary_pressure_same
            SummaryDirection.Higher -> Res.string.p2_summary_pressure_higher
        }
        SummaryMetric.Water -> when (trend.direction) {
            SummaryDirection.Lower -> Res.string.p2_summary_water_lower
            SummaryDirection.Same -> Res.string.p2_summary_water_same
            SummaryDirection.Higher -> Res.string.p2_summary_water_higher
        }
        else -> when (trend.direction) {
            SummaryDirection.Lower -> Res.string.p2_summary_weight_lower
            SummaryDirection.Same -> Res.string.p2_summary_weight_same
            SummaryDirection.Higher -> Res.string.p2_summary_weight_higher
        }
    }
    val unit = stringResource(when (trend.metric) {
        SummaryMetric.Pressure -> Res.string.p2_widget_pressure_unit
        SummaryMetric.Water -> Res.string.p2_summary_ml
        else -> Res.string.p2_summary_kg
    })
    val separator = decimalSeparator()
    Column(Modifier.fillMaxWidth().clip(EasyPaperTheme.shapes.widget).background(colors.cell)
        .clickable(role = Role.Button, onClick = onOpen).padding(spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Text(categoryTitle(trend.category), style = type.headline, color = trend.category.text(colors))
        Text(stringResource(copy), style = type.body, color = colors.label)
        SummaryMiniChart(trend.days, trend.metric, trend.category,
            Modifier.fillMaxWidth().height(spacing.summaryTrendChartHeight), trend.previousAverage to trend.currentAverage)
        Text(stringResource(if (trend.metric == SummaryMetric.Water) Res.string.p2_summary_averages else Res.string.p2_summary_reading_averages,
            measurementNumber(kotlin.math.round(trend.previousAverage * 10) / 10, separator),
            measurementNumber(kotlin.math.round(trend.currentAverage * 10) / 10, separator), unit),
            style = type.footnote.tabular(), color = colors.secondaryLabel)
    }
}

@Composable
internal fun SummaryTipCard(tip: SummaryTip, onDismiss: () -> Unit, onAction: () -> Unit) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    val title = stringResource(when (tip) {
        SummaryTip.Pressure -> Res.string.p2_summary_tip_pressure
        SummaryTip.Medicines -> Res.string.p2_summary_tip_medicines
        SummaryTip.Invite -> Res.string.p2_summary_tip_invite
    })
    Column(Modifier.fillMaxWidth().clip(EasyPaperTheme.shapes.widget).background(colors.cell).padding(spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Icon(painterResource(when (tip) {
                SummaryTip.Pressure -> Category.BloodPressure.lineIcon
                SummaryTip.Medicines -> Category.Medicines.lineIcon
                SummaryTip.Invite -> Res.drawable.ic_qr_code_2
            }), null, Modifier.size(spacing.rowIcon), tint = colors.tint)
            Text(title, style = type.headline, color = colors.label, modifier = Modifier.weight(1f))
            SummaryGlyphButton(stringResource(Res.string.p2_summary_dismiss_tip, title), onDismiss)
        }
        Text(stringResource(when (tip) {
            SummaryTip.Pressure -> Res.string.p2_summary_tip_pressure_body
            SummaryTip.Medicines -> Res.string.p2_summary_tip_medicines_body
            SummaryTip.Invite -> Res.string.p2_summary_tip_invite_body
        }), style = type.body, color = colors.secondaryLabel)
        CapsuleButton(stringResource(if (tip == SummaryTip.Invite) Res.string.p2_summary_open_invite else Res.string.p2_summary_pin), onAction)
    }
}
