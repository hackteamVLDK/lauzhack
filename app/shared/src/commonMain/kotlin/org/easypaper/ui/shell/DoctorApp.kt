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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.cost_title
import easypaper.app.shared.generated.resources.doctor_empty_message
import easypaper.app.shared.generated.resources.doctor_empty_title
import easypaper.app.shared.generated.resources.done
import easypaper.app.shared.generated.resources.entries
import easypaper.app.shared.generated.resources.ic_add
import easypaper.app.shared.generated.resources.ic_checklist
import easypaper.app.shared.generated.resources.ic_qr_code_2
import easypaper.app.shared.generated.resources.new_request
import easypaper.app.shared.generated.resources.open_demo_report
import easypaper.app.shared.generated.resources.open_report
import easypaper.app.shared.generated.resources.new_request_message
import easypaper.app.shared.generated.resources.requests
import easypaper.app.shared.generated.resources.section_entries
import easypaper.app.shared.generated.resources.switch_to_patient
import io.ktor.client.HttpClient
import kotlinx.datetime.LocalDate
import org.easypaper.ai.AiClient
import org.easypaper.ai.AiStatus
import org.easypaper.demo.DemoAi
import org.easypaper.report.Report
import org.easypaper.ui.components.BackAction
import org.easypaper.ui.doctor.RequestBuilderScreen
import org.easypaper.ui.doctor.VisitAi
import org.easypaper.ui.doctor.VisitScreen
import org.easypaper.ui.components.CategoryRow
import org.easypaper.ui.components.EmptyState
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.LargeTitleScaffold
import org.easypaper.ui.components.ListRow
import org.easypaper.ui.components.PlainButton
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.components.PushNavigation
import org.easypaper.ui.components.Sheet
import org.easypaper.ui.components.Sidebar
import org.easypaper.ui.components.SidebarRow
import org.easypaper.ui.components.TintedButton
import org.easypaper.ui.components.category
import org.easypaper.ui.cost.AiCostScreen
import org.easypaper.ui.doctor.DocumentsSection
import org.easypaper.ui.handover.OpenReportScreen
import org.easypaper.ui.handover.relayUrl
import org.easypaper.ui.format.dateRange
import org.easypaper.ui.format.lastDay
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.coroutines.cancellation.CancellationException

