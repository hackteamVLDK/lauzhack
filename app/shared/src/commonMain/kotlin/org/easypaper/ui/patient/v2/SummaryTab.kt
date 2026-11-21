package org.easypaper.ui.patient.v2

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import easypaper.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.diary.progress
import org.easypaper.model.PatientRecord
import org.easypaper.model.Water
import org.easypaper.ui.components.*
import org.easypaper.ui.format.currentUtcOffsetMinutes
import org.easypaper.ui.format.dateRange
import org.easypaper.ui.patient.entryText
import org.easypaper.ui.patient.localDate
import org.easypaper.ui.patient.timeOfDay
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun SummaryTab(records: List<PatientRecord>, settings: PatientSettings, today: LocalDate, timeZone: TimeZone,
               onProfile: () -> Unit, onDeveloper: () -> Unit, onPlan: (String) -> Unit,
               onCategory: (Category) -> Unit, onAdd: (Category) -> Unit, onEditPinned: () -> Unit) {
    val active = records.activeDoctor(today)
    val facts = highlights(records, today, timeZone)
    LargeTitleScaffold(stringResource(Res.string.p2_summary), bottomInset = false,
        actions = { AvatarButton(initials(settings.name.value), onProfile) }, onTitleLongPress = onDeveloper) {
        if (active != null) item {
            PlanCard(active, today, onClick = { onPlan(active.request.id) })
            Spacer(Modifier.height(EasyPaperTheme.spacing.sectionGap))
        }
        item {
            GroupedSection(header = stringResource(Res.string.p2_pinned)) {
                settings.pinnedCategories.forEachIndexed { i, category ->
                    if (i > 0) RowSeparator()
                    val entries = categoryEntries(records, category)
                    val latest = entries.firstOrNull()
                    CategoryRow(category, categoryTitle(category), onClick = { onCategory(category) }) {
                        if (category == Category.Water) {
                            val total = entries.filter { it.entry.localDate(timeZone) == today }
                                .sumOf { (it.entry.payload as? Water)?.ml ?: 0 }
                            AnimatedValueText(stringResource(Res.string.p2_water_today, total), style = EasyPaperTheme.typography.title3)
                        } else if (latest != null) {
                            AnimatedValueText(patientEntryText(latest), style = EasyPaperTheme.typography.title3)
                            PatientMessage("${latest.entry.localDate(timeZone)}, ${timeOfDay(latest.entry.at, timeZone)}")
                        } else PatientMessage(stringResource(Res.string.p2_no_data))
                        TintedButton(stringResource(if (category == Category.Water) Res.string.p2_water_quick else Res.string.p2_add_data),
                            { onAdd(category) })
                    }
                }
            }
            PlainButton(stringResource(Res.string.p2_edit_pinned), onEditPinned)
            Spacer(Modifier.height(EasyPaperTheme.spacing.sectionGap))
        }
        item {
            GroupedSection(header = stringResource(Res.string.p2_highlights)) {
                facts.pressure?.let { range ->
                    ListRow(stringResource(Res.string.p2_pressure_week), subtitle = pressureText(range),
                        onClick = { onCategory(Category.BloodPressure) })
                }
                facts.averageWaterMl?.let { average ->
                    ListRow(stringResource(Res.string.p2_water_average, average), subtitle = stringResource(Res.string.p2_water_average_note),
                        onClick = { onCategory(Category.Water) })
                }
                if (facts.pressure == null && facts.averageWaterMl == null) PatientMessage(stringResource(Res.string.p2_no_highlights))
            }
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
