package org.easypaper.ui.patient.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
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
    val spacing = EasyPaperTheme.spacing
    val doctors = records.filter { it.request.id != PERSONAL_REQUEST_ID }.sortedWith(
        compareByDescending<PatientRecord> { it.requestPeriod()?.contains(today) == true }.thenByDescending { it.request.createdAt })
    LargeTitleScaffold(stringResource(Res.string.p2_sharing), bottomInset = false,
        actions = { AvatarButton(initials(settings.name.value), onProfile) }, onTitleLongPress = onDeveloper) {
        item { SharingIntroduction() }
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = spacing.sectionGap), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = spacing.patientMaxWidth).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    CapsuleButton(stringResource(Res.string.p2_sharing_doctor_title), onShare,
                        prominent = true, enabled = records.isNotEmpty())
                    TintedButton(stringResource(Res.string.p2_add_request), onInvite,
                        Modifier.fillMaxWidth().heightIn(min = spacing.prominentButtonHeight))
                }
            }
        }
        item {
            Text(stringResource(Res.string.p2_requests), style = EasyPaperTheme.typography.title2,
                color = EasyPaperTheme.colors.label,
                modifier = Modifier.padding(bottom = spacing.s).semantics { heading() })
        }
        if (doctors.isEmpty()) item { GroupedSection { PatientMessage(stringResource(Res.string.p2_no_requests)) } }
        doctors.forEach { record -> item(key = record.request.id) {
            PlanCard(record, today, onClick = { onRequest(record.request.id) })
            Spacer(Modifier.height(spacing.s))
        } }
        item {
            Spacer(Modifier.height(spacing.sectionGap))
            GroupedSection(header = stringResource(Res.string.p2_shared)) {
                val history = sharedHistory(settings.history.value)
                if (history.isEmpty()) PatientMessage(stringResource(Res.string.p2_no_shared))
                history.forEach { ListRow(it, chevron = false) }
            }
        }
    }
}

@Composable
private fun SharingIntroduction() {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    Column(Modifier.fillMaxWidth().clip(EasyPaperTheme.shapes.widget).background(colors.cell).padding(spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xl)) {
        Icon(painterResource(Res.drawable.ic_share_doctor), null, Modifier.size(spacing.sharingHeroIcon), tint = colors.tint)
        WordFitText(stringResource(Res.string.p2_sharing_doctor_title), style = EasyPaperTheme.typography.title1,
            color = colors.label, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        SharingBenefit(Res.drawable.ic_calendar_today, stringResource(Res.string.p2_sharing_decide_title),
            stringResource(Res.string.p2_sharing_decide_body))
        SharingBenefit(Res.drawable.ic_checklist, stringResource(Res.string.p2_sharing_summary_title),
            stringResource(Res.string.p2_sharing_summary_body))
        SharingBenefit(Res.drawable.ic_check_circle, stringResource(Res.string.p2_sharing_private_title),
            stringResource(Res.string.p2_sharing_private_body))
    }
}

@Composable
private fun SharingBenefit(icon: DrawableResource, title: String, body: String) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.m), verticalAlignment = Alignment.Top) {
        Icon(painterResource(icon), null, Modifier.size(spacing.rowIcon), tint = colors.tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(title, style = EasyPaperTheme.typography.headline, color = colors.label)
            Text(body, style = EasyPaperTheme.typography.subheadline, color = colors.secondaryLabel)
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
        // One source at a time: a radio list (a switch would suggest several, and switching one off did nothing).
        PatientMessage(stringResource(Res.string.p2_source))
        val personal = stringResource(Res.string.p2_personal_diary)
        val byId = records.associateBy { it.request.id }
        // Picked by request id: two requests with the same doctor, complaint and period stay separate choices.
        ChoiceList(records.map { it.request.id }, source?.request?.id, label = { id ->
            val record = byId.getValue(id)
            if (id == PERSONAL_REQUEST_ID) personal
            else record.request.doctorName + ": " + record.request.complaint +
                (record.requestPeriod()?.let { ", " + dateRange(it.from, it.to) } ?: "")
        }, onSelect = { sourceId = it })
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
fun HowToShareSheet(selection: ShareSelection?, visible: Boolean, onDismiss: () -> Unit, onQr: (PatientRecord) -> Unit,
                    onCode: (PatientRecord) -> Unit, onFile: (PatientRecord) -> Unit) {
    OptionsSheet(visible, stringResource(Res.string.p2_how_share), listOf(
        SheetOption(stringResource(Res.string.p2_show_qr), stringResource(Res.string.p2_qr_body), Res.drawable.ic_qr_code_2,
            enabled = selection != null && selection.entryCount > 0, onClick = { selection?.let { onQr(it.record) } }),
        SheetOption(stringResource(Res.string.p2_code_title), stringResource(Res.string.p2_code_body), Res.drawable.ic_chat_bubble,
            enabled = selection != null && selection.entryCount > 0, onClick = { selection?.let { onCode(it.record) } }),
        SheetOption(stringResource(Res.string.p2_save_encrypted), stringResource(Res.string.p2_file_body), Res.drawable.ic_download,
            enabled = selection != null && selection.entryCount > 0, onClick = { selection?.let { onFile(it.record) } }),
    ), onDismiss)
}
