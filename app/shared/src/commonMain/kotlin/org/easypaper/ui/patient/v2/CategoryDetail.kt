package org.easypaper.ui.patient.v2

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import easypaper.app.shared.generated.resources.*
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.math.round
import org.easypaper.model.*
import org.easypaper.ui.components.*
import org.easypaper.ui.format.dateRange
import org.easypaper.ui.format.dayMonth
import org.easypaper.ui.doctor.PressureChart
import org.easypaper.ui.doctor.pressureSeries
import org.easypaper.ui.patient.decimalSeparator
import org.easypaper.ui.patient.localDate
import org.easypaper.ui.patient.measurementNumber
import org.easypaper.ui.patient.timeOfDay
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.easypaper.ui.theme.tabular
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun CategoryDetail(category: Category, records: List<PatientRecord>, today: LocalDate, timeZone: TimeZone,
                   back: BackAction, onAdd: () -> Unit, onAll: () -> Unit, onEntry: (OwnedEntry) -> Unit) {
    var segment by rememberSaveable(category) { mutableIntStateOf(1) }
    var showAbout by rememberSaveable(category) { mutableStateOf(false) }
    val spacing = EasyPaperTheme.spacing
    val motion = EasyPaperTheme.motion
    val period = when (segment) {
        0 -> recentPeriod(today, 1)
        1 -> recentPeriod(today, 7)
        2 -> recentPeriod(today, 30)
        else -> Period(today.plus(DatePeriod(months = -6, days = 1)), today)
    }
    val entries = categoryEntries(records, category).filter { it.entry.localDate(timeZone) in period }
    val aboutTitle = stringResource(Res.string.p2_category_about, categoryTitle(category))
    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(categoryTitle(category), back = back, bottomInset = false,
            actions = { RoundIconButton(Res.drawable.ic_add, stringResource(Res.string.p2_add_data), onAdd) }) {
            item(key = "category-period") {
                SegmentedControl(listOf(stringResource(Res.string.p2_period_day), stringResource(Res.string.p2_period_week),
                    stringResource(Res.string.p2_period_month), stringResource(Res.string.p2_period_six)), segment, { segment = it },
                    descriptions = listOf(stringResource(Res.string.p2_period_day_full), stringResource(Res.string.p2_period_week_full),
                        stringResource(Res.string.p2_period_month_full), stringResource(Res.string.p2_period_six_full)))
            }
            item(key = "category-chart") {
                // Each fading page owns its period and readings, so the outgoing chart stays consistent.
                // A fade has no spatial travel, including with Reduce Motion enabled.
                Crossfade(period, animationSpec = motion.fade(), label = "category period") { shownPeriod ->
                    val shownEntries = remember(records, category, shownPeriod, timeZone) {
                        categoryEntries(records, category).filter { it.entry.localDate(timeZone) in shownPeriod }
                    }
                    Column(Modifier.fillMaxWidth().padding(top = spacing.m)) {
                        CategorySummary(category, shownEntries, shownPeriod)
                        Spacer(Modifier.height(spacing.m))
                        val series = remember(shownEntries) { pressureSeries(shownEntries.map { it.entry }) }
                        if (series != null) {
                            PressureChart(series, timeZone, valuesToggle = false)
                        } else if (category == Category.Water && shownEntries.isNotEmpty()) {
                            GroupedSection { WaterChart(waterPerDay(shownEntries, timeZone), shownPeriod) }
                        }
                    }
                }
            }
            item(key = "category-actions") {
                Spacer(Modifier.height(spacing.sectionGap))
                GroupedSection {
                    ListRow(stringResource(Res.string.p2_show_all), onClick = onAll)
                    RowSeparator()
                    ListRow(aboutTitle, onClick = { showAbout = true })
                }
            }
            if (entries.isNotEmpty()) item(key = "category-readings") {
                Spacer(Modifier.height(spacing.sectionGap))
                // Text values remain the accessible alternative to the charts.
                GroupedSection {
                    entries.forEachIndexed { index, owned ->
                        if (index > 0) RowSeparator()
                        ListRow(patientEntryText(owned), subtitle = entryDate(owned, timeZone), onClick = { onEntry(owned) })
                    }
                }
            }
        }
        Sheet(showAbout, aboutTitle, stringResource(Res.string.p2_done), { showAbout = false }) {
            PatientMessage(stringResource(when (category) {
                Category.BloodPressure -> Res.string.p2_category_pressure_fact
                Category.Water -> Res.string.p2_category_water_fact
                Category.Food -> Res.string.p2_category_food_fact
                Category.Medicines -> Res.string.p2_category_medicines_fact
                Category.Questionnaire -> Res.string.p2_category_symptoms_fact
                Category.Diary -> Res.string.p2_category_diary_fact
                Category.DoctorQuestion -> Res.string.p2_category_questions_fact
                Category.Measurements -> Res.string.p2_category_measurements_fact
            }))
        }
    }
}

