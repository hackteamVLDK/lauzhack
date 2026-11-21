package org.easypaper.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.doctor_empty_message
import easypaper.app.shared.generated.resources.doctor_empty_title
import easypaper.app.shared.generated.resources.done
import easypaper.app.shared.generated.resources.entries
import easypaper.app.shared.generated.resources.ic_add
import easypaper.app.shared.generated.resources.new_request
import easypaper.app.shared.generated.resources.new_request_message
import easypaper.app.shared.generated.resources.requests
import easypaper.app.shared.generated.resources.section_entries
import kotlinx.datetime.LocalDate
import org.easypaper.report.Report
import org.easypaper.ui.components.BackAction
import org.easypaper.ui.components.CategoryRow
import org.easypaper.ui.components.EmptyState
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.LargeTitleScaffold
import org.easypaper.ui.components.ListRow
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.components.Sheet
import org.easypaper.ui.components.Sidebar
import org.easypaper.ui.components.SidebarRow
import org.easypaper.ui.components.TintedButton
import org.easypaper.ui.components.category
import org.easypaper.ui.format.dateRange
import org.easypaper.ui.format.lastDay
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Screens of the doctor app. */
sealed interface DoctorScreen {
    data object Home : DoctorScreen
    data class Visit(val reportId: String) : DoctorScreen
    data object NewRequest : DoctorScreen
    data object DesignCatalog : DoctorScreen
    data object Developer : DoctorScreen
}

/** Below this width the sidebar and the content become a list and a pushed screen. */
private val WIDE_LAYOUT = 700.dp

/**
 * The doctor app (Web, Desktop): sidebar with the opened reports and "New request", content column
 * up to 720 dp. The visit screen is filled by task 10, the request builder by task 5.
 */
@Composable
fun DoctorApp(state: ShellState, settings: ShellSettings, onSwitchRole: (() -> Unit)? = null) {
    val navigator = remember { Navigator<DoctorScreen>(DoctorScreen.Home) }
    NavigatorBackHandler(navigator)
    val openDeveloper = { navigator.presentSheet(DoctorScreen.Developer) }
    val select = { screen: DoctorScreen ->
        if (screen is DoctorScreen.Visit) state.selectedReportId = screen.reportId
        navigator.resetTo(screen)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(EasyPaperTheme.colors.background)) {
        val wide = maxWidth >= WIDE_LAYOUT
        val current = navigator.current
        if (current == DoctorScreen.DesignCatalog) {
            CatalogScreen(settings, BackAction(backLabel(), navigator::back))
        } else if (wide) {
            // Side by side the selected report stays open even at the root of the stack.
            val detail = if (current == DoctorScreen.Home) state.selectedReportId?.let { DoctorScreen.Visit(it) } ?: current else current
            Row(Modifier.fillMaxSize()) {
                RequestSidebar(state, detail, select, openDeveloper)
                Box(Modifier.weight(1f)) {
                    DoctorDetail(state, detail, back = null)
                }
            }
        } else if (current == DoctorScreen.Home) {
            RequestList(state, select, openDeveloper)
        } else {
            DoctorDetail(state, current, back = BackAction(stringResource(Res.string.requests), navigator::back))
        }
        Sheet(
            visible = navigator.sheet == DoctorScreen.Developer,
            title = developerTitle(),
            doneLabel = stringResource(Res.string.done),
            onDismiss = navigator::dismissSheet,
        ) {
            DeveloperMenuContent(
                state = state,
                settings = settings,
                onOpenCatalog = { navigator.push(DoctorScreen.DesignCatalog) },
                onSwitchRole = onSwitchRole,
                onDone = navigator::dismissSheet,
            )
        }
    }
}

@Composable
private fun RequestSidebar(state: ShellState, current: DoctorScreen, select: (DoctorScreen) -> Unit, onDeveloper: () -> Unit) {
    Sidebar(
        title = stringResource(Res.string.requests),
        onTitleLongPress = onDeveloper,
        footer = {
            TintedButton(stringResource(Res.string.new_request), onClick = { select(DoctorScreen.NewRequest) }, icon = Res.drawable.ic_add)
        },
    ) {
        items(state.reports, key = { it.id }) { report ->
            SidebarRow(
                title = report.record.request.complaint,
                subtitle = reportPeriod(report),
                selected = current == DoctorScreen.Visit(report.id),
                onClick = { select(DoctorScreen.Visit(report.id)) },
            )
        }
    }
}

