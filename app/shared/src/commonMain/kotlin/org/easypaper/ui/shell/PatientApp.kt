package org.easypaper.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import easypaper.app.shared.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.easypaper.diary.EntryProblem
import org.easypaper.model.*
import org.easypaper.ui.catalog.DesignCatalog
import org.easypaper.ui.components.*
import org.easypaper.ui.handover.ShareScreen
import org.easypaper.ui.handover.relayUrl
import org.easypaper.ui.patient.*
import org.easypaper.ui.patient.medications.MedicationsScreen
import org.easypaper.ui.patient.v2.*
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

sealed interface PatientScreen {
    data object Summary : PatientScreen
    data object Sharing : PatientScreen
    data object Browse : PatientScreen
    data object DesignCatalog : PatientScreen
    data object Developer : PatientScreen
    data object Profile : PatientScreen
    data object Pinned : PatientScreen
    data object OpenInvite : PatientScreen
    data object WhatToShare : PatientScreen
    data object HowToShare : PatientScreen
    data class CategoryPage(val category: Category) : PatientScreen
    data class AllData(val category: Category) : PatientScreen
    data class Plan(val requestId: String) : PatientScreen
    data class AddEntry(val requestId: String, val widgetId: String) : PatientScreen
    data class Medicines(val requestId: String, val widgetId: String) : PatientScreen
    data class EntryView(val requestId: String, val entryId: String) : PatientScreen
    data class DeleteEntry(val requestId: String, val entryId: String) : PatientScreen
    data class ShareReport(val record: PatientRecord) : PatientScreen
}

private val TAB_ROOTS = listOf(PatientScreen.Summary, PatientScreen.Sharing, PatientScreen.Browse)

@Composable
fun PatientApp(state: ShellState, settings: ShellSettings, onSwitchRole: (() -> Unit)? = null,
               patientSettings: PatientSettings = rememberPatientSettings()) {
    val patient = patientSettings
    val language = supportedLanguage(patient.language.value ?: Locale.current.language)
    PatientLanguage(language) { PatientContent(state, settings, patient, language, onSwitchRole) }
}

