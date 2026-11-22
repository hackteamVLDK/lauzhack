package org.easypaper.ui.doctor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.chart_description
import easypaper.app.shared.generated.resources.chart_hide_values
import easypaper.app.shared.generated.resources.chart_lower
import easypaper.app.shared.generated.resources.chart_reading
import easypaper.app.shared.generated.resources.chart_show_values
import easypaper.app.shared.generated.resources.chart_upper
import easypaper.app.shared.generated.resources.date_day_month
import easypaper.app.shared.generated.resources.months_short
import easypaper.app.shared.generated.resources.pulse
import easypaper.app.shared.generated.resources.unit_mmhg
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.easypaper.model.BloodPressure
import org.easypaper.model.Entry
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.PlainButton
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.patient.timeOfDay
import org.easypaper.ui.theme.EasyPaperTheme
import org.easypaper.ui.theme.tabular
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Readings in time order with the value range the chart's y axis must show (rounded out to tens). */
data class PressureSeries(val readings: List<Pair<Long, BloodPressure>>, val yMin: Int, val yMax: Int)

fun pressureSeries(entries: List<Entry>): PressureSeries? {
    val readings = entries.mapNotNull { e -> (e.payload as? BloodPressure)?.let { e.at to it } }.sortedBy { it.first }
    if (readings.isEmpty()) return null
    val low = readings.minOf { it.second.diastolic }
    val high = readings.maxOf { it.second.systolic }
    return PressureSeries(readings, (low / 10) * 10, ((high + 9) / 10) * 10)
}

/** The calendar days the readings fall on, each with the moment its label sits at (local noon). */
@OptIn(ExperimentalTime::class)
fun readingDays(series: PressureSeries, timeZone: TimeZone): List<Pair<LocalDate, Long>> =
    series.readings.map { Instant.fromEpochMilliseconds(it.first).toLocalDateTime(timeZone).date }.distinct()
        .map { it to it.atTime(LocalTime(12, 0)).toInstant(timeZone).toEpochMilliseconds() }

/**
 * Blood pressure over the period (docs/design.md): one line per series, circles = upper (systolic),
 * diamonds = lower (diastolic), days on the x axis, mmHg on the y axis, no decoration. Shape and legend tell
 * the series apart, not color. "Show values" opens the same readings as a list, for screen readers and for
 * the exact numbers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PressureChart(series: PressureSeries, timeZone: TimeZone, modifier: Modifier = Modifier) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val type = EasyPaperTheme.typography
    val measurer = rememberTextMeasurer()
    val axisStyle = type.caption1.tabular().copy(color = colors.secondaryLabel)
    val upper = stringResource(Res.string.chart_upper)
    val lower = stringResource(Res.string.chart_lower)
    val unit = stringResource(Res.string.unit_mmhg)
    val systolic = series.readings.map { it.second.systolic }
    val diastolic = series.readings.map { it.second.diastolic }
    val description = stringResource(
        Res.string.chart_description,
        "${systolic.min()}–${systolic.max()}",
        "${diastolic.min()}–${diastolic.max()}",
        series.readings.size,
    )
    val pattern = stringResource(Res.string.date_day_month)
    val months = stringArrayResource(Res.array.months_short)
    val days = remember(series, timeZone) { readingDays(series, timeZone) }
    val dayLabels = days.map { (date, at) -> at to dayLabel(pattern, months, date) }
    var showValues by rememberSaveable { mutableStateOf(false) }

    GroupedSection(modifier) {
        Column(Modifier.fillMaxWidth().padding(spacing.m)) {
            Canvas(
                Modifier.fillMaxWidth().height(spacing.xxl * 6).semantics { contentDescription = description },
            ) {
                drawChart(series, dayLabels, unit, measurer, axisStyle, colors.separator, colors.label, colors.secondaryLabel,
                    spacing.xxs.toPx(), spacing.xs.toPx())
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(top = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.l),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Legend(upper, diamond = false, color = colors.label)
                Legend(lower, diamond = true, color = colors.secondaryLabel)
                PlainButton(stringResource(if (showValues) Res.string.chart_hide_values else Res.string.chart_show_values),
                    onClick = { showValues = !showValues })
            }
        }
        if (showValues) {
            series.readings.forEach { (at, bp) ->
                RowSeparator(spacing.m)
                val date = Instant.fromEpochMilliseconds(at).toLocalDateTime(timeZone).date
                val value = stringResource(Res.string.chart_reading, bp.systolic, bp.diastolic) + " " + unit +
                    (bp.pulse?.let { ", " + stringResource(Res.string.pulse, it) } ?: "")
                FlowRow(
                    Modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight).padding(horizontal = spacing.m, vertical = spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dayLabel(pattern, months, date) + ", " + timeOfDay(at, timeZone), style = type.body, color = colors.secondaryLabel)
                    Text(value, style = type.body.tabular(), color = colors.label)
                }
            }
        }
    }
}

/** "14 Mar" from the locale's day-month pattern ("%1$d %2$s") and the short month names. */
private fun dayLabel(pattern: String, months: List<String>, date: LocalDate) =
    pattern.replace("%1\$d", date.day.toString()).replace("%2\$s", months.getOrElse(date.month.ordinal) { "" })

