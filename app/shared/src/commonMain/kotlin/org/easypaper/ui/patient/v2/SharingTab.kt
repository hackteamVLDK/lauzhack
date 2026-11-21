package org.easypaper.ui.patient.v2

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.easypaper.model.EasyPaperJson
import org.easypaper.model.PatientRecord
import org.easypaper.ui.components.*
import org.easypaper.ui.format.dateRange
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.stringResource

fun sharedHistory(value: String?): List<String> = runCatching {
    EasyPaperJson.decodeFromString(ListSerializer(String.serializer()), value ?: "[]")
}.getOrDefault(emptyList())

fun PatientSettings.addSharedHistory(line: String) {
    // Desktop Preferences limits one value to 8192 characters. Keep a bounded local history.
    val lines = (listOf(line.take(500)) + sharedHistory(history.value)).take(20).toMutableList()
    var encoded = EasyPaperJson.encodeToString(ListSerializer(String.serializer()), lines)
    while (encoded.length > 6000 && lines.size > 1) {
        lines.removeAt(lines.lastIndex)
        encoded = EasyPaperJson.encodeToString(ListSerializer(String.serializer()), lines)
    }
    history.update(encoded)
}

@Composable
fun SharingTab(records: List<PatientRecord>, settings: PatientSettings, today: LocalDate,
               onProfile: () -> Unit, onDeveloper: () -> Unit, onRequest: (String) -> Unit,
               onInvite: () -> Unit, onShare: () -> Unit) {
    val doctors = records.filter { it.request.id != PERSONAL_REQUEST_ID }.sortedWith(
        compareByDescending<PatientRecord> { it.requestPeriod()?.contains(today) == true }.thenByDescending { it.request.createdAt })
    LargeTitleScaffold(stringResource(Res.string.p2_sharing), bottomInset = false,
        actions = { AvatarButton(initials(settings.name.value), onProfile) }, onTitleLongPress = onDeveloper) {
        item { PatientMessage(stringResource(Res.string.p2_requests)) }
        if (doctors.isEmpty()) item { GroupedSection { PatientMessage(stringResource(Res.string.p2_no_requests)) } }
        doctors.forEach { record -> item(key = record.request.id) {
            PlanCard(record, today, onClick = { onRequest(record.request.id) })
            Spacer(Modifier.height(EasyPaperTheme.spacing.s))
        } }
        item {
            PlainButton(stringResource(Res.string.p2_add_request), onInvite)
            Spacer(Modifier.height(EasyPaperTheme.spacing.sectionGap))
            ProminentButton(stringResource(Res.string.p2_share), onShare, Modifier.fillMaxWidth(), enabled = records.isNotEmpty())
            Spacer(Modifier.height(EasyPaperTheme.spacing.sectionGap))
            GroupedSection(header = stringResource(Res.string.p2_shared)) {
                val history = sharedHistory(settings.history.value)
                if (history.isEmpty()) PatientMessage(stringResource(Res.string.p2_no_shared))
                history.forEach { ListRow(it, chevron = false) }
            }
        }
    }
}

@Composable
fun WhatToShareSheet(visible: Boolean, records: List<PatientRecord>, today: LocalDate, timeZone: TimeZone,
                     onDismiss: () -> Unit, onNext: (ShareSelection) -> Unit) {
    val active = records.activeDoctor(today)
    var sourceId by remember(visible) { mutableStateOf(active?.request?.id ?: PERSONAL_REQUEST_ID) }
    val source = records.firstOrNull { it.request.id == sourceId } ?: records.firstOrNull()
    var periodChoice by remember(visible, sourceId) { mutableIntStateOf(if (source?.request?.id == active?.request?.id && active != null) 0 else 1) }
    var selected by remember(visible) { mutableStateOf(Category.entries.toSet()) }
    var photos by remember(visible) { mutableStateOf(true) }
    val period = if (periodChoice == 0) source?.requestPeriod() ?: recentPeriod(today, 7)
        else recentPeriod(today, when (periodChoice) { 2 -> 14; 3 -> 30; else -> 7 })
    val selection = source?.let { selectToShare(it, selected, period, photos, timeZone) }
    Sheet(visible, stringResource(Res.string.p2_what_share), stringResource(Res.string.p2_cancel), onDismiss) {
        GroupedSection(header = stringResource(Res.string.p2_source)) {
            records.forEach { record ->
                val title = if (record.request.id == PERSONAL_REQUEST_ID) stringResource(Res.string.p2_personal_diary)
                    else record.request.doctorName + ": " + record.request.complaint
                ListRow(title, trailing = { IosSwitch(record.request.id == sourceId, { if (it) sourceId = record.request.id },
                    Modifier.semantics { contentDescription = title }) }, chevron = false)
            }
        }
        Spacer(Modifier.height(EasyPaperTheme.spacing.sectionGap))
        val labels = listOf(stringResource(Res.string.p2_request_period), stringResource(Res.string.p2_days7),
            stringResource(Res.string.p2_days14), stringResource(Res.string.p2_days30))
        val indices = if (source?.request?.id == PERSONAL_REQUEST_ID) listOf(1, 2, 3) else listOf(0, 1, 2, 3)
        ChoiceList(indices.map { labels[it] }, labels[periodChoice], onSelect = { periodChoice = labels.indexOf(it) })
        PatientMessage(dateRange(period.from, period.to))
        CategorySwitches(selected, { selected = it })
        GroupedSection {
            val photoLabel = stringResource(Res.string.p2_include_photos)
            ListRow(photoLabel, trailing = { IosSwitch(photos, { photos = it }, Modifier.semantics { contentDescription = photoLabel }) })
        }
        PatientMessage(stringResource(Res.string.p2_share_count, selection?.entryCount ?: 0, selection?.photoCount ?: 0))
        ProminentButton(stringResource(Res.string.p2_next), { selection?.let(onNext) }, Modifier.fillMaxWidth(),
            enabled = selection != null && selection.entryCount > 0)
    }
}

@Composable
fun HowToShareSheet(selection: ShareSelection?, visible: Boolean, onDismiss: () -> Unit, onQr: (PatientRecord) -> Unit) {
    OptionsSheet(visible, stringResource(Res.string.p2_how_share), listOf(
        SheetOption(stringResource(Res.string.p2_show_qr), stringResource(Res.string.p2_qr_body), Res.drawable.ic_qr_code_2,
            enabled = selection != null && selection.entryCount > 0, onClick = { selection?.let { onQr(it.record) } }),
        SheetOption(stringResource(Res.string.p2_nearby), stringResource(Res.string.p2_nearby_body), Res.drawable.ic_water_drop,
            enabled = false, badge = stringResource(Res.string.p2_later), onClick = {}),
        SheetOption(stringResource(Res.string.p2_save_encrypted), null, Res.drawable.ic_edit_note,
            enabled = false, badge = stringResource(Res.string.p2_later), onClick = {}),
    ), onDismiss)
}
