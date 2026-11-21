package org.easypaper.ui.doctor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import easypaper.app.shared.generated.resources.chart_lower
import easypaper.app.shared.generated.resources.chart_upper
import org.easypaper.model.BloodPressure
import org.easypaper.model.Entry
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.theme.EasyPaperTheme
import org.easypaper.ui.theme.tabular
import org.jetbrains.compose.resources.stringResource

/** Readings in time order with the value range the chart's y axis must show (rounded out to tens). */
data class PressureSeries(val readings: List<Pair<Long, BloodPressure>>, val yMin: Int, val yMax: Int)

fun pressureSeries(entries: List<Entry>): PressureSeries? {
    val readings = entries.mapNotNull { e -> (e.payload as? BloodPressure)?.let { e.at to it } }.sortedBy { it.first }
    if (readings.isEmpty()) return null
    val low = readings.minOf { it.second.diastolic }
    val high = readings.maxOf { it.second.systolic }
    return PressureSeries(readings, (low / 10) * 10, ((high + 9) / 10) * 10)
}

/**
 * Blood pressure over the period (docs/design.md): one line per series, circles = upper (systolic),
 * diamonds = lower (diastolic), real units, no decoration. Shape and legend tell the series apart, not color.
 */
@Composable
fun PressureChart(series: PressureSeries, modifier: Modifier = Modifier) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val type = EasyPaperTheme.typography
    val measurer = rememberTextMeasurer()
    val axisStyle = type.caption1.tabular().copy(color = colors.secondaryLabel)
    val upper = stringResource(Res.string.chart_upper)
    val lower = stringResource(Res.string.chart_lower)
    val systolic = series.readings.map { it.second.systolic }
    val diastolic = series.readings.map { it.second.diastolic }
    val description = stringResource(
        Res.string.chart_description,
        "${systolic.min()}–${systolic.max()}",
        "${diastolic.min()}–${diastolic.max()}",
        series.readings.size,
    )
    GroupedSection(modifier) {
        Column(Modifier.fillMaxWidth().padding(spacing.m)) {
            Canvas(
                Modifier.fillMaxWidth().height(spacing.xxl * 6).semantics { contentDescription = description },
            ) {
                drawChart(series, measurer, axisStyle, colors.separator, colors.label, colors.secondaryLabel, spacing.xxs.toPx(), spacing.xs.toPx())
            }
            Row(Modifier.padding(top = spacing.xs), horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
                Legend(upper, diamond = false, color = colors.label)
                Legend(lower, diamond = true, color = colors.secondaryLabel)
            }
        }
    }
}

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
    measurer: TextMeasurer,
    axisStyle: TextStyle,
    grid: Color,
    upperColor: Color,
    lowerColor: Color,
    small: Float, // spacing.xxs in px: marker radius, edge padding
    gap: Float, // spacing.xs in px: room for the axis labels
) {
    val axisWidth = measurer.measure("000", axisStyle).size.width + gap
    val left = axisWidth
    val right = size.width - small
    val top = gap
    val bottom = size.height - gap
    val span = (series.yMax - series.yMin).coerceAtLeast(10)
    fun y(value: Int) = bottom - (value - series.yMin).toFloat() / span * (bottom - top)
    val first = series.readings.first().first
    val last = series.readings.last().first
    fun x(at: Long) = if (last == first) (left + right) / 2 else left + (at - first).toFloat() / (last - first) * (right - left)

    // Horizontal grid every 20 mmHg with its value: the only scale the doctor needs.
    var value = ((series.yMin + 19) / 20) * 20
    while (value <= series.yMax) {
        val gy = y(value)
        drawLine(grid, Offset(left, gy), Offset(right, gy), strokeWidth = 1f)
        val label = measurer.measure(value.toString(), axisStyle)
        drawText(label, topLeft = Offset(left - label.size.width - small, gy - label.size.height / 2))
        value += 20
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