@Composable
private fun Legend(label: String, diamond: Boolean, color: Color) {
    val size = EasyPaperTheme.spacing.s
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EasyPaperTheme.spacing.xxs)) {
        Canvas(Modifier.size(size)) { marker(center, this.size.minDimension / 2, diamond, color) }
        Text(label, style = EasyPaperTheme.typography.footnote, color = EasyPaperTheme.colors.secondaryLabel)
    }
}

private fun DrawScope.marker(at: Offset, radius: Float, diamond: Boolean, color: Color) {
    if (!diamond) {
        drawCircle(color, radius, at)
        return
    }
    val path = Path().apply {
        moveTo(at.x, at.y - radius); lineTo(at.x + radius, at.y); lineTo(at.x, at.y + radius); lineTo(at.x - radius, at.y); close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawChart(
    series: PressureSeries,
    dayLabels: List<Pair<Long, String>>,
    unit: String,
    measurer: TextMeasurer,
    axisStyle: TextStyle,
    grid: Color,
    upperColor: Color,
    lowerColor: Color,
    small: Float, // spacing.xxs in px: marker radius, edge padding
    gap: Float, // spacing.xs in px: room for the axis labels
) {
    val unitLabel = measurer.measure(unit, axisStyle)
    val axisWidth = maxOf(measurer.measure("000", axisStyle).size.width, unitLabel.size.width) + gap
    val left = axisWidth
    val right = size.width - small
    val lineHeight = unitLabel.size.height
    val top = lineHeight + gap
    val bottom = size.height - lineHeight - gap
    val span = (series.yMax - series.yMin).coerceAtLeast(10)
    fun y(value: Int) = bottom - (value - series.yMin).toFloat() / span * (bottom - top)
    // The x axis runs over whole days when there is more than one, so the day labels sit inside it.
    val first = minOf(series.readings.first().first, dayLabels.first().first - HALF_DAY)
    val last = maxOf(series.readings.last().first, dayLabels.last().first + HALF_DAY)
    fun x(at: Long) = if (last == first) (left + right) / 2 else left + (at - first).toFloat() / (last - first) * (right - left)

    // The unit above the scale, where the eye looks for it.
    drawText(unitLabel, topLeft = Offset(0f, 0f))
    // Horizontal grid every 20 mmHg with its value: the only scale the doctor needs.
    var value = ((series.yMin + 19) / 20) * 20
    while (value <= series.yMax) {
        val gy = y(value)
        drawLine(grid, Offset(left, gy), Offset(right, gy), strokeWidth = 1f)
        val label = measurer.measure(value.toString(), axisStyle)
        drawText(label, topLeft = Offset(left - label.size.width - small, gy - label.size.height / 2))
        value += 20
    }
    // Day labels under the plot; one that would touch the previous one is skipped (a long period on a narrow screen).
    var lastEnd = Float.NEGATIVE_INFINITY
    for ((at, text) in dayLabels) {
        val label = measurer.measure(text, axisStyle)
        if (label.size.width > right - left) continue // wider than the plot (narrow window, 200 % text): no room
        val lx = (x(at) - label.size.width / 2).coerceIn(left, right - label.size.width)
        if (lx < lastEnd + gap) continue
        drawText(label, topLeft = Offset(lx, bottom + gap))
        lastEnd = lx + label.size.width
    }
    val radius = small
    for ((pick, diamond, color) in listOf(
        Triple({ bp: BloodPressure -> bp.systolic }, false, upperColor),
        Triple({ bp: BloodPressure -> bp.diastolic }, true, lowerColor),
    )) {
        val points = series.readings.map { (at, bp) -> Offset(x(at), y(pick(bp))) }
        val line = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        drawPath(line, color, style = Stroke(width = small / 2))
        for (p in points) marker(p, radius, diamond, color)
    }
}

private const val HALF_DAY = 12 * 60 * 60 * 1000L
