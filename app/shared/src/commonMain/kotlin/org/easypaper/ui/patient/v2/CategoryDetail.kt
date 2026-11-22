package org.easypaper.ui.patient.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.*
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.easypaper.model.*
import org.easypaper.ui.components.*
import org.easypaper.ui.format.dateRange
import org.easypaper.ui.patient.entryText
import org.easypaper.ui.patient.localDate
import org.easypaper.ui.patient.timeOfDay
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun CategoryDetail(category: Category, records: List<PatientRecord>, today: LocalDate, timeZone: TimeZone,
                   back: BackAction, onAdd: () -> Unit, onAll: () -> Unit, onEntry: (OwnedEntry) -> Unit) {
    var segment by rememberSaveable(category) { mutableIntStateOf(1) }
    val period = when (segment) {
        0 -> recentPeriod(today, 1)
        1 -> recentPeriod(today, 7)
        2 -> recentPeriod(today, 30)
        else -> Period(today.plus(DatePeriod(months = -6, days = 1)), today)
    }
    val entries = categoryEntries(records, category).filter { it.entry.localDate(timeZone) in period }
    LargeTitleScaffold(categoryTitle(category), back = back, bottomInset = false,
        actions = { PlainButton(stringResource(Res.string.p2_add_data), onAdd) }) {
        item {
            SegmentedControl(listOf(stringResource(Res.string.p2_period_day), stringResource(Res.string.p2_period_week),
                stringResource(Res.string.p2_period_month), stringResource(Res.string.p2_period_six)), segment, { segment = it },
                descriptions = listOf(stringResource(Res.string.p2_period_day_full), stringResource(Res.string.p2_period_week_full),
                    stringResource(Res.string.p2_period_month_full), stringResource(Res.string.p2_period_six_full)))
            PatientMessage(dateRange(period.from, period.to))
            GroupedSection {
                val range = pressureRange(entries.map { it.entry })
                when {
                    entries.isEmpty() -> PatientMessage(stringResource(Res.string.p2_no_data))
                    range != null -> {
                        PatientMessage(pressureText(range))
                        RecordChart(entries, category, period, timeZone)
                    }
                    category == Category.Water -> {
                        val daily = entries.groupBy { it.entry.localDate(timeZone) }.values.map { day -> day.sumOf { (it.entry.payload as Water).ml } }
                        PatientMessage(stringResource(Res.string.p2_ml, daily.min()) + " – " + stringResource(Res.string.p2_ml, daily.max()))
                        RecordChart(entries, category, period, timeZone)
                    }
                }
                // Text values are also the accessible alternative to charts.
                entries.forEach { owned ->
                    ListRow(patientEntryText(owned), subtitle = entryDate(owned, timeZone), onClick = { onEntry(owned) })
                }
            }
            Spacer(Modifier.height(EasyPaperTheme.spacing.sectionGap))
            GroupedSection {
                ListRow(stringResource(Res.string.p2_show_all), onClick = onAll)
                RowSeparator()
                ListRow(stringResource(Res.string.p2_about_records), subtitle = stringResource(Res.string.p2_records_body), chevron = false)
            }
        }
    }
}

@Composable
private fun RecordChart(entries: List<OwnedEntry>, category: Category, period: Period, timeZone: TimeZone) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val legend = stringResource(if (category == Category.BloodPressure) Res.string.p2_pressure_legend else Res.string.p2_water_chart)
    PatientMessage(legend)
    val chronological = entries.sortedBy { it.entry.at }
    val totalDays = (period.to.toEpochDays() - period.from.toEpochDays()).toFloat() + 1f
    val daily = entries.groupBy { it.entry.localDate(timeZone) }.mapValues { (_, values) -> values.sumOf { (it.entry.payload as? Water)?.ml ?: 0 } }
    val pressures = entries.mapNotNull { it.entry.payload as? BloodPressure }
    val low = if (pressures.isEmpty()) 0f else (pressures.minOf { it.diastolic } - 10).coerceAtLeast(0).toFloat()
    val high = if (pressures.isEmpty()) (daily.values.maxOrNull() ?: 1).coerceAtLeast(1).toFloat() else (pressures.maxOf { it.systolic } + 10).toFloat()
    Row(Modifier.fillMaxWidth().padding(horizontal = spacing.m)) {
        Column { PatientMessage(high.toInt().toString()); Spacer(Modifier.height(spacing.xxl * 2)); PatientMessage(low.toInt().toString()) }
        Canvas(Modifier.weight(1f).height(spacing.rowMinHeight * 4).padding(spacing.s).semantics { contentDescription = legend }) {
            val stroke = spacing.xxs.toPx() / 2
            drawLine(colors.separator, Offset(0f, 0f), Offset(0f, size.height), stroke)
            drawLine(colors.separator, Offset(0f, size.height), Offset(size.width, size.height), stroke)
            fun x(date: LocalDate, fraction: Float = 0.5f) =
                ((date.toEpochDays() - period.from.toEpochDays()).toFloat() + fraction) / totalDays * size.width
            fun y(value: Float) = size.height * (1f - (value - low) / (high - low))
            if (category == Category.Water) daily.forEach { (date, ml) ->
                val width = (size.width / totalDays * 0.6f).coerceAtLeast(stroke)
                val top = y(ml.toFloat())
                drawRect(colors.tint, Offset(x(date) - width / 2, top), Size(width, size.height - top))
            } else chronological.forEach { owned ->
                val value = owned.entry.payload as BloodPressure
                val local = Instant.fromEpochMilliseconds(owned.entry.at).toLocalDateTime(timeZone)
                val x = x(local.date, (local.hour * 60 + local.minute) / 1440f)
                val radius = spacing.xxs.toPx()
                drawCircle(colors.tint, radius, Offset(x, y(value.systolic.toFloat())))
                val dia = y(value.diastolic.toFloat())
                val diamond = Path().apply {
                    moveTo(x, dia - radius); lineTo(x + radius, dia); lineTo(x, dia + radius); lineTo(x - radius, dia); close()
                }
                drawPath(diamond, colors.label)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) { PatientMessage(period.from.toString()) }
        Column(Modifier.weight(1f)) { PatientMessage(period.to.toString()) }
    }
}

fun entryDate(owned: OwnedEntry, timeZone: TimeZone) = "${owned.entry.localDate(timeZone)}, ${timeOfDay(owned.entry.at, timeZone)}"

@Composable
fun AllCategoryData(category: Category, records: List<PatientRecord>, timeZone: TimeZone, back: BackAction,
                    onEntry: (OwnedEntry) -> Unit, onDelete: (OwnedEntry) -> Unit) {
    val entries = categoryEntries(records, category)
    LargeTitleScaffold(stringResource(Res.string.p2_all_data), back = back, bottomInset = false) {
        item { PatientMessage(categoryTitle(category)); PatientMessage(stringResource(Res.string.p2_entries_count, entries.size)) }
        if (entries.isEmpty()) item { PatientMessage(stringResource(Res.string.p2_no_data)) }
        entries.forEach { owned -> item(key = owned.record.request.id + ":" + owned.entry.id) {
            GroupedSection(Modifier.padding(bottom = EasyPaperTheme.spacing.s)) {
                ListRow(patientEntryText(owned), subtitle = entryDate(owned, timeZone), onClick = { onEntry(owned) })
                ListRow(stringResource(Res.string.p2_delete), titleColor = EasyPaperTheme.colors.destructive,
                    chevron = false, onClick = { onDelete(owned) })
            }
        } }
    }
}