@Composable
private fun PatientContent(state: ShellState, settings: ShellSettings, patient: PatientSettings, language: String,
                           onSwitchRole: (() -> Unit)?) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val navigators = remember { TAB_ROOTS.map { Navigator<PatientScreen>(it) } }
    val navigator = navigators[tab]
    val records by state.repository.records.collectAsState()
    val scope = rememberCoroutineScope()
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = rememberToday()
    val titles = Category.entries.associateWith { categoryTitle(it) }
    val completed = patient.onboarding.value == "true"
    var finishing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<ShareSelection?>(null) }
    val back = BackAction(stringResource(Res.string.p2_back)) { navigator.back() }
    val openDeveloper = { navigator.presentSheet(PatientScreen.Developer) }
    val openProfile = { navigator.presentSheet(PatientScreen.Profile) }
    val openInvite = { navigator.presentSheet(PatientScreen.OpenInvite) }
    val openCategory: (Category) -> Unit = { navigator.push(PatientScreen.CategoryPage(it)) }
    val openEntry: (OwnedEntry) -> Unit = { navigator.presentSheet(PatientScreen.EntryView(it.record.request.id, it.entry.id)) }

    // Idempotent even if the first repository refresh is still running.
    LaunchedEffect(completed, state.repository) {
        if (completed) try {
            state.repository.ensurePersonal(personalRequest(today, language, Clock.System.now().toEpochMilliseconds(), titles))
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { failed = true }
    }
    LaunchedEffect(state.pendingInvite, completed) {
        if (completed && state.pendingInvite != null) {
            tab = 1
            navigators[1].presentSheet(PatientScreen.OpenInvite)
        }
    }

    if (!completed) {
        Onboarding(patient, today, finishing, failed) { invite ->
            finishing = true; failed = false
            scope.launch {
                try {
                    state.repository.ensurePersonal(personalRequest(today, language, Clock.System.now().toEpochMilliseconds(), titles))
                    patient.onboarding.update("true")
                    if (invite) { tab = 1; navigators[1].presentSheet(PatientScreen.OpenInvite) }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) { failed = true
                } finally { finishing = false }
            }
        }
        return
    }
    NavigatorBackHandler(navigator)

    // Lambdas, not local funs: a local fun inside this composable made the compiler emit an illegal JVM method
    // name ("<anonymous>") and the class failed to load at runtime.
    val addWater: (PatientRecord, String) -> Unit = { record, widgetId ->
        scope.launch {
            try {
                val problems = state.repository.add(record.request.id,
                    Entry(newEntryId(), widgetId, Clock.System.now().toEpochMilliseconds(), Water(WATER_STEP_ML)))
                if (problems.isNotEmpty()) failed = true
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { failed = true }
        }
    }
    val addCategory: (Category, Boolean) -> Unit = addCategory@{ category, quick ->
        val active = records.activeDoctor(today)
        val record = active?.takeIf { it.request.widgets.any { widget -> widget.category == category } }
            ?: records.firstOrNull { it.request.id == PERSONAL_REQUEST_ID }
        val widget = record?.request?.widgets?.firstOrNull { it.category == category }
        if (record == null || widget == null) { failed = true; return@addCategory }
        when {
            category == Category.Medicines -> navigator.push(PatientScreen.Medicines(record.request.id, widget.id))
            category == Category.Water && quick -> addWater(record, widget.id)
            else -> navigator.presentSheet(PatientScreen.AddEntry(record.request.id, widget.id))
        }
    }

    Box(Modifier.fillMaxSize().background(EasyPaperTheme.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            key(tab) {
                PushNavigation(navigator.stack, Modifier.weight(1f).fillMaxWidth()) { screen ->
                    when (screen) {
                        PatientScreen.Summary -> SummaryTab(records, patient, today, timeZone, openProfile, openDeveloper,
                            { navigator.push(PatientScreen.Plan(it)) }, openCategory, { addCategory(it, true) },
                            { navigator.presentSheet(PatientScreen.Pinned) })
                        PatientScreen.Sharing -> SharingTab(records, patient, today, openProfile, openDeveloper,
                            { navigator.push(PatientScreen.Plan(it)) }, openInvite,
                            { navigator.presentSheet(PatientScreen.WhatToShare) })
                        PatientScreen.Browse -> BrowseTab(patient, openProfile, openDeveloper, openCategory)
                        is PatientScreen.CategoryPage -> CategoryDetail(screen.category, records, today, timeZone, back,
                            { addCategory(screen.category, false) }, { navigator.push(PatientScreen.AllData(screen.category)) }, openEntry)
                        is PatientScreen.AllData -> AllCategoryData(screen.category, records, timeZone, back, openEntry,
                            { navigator.presentSheet(PatientScreen.DeleteEntry(it.record.request.id, it.entry.id)) })
                        is PatientScreen.Plan -> {
                            val record = records.firstOrNull { it.request.id == screen.requestId }
                            Column {
                                NavigationBar(back)
                                Box(Modifier.weight(1f)) {
                                    TodayScreen(record, today, timeZone, stringResource(Res.string.p2_plan), header = {}, onDeveloper = openDeveloper,
                                        actions = TodayActions(
                                            add = { navigator.presentSheet(PatientScreen.AddEntry(screen.requestId, it)) },
                                            addWater = { id -> record?.let { addWater(it, id) } },
                                            removeEntry = { navigator.presentSheet(PatientScreen.DeleteEntry(screen.requestId, it)) },
                                            openMedicines = { navigator.push(PatientScreen.Medicines(screen.requestId, it)) },
                                        ), onOpenInvite = openInvite)
                                }
                            }
                        }
                        is PatientScreen.Medicines -> {
                            val record = records.firstOrNull { it.request.id == screen.requestId }
                            val widget = record?.request?.widgets?.filterIsInstance<MedicationsWidget>()?.firstOrNull { it.id == screen.widgetId }
                            if (record != null && widget != null) MedicationsScreen(state.repository, record, widget, back)
                        }
                        is PatientScreen.ShareReport -> {
                            val historyPattern = stringResource(Res.string.p2_history_line, screen.record.request.doctorName.ifBlank {
                                stringResource(Res.string.p2_personal_diary)
                            }, "%TIME%", screen.record.entries.size)
                            ShareScreen(state.repository, relayUrl(settings.serverUrl, defaultServerUrl()), openDeveloper,
                                selectedRecord = screen.record, back = back, onCreated = {
                                    val at = Clock.System.now().toEpochMilliseconds()
                                    val date = Entry("", "", at, Water(0)).localDate(timeZone)
                                    patient.addSharedHistory(historyPattern.replace("%TIME%", "$date ${timeOfDay(at, timeZone)}"))
                                })
                        }
                        PatientScreen.DesignCatalog -> CatalogScreen(settings, back)
                        else -> Unit
                    }
                }
            }
            TabBar(listOf(TabItem(stringResource(Res.string.p2_summary), Res.drawable.ic_calendar_today),
                TabItem(stringResource(Res.string.p2_sharing), Res.drawable.ic_qr_code_2),
                TabItem(stringResource(Res.string.p2_browse), Res.drawable.ic_checklist)), tab,
                { index -> if (index == tab) navigators[index].popToRoot() else tab = index })
        }
        PatientEntrySheet(state, records, navigator)
        ProfileSheet(navigator.sheet == PatientScreen.Profile, patient, state.repository, today, navigator::dismissSheet,
            onDeleted = { navigators.forEach { it.popToRoot() }; tab = 0; selection = null })
        Sheet(navigator.sheet == PatientScreen.Pinned, stringResource(Res.string.p2_edit_pinned), stringResource(Res.string.p2_done), navigator::dismissSheet) {
            CategorySwitches(patient.pinnedCategories, patient::pin)
        }
        Sheet(navigator.sheet == PatientScreen.OpenInvite, stringResource(Res.string.p2_add_request), stringResource(Res.string.p2_cancel),
            { state.pendingInvite = null; navigator.dismissSheet() }) {
            OpenInviteForm(state.pendingInvite) { request ->
                scope.launch {
                    try {
                        state.repository.start(request)
                        state.pendingInvite = null; navigator.dismissSheet()
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { failed = true }
                }
            }
        }
        Sheet(navigator.sheet == PatientScreen.Developer, developerTitle(), stringResource(Res.string.p2_done), navigator::dismissSheet) {
            DeveloperMenuContent(state, settings, { navigator.push(PatientScreen.DesignCatalog) }, onSwitchRole,
                navigator::dismissSheet, scope,
                onShowOnboarding = { patient.onboarding.update(null); navigators.forEach { it.popToRoot() } },
                onPatientClear = { patient.reset(); navigators.forEach { it.popToRoot() } })
        }
        WhatToShareSheet(navigator.sheet == PatientScreen.WhatToShare, records, today, timeZone, navigator::dismissSheet,
            { selection = it; navigator.presentSheet(PatientScreen.HowToShare) })
        HowToShareSheet(selection, navigator.sheet == PatientScreen.HowToShare, navigator::dismissSheet,
            { navigator.dismissSheet(); navigator.push(PatientScreen.ShareReport(it)) })
        val entryView = navigator.sheet as? PatientScreen.EntryView
        val viewedRecord = records.firstOrNull { it.request.id == entryView?.requestId }
        val viewedEntry = viewedRecord?.entries?.firstOrNull { it.id == entryView?.entryId }
        Sheet(entryView != null, stringResource(Res.string.p2_entry), stringResource(Res.string.p2_done), navigator::dismissSheet) {
            if (viewedEntry != null) {
                PatientMessage(patientEntryText(OwnedEntry(viewedRecord, viewedEntry)))
                PatientMessage(entryDate(OwnedEntry(viewedRecord, viewedEntry), timeZone))
                EntryPhoto(state.repository, viewedEntry)
            }
        }
        val deletion = navigator.sheet as? PatientScreen.DeleteEntry
        var deleting by remember(deletion) { mutableStateOf(false) }
        ConfirmSheet(deletion != null, stringResource(Res.string.p2_delete_entry), navigator::dismissSheet, onConfirm = {
            if (deletion != null) {
                deleting = true
                scope.launch {
                    try { state.repository.removeEntry(deletion.requestId, deletion.entryId); navigator.dismissSheet()
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { failed = true
                    } finally { deleting = false }
                }
            }
        }, busy = deleting)
        Sheet(failed, stringResource(Res.string.p2_error), stringResource(Res.string.p2_done), { failed = false }) {
            PatientMessage(stringResource(Res.string.p2_error), error = true)
        }
    }
}

@Composable
private fun PatientEntrySheet(state: ShellState, records: List<PatientRecord>, navigator: Navigator<PatientScreen>) {
    val open = navigator.sheet as? PatientScreen.AddEntry
    val record = records.firstOrNull { it.request.id == open?.requestId }
    val widget = record?.request?.let { displayRequest(it) }?.widgets?.firstOrNull { it.id == open?.widgetId }
    var shown by remember { mutableStateOf<WidgetSpec?>(null) }
    if (widget != null) shown = widget
    val scope = rememberCoroutineScope()
    var problems by remember(open) { mutableStateOf(emptyList<EntryProblem>()) }
    var saving by remember(open) { mutableStateOf(false) }
    var water by remember(open) { mutableStateOf("250") }
    Sheet(widget != null, shown?.let { categoryTitle(it.category) }.orEmpty(), stringResource(Res.string.p2_cancel), navigator::dismissSheet) {
        // No early return inside key {}: 'return@key' in a composable lambda made the compiler emit an illegal
        // JVM method ($$$$$NON_LOCAL_RETURN$$$$$.<anonymous>) and the patient app failed to load.
        key(open) {
            val form = shown
            if (form != null) {
                val save: (EntryPayload, ByteArray?) -> Unit = { payload, photo ->
                    if (open != null && !saving) {
                        saving = true
                        scope.launch {
                            try {
                                val result = state.repository.add(open.requestId,
                                    Entry(newEntryId(), form.id, Clock.System.now().toEpochMilliseconds(), payload), photo)
                                if (result.isEmpty()) navigator.dismissSheet() else problems = result
                            } catch (e: CancellationException) { throw e
                            } catch (e: Exception) { problems = listOf(EntryProblem.UNKNOWN_WIDGET)
                            } finally { saving = false }
                        }
                    }
                }
                if (form is WaterWidget) {
                    GroupedSection(header = stringResource(Res.string.p2_water_amount)) {
                        TextFieldRow(water, { water = it.filter(Char::isDigit).take(4) }, stringResource(Res.string.p2_water_amount))
                    }
                    if (problems.isNotEmpty()) PatientMessage(stringResource(Res.string.p2_error), error = true)
                    ProminentButton(stringResource(if (saving) Res.string.p2_working else Res.string.p2_add_data),
                        { save(Water(water.toIntOrNull() ?: 0), null) }, enabled = !saving && (water.toIntOrNull() ?: 0) > 0)
                } else EntryForm(form, problems, saving, save)
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