/** Screens of the doctor app. */
sealed interface DoctorScreen {
    data object Home : DoctorScreen
    data class Visit(val reportId: String) : DoctorScreen
    data object NewRequest : DoctorScreen
    data object OpenReport : DoctorScreen
    data object Cost : DoctorScreen
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
    LaunchedEffect(state) { state.refreshAiStatus() }
    NavigatorBackHandler(navigator)
    val appScope = rememberCoroutineScope()
    val openDeveloper = { navigator.presentSheet(DoctorScreen.Developer) }
    val select = { screen: DoctorScreen ->
        if (screen is DoctorScreen.Visit) state.selectedReportId = screen.reportId
        navigator.resetTo(screen)
    }
    val openDemo: () -> Unit = {
        appScope.launch {
            state.fillWithDemoData()
            state.selectedReportId?.let { select(DoctorScreen.Visit(it)) }
        }
    }
    val emptyActions: @Composable () -> Unit = {
        if (state.reports.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(EasyPaperTheme.spacing.xs)) {
                TintedButton(stringResource(Res.string.open_demo_report), onClick = openDemo)
                if (onSwitchRole != null) {
                    PlainButton(stringResource(Res.string.switch_to_patient), onClick = onSwitchRole)
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(EasyPaperTheme.colors.background)) {
        val wide = maxWidth >= WIDE_LAYOUT
        val current = navigator.current
        if (wide && current == DoctorScreen.DesignCatalog) {
            CatalogScreen(settings, BackAction(backLabel(), navigator::back))
        } else if (wide) {
            // Side by side the selected report stays open even at the root of the stack.
            val detail = if (current == DoctorScreen.Home) state.selectedReportId?.let { DoctorScreen.Visit(it) } ?: current else current
            Row(Modifier.fillMaxSize()) {
                RequestSidebar(state, detail, select, openDeveloper)
                Box(Modifier.weight(1f)) {
                    DoctorDetail(state, detail, settings, back = null, select = select, emptyActions = emptyActions)
                }
            }
        } else {
            PushNavigation(navigator.stack, Modifier.fillMaxSize()) { screen ->
                when (screen) {
                    DoctorScreen.Home -> RequestList(state, select, openDeveloper, emptyActions)
                    DoctorScreen.DesignCatalog -> CatalogScreen(settings, BackAction(backLabel(), navigator::back))
                    else -> DoctorDetail(state, screen, settings, back = BackAction(stringResource(Res.string.requests), navigator::back), select = select, emptyActions = emptyActions)
                }
            }
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
                scope = appScope,
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
            TintedButton(
                stringResource(Res.string.cost_title),
                onClick = { select(DoctorScreen.Cost) },
                icon = Res.drawable.ic_checklist,
            )
            TintedButton(stringResource(Res.string.open_report), onClick = { select(DoctorScreen.OpenReport) }, icon = Res.drawable.ic_qr_code_2)
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
private fun RequestList(state: ShellState, select: (DoctorScreen) -> Unit, onDeveloper: () -> Unit, emptyActions: @Composable () -> Unit) {
    LargeTitleScaffold(title = stringResource(Res.string.requests), onTitleLongPress = onDeveloper) {
        if (state.reports.isEmpty()) {
            item { EmptyState(stringResource(Res.string.doctor_empty_title), stringResource(Res.string.doctor_empty_message), action = emptyActions) }
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
                TintedButton(
                    stringResource(Res.string.cost_title),
                    onClick = { select(DoctorScreen.Cost) },
                    icon = Res.drawable.ic_checklist,
                )
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(top = EasyPaperTheme.spacing.m)) {
                TintedButton(stringResource(Res.string.open_report), onClick = { select(DoctorScreen.OpenReport) }, icon = Res.drawable.ic_qr_code_2)
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
private fun DoctorDetail(state: ShellState, screen: DoctorScreen, settings: ShellSettings, back: BackAction?, select: (DoctorScreen) -> Unit, emptyActions: @Composable () -> Unit) {
    when (screen) {
        is DoctorScreen.Visit -> state.reports.firstOrNull { it.id == screen.reportId }?.let { report -> key(report.id) { VisitScreen(report, back, visitAi(state, report), state.reportPhotos[report.id].orEmpty(), store = state.visits,
                documents = { visit -> DocumentsSection(visit, state.server?.let { server -> { server.ai } }, state.documents[report.id].orEmpty()) { state.documents[report.id] = it } }) } }
            ?: NothingOpen(emptyActions)
        DoctorScreen.NewRequest -> RequestBuilderScreen(back)
        // the patient's QR opens the report once (task 30); it joins the list and opens right away
        DoctorScreen.OpenReport -> OpenReportScreen(relayUrl(settings.serverUrl, defaultServerUrl()), back = back, onOpened = { received ->
            state.receive(received)
            select(DoctorScreen.Visit(received.report.id))
        })
        DoctorScreen.Cost -> {
            val report = state.selectedReport
            if (state.aiUsage.isNotEmpty() || state.aiStatus != null) {
                AiCostScreen(
                    status = state.aiStatus,
                    usage = state.aiUsage.toList(),
                    selectedRequestId = report?.record?.request?.id,
                    reportComplaint = report?.record?.request?.complaint,
                    reportPeriod = report?.let { reportPeriod(it) },
                    back = back,
                )
            } else {
                DoctorCostScreen(settings.serverUrl, settings.aiToken, back, report)
            }
        }
        else -> NothingOpen(emptyActions)
    }
}

@Composable
private fun DoctorCostScreen(serverUrl: String?, aiToken: String?, back: BackAction?, report: Report?) {
    val url = serverUrl?.trim()?.takeIf { it.isNotEmpty() }
    var status by remember(url, aiToken) { mutableStateOf<AiStatus?>(null) }
    LaunchedEffect(url, aiToken) {
        if (url != null) {
            status = try {
                val http = HttpClient()
                try {
                    AiClient(url, http, token = aiToken).status()
                } finally {
                    http.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }
    // Reports do not carry an AI usage ledger yet; show the captured local run until they do.
    AiCostScreen(
        status = status,
        usage = DemoAi.usage,
        selectedRequestId = report?.record?.request?.id,
        reportComplaint = report?.record?.request?.complaint,
        reportPeriod = report?.let { reportPeriod(it) },
        back = back,
        demoCapture = true,
    )
}

/** Nothing selected: a quiet centered message, as in macOS split views. */
@Composable
private fun NothingOpen(actions: @Composable () -> Unit) {
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
            actions()
        }
    }
}

@Composable
private fun reportPeriod(report: Report): String? {
    val request = report.record.request
    val start = runCatching { LocalDate.parse(request.startDate) }.getOrNull() ?: return null
    return dateRange(start, lastDay(start, request.days))
}

/** What the visit screen needs from the server: the AI summary of [report], its progress, and how to ask for it. */
@Composable
private fun visitAi(state: ShellState, report: Report): VisitAi? {
    if (state.server == null) return null
    val scope = rememberCoroutineScope()
    return VisitAi(
        result = state.aiResults[report.id],
        busy = state.aiBusy[report.id] == true,
        failed = state.aiFailed[report.id] == true,
        onWrite = { scope.launch { state.summarize(report) } },
    )
}