@Composable
private fun CategorySummary(category: Category, entries: List<OwnedEntry>, period: Period) {
    val type = EasyPaperTheme.typography
    val colors = EasyPaperTheme.colors
    val pressure = pressureRange(entries.map { it.entry })
    // Measurements can include unrelated units: only weight in kg participates in the weight mean.
    val weights = entries.mapNotNull { it.entry.payload as? Measurement }
        .filter { it.loinc == Measurement.WEIGHT_LOINC && it.unit == "kg" && it.value.isFinite() }
    val weightSummary = category == Category.Measurements && (weights.isNotEmpty() || entries.isEmpty())
    val label = stringResource(when {
        category == Category.BloodPressure -> Res.string.p2_category_range
        category == Category.Water -> Res.string.p2_category_daily_average
        weightSummary -> Res.string.p2_category_average
        else -> Res.string.p2_category_total
    })
    val value = when {
        entries.isEmpty() -> stringResource(Res.string.p2_no_data)
        category == Category.BloodPressure && pressure != null -> stringResource(Res.string.p2_category_pressure_value,
            pressure.systolicMin, pressure.systolicMax, pressure.diastolicMin, pressure.diastolicMax)
        category == Category.Water -> {
            val days = (period.to.toEpochDays() - period.from.toEpochDays()).toInt() + 1
            (entries.sumOf { (it.entry.payload as? Water)?.ml ?: 0 } / days).toString()
        }
        weightSummary -> measurementNumber(round(weights.map { it.value }.average() * 10) / 10, decimalSeparator())
        else -> entries.size.toString()
    }
    val unit = if (entries.isEmpty()) null else when {
        category == Category.BloodPressure -> stringResource(Res.string.unit_mmhg)
        category == Category.Water -> stringResource(Res.string.p2_category_ml_unit)
        weightSummary -> stringResource(Res.string.m_kg)
        else -> pluralStringResource(Res.plurals.p2_category_entries_unit, entries.size)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EasyPaperTheme.spacing.xxs)) {
        Text(label, style = type.footnote, color = colors.secondaryLabel)
        Text(buildAnnotatedString {
            append(value)
            if (unit != null) withStyle(type.footnote.toSpanStyle()) { append("\u00A0$unit") }
        }, style = type.title1.tabular(), color = colors.label)
        Text(dateRange(period.from, period.to), style = type.footnote, color = colors.secondaryLabel)
        if (category == Category.Water && entries.isNotEmpty()) {
            Text(stringResource(Res.string.p2_water_average_note), style = type.caption1, color = colors.secondaryLabel)
        }
    }
}

private fun waterPerDay(entries: List<OwnedEntry>, timeZone: TimeZone): Map<LocalDate, Int> =
    entries.filter { it.entry.payload is Water }.groupBy { it.entry.localDate(timeZone) }
        .mapValues { (_, day) -> day.sumOf { (it.entry.payload as Water).ml } }

/** Water per day as bars from 0; the scale shows 0 and the day's maximum in ml, the axis the first and last day. */
@Composable
private fun WaterChart(daily: Map<LocalDate, Int>, period: Period) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val type = EasyPaperTheme.typography
    val measurer = rememberTextMeasurer()
    val axisStyle = type.caption1.tabular().copy(color = colors.secondaryLabel)
    val legend = stringResource(Res.string.p2_water_chart)
    val high = (daily.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val top = stringResource(Res.string.p2_ml, high)
    val zero = stringResource(Res.string.p2_ml, 0)
    val totalDays = (period.to.toEpochDays() - period.from.toEpochDays()).toFloat() + 1f
    Column(Modifier.fillMaxWidth().padding(horizontal = spacing.m, vertical = spacing.xs)) {
        Text(legend, style = type.footnote, color = colors.secondaryLabel)
        Canvas(Modifier.fillMaxWidth().height(spacing.rowMinHeight * 4).padding(vertical = spacing.xs)
            .semantics { contentDescription = legend }) {
            val topLabel = measurer.measure(top, axisStyle)
            val zeroLabel = measurer.measure(zero, axisStyle)
            val left = maxOf(topLabel.size.width, zeroLabel.size.width) + spacing.xs.toPx()
            val half = topLabel.size.height / 2f
            val bottom = size.height - half
            val stroke = spacing.xxs.toPx() / 2
            val y: (Int) -> Float = { ml -> bottom - ml.toFloat() / high * (bottom - half) }
            // Scale lines at 0 and at the maximum, each with its value where the line is.
            for ((ml, label) in listOf(0 to zeroLabel, high to topLabel)) {
                drawLine(colors.separator, Offset(left, y(ml)), Offset(size.width, y(ml)), stroke)
                drawText(label, topLeft = Offset(left - label.size.width - spacing.xxs.toPx(), y(ml) - half))
            }
            val plot = size.width - left
            val width = (plot / totalDays * 0.6f).coerceAtLeast(stroke)
            daily.forEach { (date, ml) ->
                val x = left + ((date.toEpochDays() - period.from.toEpochDays()).toFloat() + 0.5f) / totalDays * plot
                drawRect(colors.tint, Offset(x - width / 2, y(ml)), Size(width, bottom - y(ml)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dayMonth(period.from), style = type.caption1, color = colors.secondaryLabel)
            if (period.to != period.from) Text(dayMonth(period.to), style = type.caption1, color = colors.secondaryLabel)
        }
    }
}

/** "21 Nov, 08:10"; an entry from another year also shows its year, so two years' readings stay apart. */
@Composable
fun entryDate(owned: OwnedEntry, timeZone: TimeZone): String {
    val date = owned.entry.localDate(timeZone)
    val day = if (date.year == org.easypaper.ui.format.today().year) dayMonth(date) else "${dayMonth(date)} ${date.year}"
    return "$day, ${timeOfDay(owned.entry.at, timeZone)}"
}

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
