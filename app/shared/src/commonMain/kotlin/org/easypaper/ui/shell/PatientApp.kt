package org.easypaper.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.back
import easypaper.app.shared.generated.resources.cancel
import easypaper.app.shared.generated.resources.day_of
import easypaper.app.shared.generated.resources.done
import easypaper.app.shared.generated.resources.ended_on
import easypaper.app.shared.generated.resources.ic_calendar_today
import easypaper.app.shared.generated.resources.ic_history
import easypaper.app.shared.generated.resources.ic_qr_code_2
import easypaper.app.shared.generated.resources.share_explain
import easypaper.app.shared.generated.resources.starts_on
import easypaper.app.shared.generated.resources.tab_history
import easypaper.app.shared.generated.resources.tab_share
import easypaper.app.shared.generated.resources.today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.diary.EntryProblem
import org.easypaper.model.CareRequest
import org.easypaper.model.Entry
import org.easypaper.model.PatientRecord
import org.easypaper.model.Water
import org.easypaper.model.WidgetSpec
import org.easypaper.ui.catalog.DesignCatalog
import org.easypaper.ui.components.BackAction
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.LargeTitleScaffold
import org.easypaper.ui.components.NavigationBar
import org.easypaper.ui.components.PushNavigation
import org.easypaper.ui.components.Sheet
import org.easypaper.ui.components.TabBar
import org.easypaper.ui.components.TabItem
import org.easypaper.ui.format.RequestDay
import org.easypaper.ui.format.longDate
import org.easypaper.ui.format.requestDay
import org.easypaper.ui.patient.DayScreen
import org.easypaper.ui.patient.EntryForm
import org.easypaper.ui.patient.HistoryScreen
import org.easypaper.ui.patient.TodayActions
import org.easypaper.ui.patient.TodayScreen
import org.easypaper.ui.patient.WATER_STEP_ML
import org.easypaper.ui.patient.newEntryId
import org.easypaper.ui.patient.rememberToday
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Screens of the patient app. Each tab keeps its own stack, as on iOS. */
sealed interface PatientScreen {
    data object Today : PatientScreen
    data object History : PatientScreen
    data object Share : PatientScreen
    data object DesignCatalog : PatientScreen
    data object Developer : PatientScreen
    data class AddEntry(val widgetId: String) : PatientScreen
    data class Day(val date: LocalDate) : PatientScreen
}

private val TAB_ROOTS = listOf(PatientScreen.Today, PatientScreen.History, PatientScreen.Share)

@OptIn(ExperimentalTime::class)
private fun now(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * The patient app (Android, Desktop): tab bar Today / History / Share. Entries go through the
 * patient repository of [state] (checked before saving); Share is filled by task 30.
 */
@Composable
fun PatientApp(state: ShellState, settings: ShellSettings, onSwitchRole: (() -> Unit)? = null) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val navigators = remember { TAB_ROOTS.map { Navigator<PatientScreen>(it) } }
    val navigator = navigators[tab]
    NavigatorBackHandler(navigator)
    val openDeveloper = { navigator.presentSheet(PatientScreen.Developer) }
    val records by state.repository.records.collectAsState()
    val record = records.current()
    val scope = rememberCoroutineScope()
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = rememberToday()

    val actions = TodayActions(
        add = { widgetId -> navigator.presentSheet(PatientScreen.AddEntry(widgetId)) },
        addWater = { widgetId ->
            record?.let { r -> scope.launch { state.repository.add(r.request.id, Entry(newEntryId(), widgetId, now(), Water(WATER_STEP_ML))) } }
        },
        removeEntry = { entryId -> record?.let { r -> scope.launch { state.repository.removeEntry(r.request.id, entryId) } } },
    )

    Box(Modifier.fillMaxSize().background(EasyPaperTheme.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            key(tab) {
                PushNavigation(navigator.stack, Modifier.weight(1f).fillMaxWidth()) { screen ->
                    when (screen) {
                        PatientScreen.Today -> TodayScreen(
                            record, today, timeZone,
                            title = stringResource(Res.string.today),
                            header = { TodayHeader(record, today) },
                            onDeveloper = openDeveloper,
                            actions = actions,
                        )
                        PatientScreen.History -> HistoryScreen(
                            record, timeZone, stringResource(Res.string.tab_history), openDeveloper,
                            onOpenDay = { navigator.push(PatientScreen.Day(it)) },
                        )
                        is PatientScreen.Day -> record?.let { r ->
                            DayScreen(r, screen.date, timeZone, BackAction(tabTitle(tab), navigator::back),
                                onDelete = { entryId -> scope.launch { state.repository.removeEntry(r.request.id, entryId) } })
                        }
                        PatientScreen.Share -> ShareScreen(openDeveloper)
                        PatientScreen.DesignCatalog -> CatalogScreen(settings, BackAction(tabTitle(tab), navigator::back))
                        PatientScreen.Developer, is PatientScreen.AddEntry -> Unit // shown as sheets
                    }
                }
            }
            TabBar(
                items = listOf(
                    TabItem(stringResource(Res.string.today), Res.drawable.ic_calendar_today),
                    TabItem(stringResource(Res.string.tab_history), Res.drawable.ic_history),
                    TabItem(stringResource(Res.string.tab_share), Res.drawable.ic_qr_code_2),
                ),
                selected = tab,
                onSelect = { index -> if (index == tab) navigators[index].popToRoot() else tab = index },
            )
        }
        EntrySheet(state, record, navigator, scope)
        Sheet(
            visible = navigator.sheet == PatientScreen.Developer,
            title = developerTitle(),
            doneLabel = stringResource(Res.string.done),
            onDismiss = navigator::dismissSheet,
        ) {
            DeveloperMenuContent(
                state = state,
                settings = settings,
                onOpenCatalog = { navigator.push(PatientScreen.DesignCatalog) },
                onSwitchRole = onSwitchRole,
                onDone = navigator::dismissSheet,
            )
        }
    }
}