/** Narrow layout: the sidebar becomes the root list. */
@Composable
private fun RequestList(state: ShellState, select: (DoctorScreen) -> Unit, onDeveloper: () -> Unit) {
    LargeTitleScaffold(title = stringResource(Res.string.requests), onTitleLongPress = onDeveloper) {
        if (state.reports.isEmpty()) {
            item { EmptyState(stringResource(Res.string.doctor_empty_title), stringResource(Res.string.doctor_empty_message)) }
        } else {
            item {
                GroupedSection {
                    state.reports.forEachIndexed { index, report ->
                        if (index > 0) RowSeparator(EasyPaperTheme.spacing.m)
                        ListRow(
                            report.record.request.complaint,
                            subtitle = reportPeriod(report),
                            onClick = { select(DoctorScreen.Visit(report.id)) },
                        )
                    }
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(top = EasyPaperTheme.spacing.m)) {
                TintedButton(stringResource(Res.string.new_request), onClick = { select(DoctorScreen.NewRequest) }, icon = Res.drawable.ic_add)
            }
        }
    }
}

@Composable
private fun DoctorDetail(state: ShellState, screen: DoctorScreen, back: BackAction?) {
    when (screen) {
        is DoctorScreen.Visit -> state.reports.firstOrNull { it.id == screen.reportId }?.let { VisitScreen(it, back) }
            ?: NothingOpen()
        DoctorScreen.NewRequest -> NewRequestScreen(back)
        else -> NothingOpen()
    }
}

/** Nothing selected: a quiet centered message, as in macOS split views. */
@Composable
private fun NothingOpen() {
    val spacing = EasyPaperTheme.spacing
    Box(Modifier.fillMaxSize().padding(spacing.screenMarginWide), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = spacing.contentMaxWidth / 2 + spacing.xxl * 3),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(stringResource(Res.string.doctor_empty_title), style = EasyPaperTheme.typography.title3,
                color = EasyPaperTheme.colors.secondaryLabel, textAlign = TextAlign.Center)
            Text(stringResource(Res.string.doctor_empty_message), style = EasyPaperTheme.typography.body,
                color = EasyPaperTheme.colors.secondaryLabel, textAlign = TextAlign.Center)
        }
    }
}

/** Task 10 replaces the body with the summary, the originals and the clinic form. */
@Composable
private fun VisitScreen(report: Report, back: BackAction?) {
    val request = report.record.request
    val counts = report.record.entries.groupingBy { it.widgetId }.eachCount()
    LargeTitleScaffold(
        title = request.complaint,
        back = back,
        header = {
            reportPeriod(report)?.let {
                Text(it, style = EasyPaperTheme.typography.subheadline, color = EasyPaperTheme.colors.secondaryLabel)
            }
        },
    ) {
        item {
            GroupedSection(header = stringResource(Res.string.section_entries)) {
                request.widgets.forEachIndexed { index, widget ->
                    if (index > 0) RowSeparator()
                    val count = counts[widget.id] ?: 0
                    CategoryRow(widget.category, widget.title, trailing = {
                        Text(
                            pluralStringResource(Res.plurals.entries, count, count),
                            style = EasyPaperTheme.typography.footnote,
                            color = EasyPaperTheme.colors.secondaryLabel,
                        )
                    })
                }
            }
        }
    }
}

/** Task 5 builds the request form here. */
@Composable
private fun NewRequestScreen(back: BackAction?) {
    LargeTitleScaffold(title = stringResource(Res.string.new_request), back = back) {
        item {
            GroupedSection {
                Text(
                    stringResource(Res.string.new_request_message),
                    style = EasyPaperTheme.typography.body,
                    color = EasyPaperTheme.colors.label,
                    modifier = Modifier.padding(EasyPaperTheme.spacing.m),
                )
            }
        }
    }
}

/** "17–21 Nov" from the request's start date and length; null for a malformed date. */
@Composable
private fun reportPeriod(report: Report): String? {
    val request = report.record.request
    val start = runCatching { LocalDate.parse(request.startDate) }.getOrNull() ?: return null
    return dateRange(start, lastDay(start, request.days))
}
