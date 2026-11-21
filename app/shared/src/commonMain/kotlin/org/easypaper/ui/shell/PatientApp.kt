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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.back
import easypaper.app.shared.generated.resources.day_of
import easypaper.app.shared.generated.resources.done
import easypaper.app.shared.generated.resources.ended_on
import easypaper.app.shared.generated.resources.entries
import easypaper.app.shared.generated.resources.footer_privacy
import easypaper.app.shared.generated.resources.history_empty_message
import easypaper.app.shared.generated.resources.history_empty_title
import easypaper.app.shared.generated.resources.ic_calendar_today
import easypaper.app.shared.generated.resources.ic_history
import easypaper.app.shared.generated.resources.ic_qr_code_2
import easypaper.app.shared.generated.resources.share_explain
import easypaper.app.shared.generated.resources.starts_on
import easypaper.app.shared.generated.resources.tab_history
import easypaper.app.shared.generated.resources.tab_share
import easypaper.app.shared.generated.resources.today
import easypaper.app.shared.generated.resources.today_empty_message
import easypaper.app.shared.generated.resources.today_empty_title
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.easypaper.model.CareRequest
import org.easypaper.model.PatientRecord
import org.easypaper.ui.catalog.DesignCatalog
import org.easypaper.ui.components.BackAction
import org.easypaper.ui.components.CategoryRow
import org.easypaper.ui.components.EmptyState
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.LargeTitleScaffold
import org.easypaper.ui.components.ListRow
import org.easypaper.ui.components.NavigationBar
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.components.Sheet
import org.easypaper.ui.components.TabBar
import org.easypaper.ui.components.TabItem
import org.easypaper.ui.components.category
import org.easypaper.ui.format.RequestDay
import org.easypaper.ui.format.longDate
import org.easypaper.ui.format.requestDay
import org.easypaper.ui.format.today
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Screens of the patient app. Each tab keeps its own stack, as on iOS. */
sealed interface PatientScreen {
    data object Today : PatientScreen
    data object History : PatientScreen
    data object Share : PatientScreen
    data object DesignCatalog : PatientScreen
    data object Developer : PatientScreen
}

private val TAB_ROOTS = listOf(PatientScreen.Today, PatientScreen.History, PatientScreen.Share)

/**
 * The patient app (Android, Desktop): tab bar Today / History / Share. Today and History are filled
 * by task 4, Share by task 30; this shell gives them their place, titles and navigation.
 */
@Composable
fun PatientApp(state: ShellState, settings: ShellSettings, onSwitchRole: (() -> Unit)? = null) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val navigators = remember { TAB_ROOTS.map { Navigator<PatientScreen>(it) } }
    val navigator = navigators[tab]
    NavigatorBackHandler(navigator)
    val openDeveloper = { navigator.presentSheet(PatientScreen.Developer) }

    Box(Modifier.fillMaxSize().background(EasyPaperTheme.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (navigator.current) {
                    PatientScreen.Today -> TodayScreen(state.patientRecord, openDeveloper)
                    PatientScreen.History -> HistoryScreen(state.patientRecord, openDeveloper)
                    PatientScreen.Share -> ShareScreen(openDeveloper)
                    PatientScreen.DesignCatalog -> CatalogScreen(settings, BackAction(tabTitle(tab), navigator::back))
                    PatientScreen.Developer -> Unit // shown as a sheet
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

@Composable
private fun tabTitle(tab: Int): String = stringResource(
    when (tab) {
        0 -> Res.string.today
        1 -> Res.string.tab_history
        else -> Res.string.tab_share
    },
)

@Composable
private fun TodayScreen(record: PatientRecord?, onDeveloper: () -> Unit) {
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    val today = remember { today() }
    LargeTitleScaffold(
        title = stringResource(Res.string.today),
        onTitleLongPress = onDeveloper,
        bottomInset = false,
        header = {
            Text(longDate(today), style = type.subheadline, color = colors.secondaryLabel)
            record?.request?.let { requestLine(it, today) }?.let { Text(it, style = type.subheadline, color = colors.secondaryLabel) }
        },
    ) {
        if (record == null) {
            item { EmptyState(stringResource(Res.string.today_empty_title), stringResource(Res.string.today_empty_message)) }
        } else {
            item {
                // Task 4 turns each row into the widget the patient fills in.
                GroupedSection(footer = stringResource(Res.string.footer_privacy)) {
                    record.request.widgets.forEachIndexed { index, widget ->
                        if (index > 0) RowSeparator()
                        CategoryRow(widget.category, widget.title)
                    }
                }
            }
        }
    }
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

@OptIn(ExperimentalTime::class)
@Composable
private fun HistoryScreen(record: PatientRecord?, onDeveloper: () -> Unit) {
    val timeZone = remember { TimeZone.currentSystemDefault() }
    LargeTitleScaffold(title = stringResource(Res.string.tab_history), onTitleLongPress = onDeveloper, bottomInset = false) {
        if (record == null || record.entries.isEmpty()) {
            item { EmptyState(stringResource(Res.string.history_empty_title), stringResource(Res.string.history_empty_message)) }
        } else {
            // Task 4 opens each day with its entries; the shell lists the days, newest first.
            val days = record.entries.groupBy { Instant.fromEpochMilliseconds(it.at).toLocalDateTime(timeZone).date }
                .toList().sortedByDescending { it.first }
            item {
                GroupedSection {
                    days.forEachIndexed { index, (date, entries) ->
                        if (index > 0) RowSeparator(EasyPaperTheme.spacing.m)
                        ListRow(longDate(date), subtitle = pluralStringResource(Res.plurals.entries, entries.size, entries.size))
                    }
                }
            }
        }
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