/** The form of one widget in a sheet; closes after a successful save, shows input problems otherwise. */
@Composable
private fun EntrySheet(state: ShellState, record: PatientRecord?, navigator: Navigator<PatientScreen>, scope: CoroutineScope) {
    val open = navigator.sheet as? PatientScreen.AddEntry
    val widget = open?.let { s -> record?.request?.widgets?.firstOrNull { it.id == s.widgetId } }
    // Keep the last widget while the sheet slides out.
    var shown by remember { mutableStateOf<WidgetSpec?>(null) }
    if (widget != null) shown = widget
    var problems by remember(open) { mutableStateOf(emptyList<EntryProblem>()) }
    var saving by remember(open) { mutableStateOf(false) }
    Sheet(
        visible = widget != null,
        title = shown?.title.orEmpty(),
        doneLabel = stringResource(Res.string.cancel),
        onDismiss = navigator::dismissSheet,
    ) {
        val form = shown ?: return@Sheet
        key(open) {
            EntryForm(form, problems, saving) { payload ->
                val requestId = record?.request?.id ?: return@EntryForm
                saving = true
                scope.launch {
                    val result = try {
                        state.repository.add(requestId, Entry(newEntryId(), form.id, now(), payload))
                    } catch (e: IllegalArgumentException) {
                        listOf(EntryProblem.UNKNOWN_WIDGET) // shown as "not saved, try again"
                    }
                    saving = false
                    if (result.isEmpty()) navigator.dismissSheet() else problems = result
                }
            }
        }
    }
}

@Composable
private fun tabTitle(tab: Int): String = stringResource(
    when (tab) {
        0 -> Res.string.today
        1 -> Res.string.tab_history
        else -> Res.string.tab_share
    },
)

@Composable
private fun TodayHeader(record: PatientRecord?, today: LocalDate) {
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    Text(longDate(today), style = type.subheadline, color = colors.secondaryLabel)
    record?.request?.let { requestLine(it, today) }?.let { Text(it, style = type.subheadline, color = colors.secondaryLabel) }
}

/** "Day 3 of 7 for Dr. Weber", or when the request starts / ended. Null for a malformed start date. */
@Composable
private fun requestLine(request: CareRequest, today: LocalDate): String? {
    val start = runCatching { LocalDate.parse(request.startDate) }.getOrNull() ?: return null
    return when (val day = requestDay(today, start, request.days)) {
        is RequestDay.Running -> stringResource(Res.string.day_of, day.day, day.of, request.doctorName)
        is RequestDay.NotStarted -> stringResource(Res.string.starts_on, longDate(day.startsOn))
        is RequestDay.Finished -> stringResource(Res.string.ended_on, longDate(day.endedOn))
    }
}

@Composable
private fun ShareScreen(onDeveloper: () -> Unit) {
    LargeTitleScaffold(title = stringResource(Res.string.tab_share), onTitleLongPress = onDeveloper, bottomInset = false) {
        item {
            // Task 30 adds the button that encrypts the report and shows the QR.
            GroupedSection {
                Text(
                    stringResource(Res.string.share_explain),
                    style = EasyPaperTheme.typography.body,
                    color = EasyPaperTheme.colors.label,
                    modifier = Modifier.padding(EasyPaperTheme.spacing.m),
                )
            }
        }
    }
}

@Composable
internal fun CatalogScreen(settings: ShellSettings, back: BackAction) {
    val systemDark = isSystemInDarkTheme()
    Column(Modifier.fillMaxSize().background(EasyPaperTheme.colors.background)) {
        NavigationBar(back)
        Box(Modifier.weight(1f)) {
            DesignCatalog(dark = settings.dark ?: systemDark, onDarkChange = { settings.dark = it })
        }
    }
}

/** Shared label of the back button. */
@Composable
internal fun backLabel(): String = stringResource(Res.string.back)
