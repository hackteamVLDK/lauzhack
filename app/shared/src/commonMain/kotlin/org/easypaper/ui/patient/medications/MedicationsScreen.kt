package org.easypaper.ui.patient.medications

import easypaper.app.shared.generated.resources.med_not_in_list
import org.easypaper.scan.likelyName
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.cancel
import easypaper.app.shared.generated.resources.delete
import easypaper.app.shared.generated.resources.draft
import easypaper.app.shared.generated.resources.ic_add
import easypaper.app.shared.generated.resources.ic_pill
import easypaper.app.shared.generated.resources.ic_remove
import easypaper.app.shared.generated.resources.med_check
import easypaper.app.shared.generated.resources.med_dose
import easypaper.app.shared.generated.resources.med_empty
import easypaper.app.shared.generated.resources.med_empty_message
import easypaper.app.shared.generated.resources.med_footer
import easypaper.app.shared.generated.resources.med_found
import easypaper.app.shared.generated.resources.med_name
import easypaper.app.shared.generated.resources.med_not_found
import easypaper.app.shared.generated.resources.med_other
import easypaper.app.shared.generated.resources.med_reading
import easypaper.app.shared.generated.resources.med_scan
import easypaper.app.shared.generated.resources.med_take_now
import easypaper.app.shared.generated.resources.med_taken_today
import easypaper.app.shared.generated.resources.med_title
import easypaper.app.shared.generated.resources.med_type
import easypaper.app.shared.generated.resources.problem_empty
import easypaper.app.shared.generated.resources.save
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.easypaper.diary.EntryProblem
import org.easypaper.model.Medication
import org.easypaper.model.MedicationsWidget
import org.easypaper.model.PatientRecord
import org.easypaper.scan.MedicationDb
import org.easypaper.scan.readPhoto
import org.easypaper.scan.recognize
import org.easypaper.storage.PatientRepository
import org.easypaper.ui.capture.rememberPhotoSource
import org.easypaper.ui.components.BackAction
import org.easypaper.ui.components.ChoiceList
import org.easypaper.ui.components.DraftBadge
import org.easypaper.ui.components.EmptyState
import org.easypaper.ui.components.GrayButton
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.LargeTitleScaffold
import org.easypaper.ui.components.ListRow
import org.easypaper.ui.components.PlainButton
import org.easypaper.ui.components.ProminentButton
import org.easypaper.ui.components.RowSeparator
import org.easypaper.ui.components.Sheet
import org.easypaper.ui.components.TextAreaRow
import org.easypaper.ui.components.TintedButton
import org.easypaper.ui.format.today
import org.easypaper.ui.patient.timeOfDay
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** What the sheet shows: reading a photo, choosing among scan results, checking a draft, or typing a medicine. */
private sealed interface MedSheet {
    data object Reading : MedSheet
    data class Choose(val choices: List<Medication>) : MedSheet
    data class Check(val item: MedicineItem) : MedSheet
    data class Type(val code: String?, val unreadable: Boolean = false, val readName: String? = null) : MedSheet
}

/**
 * The patient's medicines (task 31): scan a package (camera or photo, recognised on the device without AI, #28),
 * confirm what was found with dose and frequency, or type it in; "Taken now" records an intake. A scan that was
 * not checked stays a draft. Facts only: no advice and no interaction warnings.
 */
@Composable
fun MedicationsScreen(
    repository: PatientRepository,
    record: PatientRecord,
    widget: MedicationsWidget,
    back: BackAction?,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val scope = rememberCoroutineScope()
    val requestId = record.request.id
    val items = medicinesFor(record, widget.id, today(), timeZone)
    val db = remember { MedicationDb() }
    var sheet by remember { mutableStateOf<MedSheet?>(null) }
    var problems by remember { mutableStateOf(emptyList<EntryProblem>()) }
    var saving by remember { mutableStateOf(false) }
    fun now() = Clock.System.now().toEpochMilliseconds()
    fun run(action: suspend () -> List<EntryProblem>) {
        saving = true
        scope.launch {
            problems = try { action() } finally { saving = false }
            if (problems.isEmpty()) sheet = null
        }
    }
    // a file that is not a photo: say so and offer typing, rather than a tap that seems to do nothing
    val photos = rememberPhotoSource(onError = { problems = emptyList(); sheet = MedSheet.Type(code = null, unreadable = true) }) { photo ->
        sheet = MedSheet.Reading
        scope.launch {
            // any failure of on-device recognition ends in typing, never in silence or a crash
            val reading = try { readPhoto(photo) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            val match = reading?.let { db.recognize(it) }
            val choices = match?.let { scanChoices(it, record.request.locale) }.orEmpty()
            sheet = when {
                choices.isNotEmpty() -> MedSheet.Choose(choices)
                reading == null -> MedSheet.Type(code = null, unreadable = true)
                // not in the local list: offer what was read on the box, for the patient to check
                else -> MedSheet.Type(match?.code?.gtin, unreadable = true, readName = reading.likelyName())
            }
        }
    }
    val scan = (photos.takePhoto ?: photos.pickPhoto)?.takeIf { widget.scan }

    Box(modifier.fillMaxSize()) {
        LargeTitleScaffold(title = widget.title.ifBlank { stringResource(Res.string.med_title) }, back = back) {
            item {
                if (items.isEmpty()) {
                    EmptyState(stringResource(Res.string.med_empty), stringResource(Res.string.med_empty_message))
                } else {
                    GroupedSection(footer = stringResource(Res.string.med_footer)) {
                        items.forEachIndexed { index, item ->
                            if (index > 0) RowSeparator(EasyPaperTheme.spacing.m)
                            MedicineRow(item, timeZone, saving,
                                check = { problems = emptyList(); sheet = MedSheet.Check(item) },
                                takeNow = { run { repository.takeNow(requestId, widget.id, item, now()) } })
                        }
                    }
                }
            }
            item {
                val spacing = EasyPaperTheme.spacing
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = spacing.m, vertical = spacing.sectionGap),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    if (scan != null) TintedButton(stringResource(Res.string.med_scan), onClick = scan, icon = Res.drawable.ic_pill)
                    GrayButton(stringResource(Res.string.med_type), onClick = { problems = emptyList(); sheet = MedSheet.Type(null) },
                        icon = Res.drawable.ic_add)
                }
            }
        }

        val open = sheet
        Sheet(
            visible = open != null,
            title = when (open) {
                is MedSheet.Choose -> stringResource(Res.string.med_found)
                is MedSheet.Check -> stringResource(Res.string.med_check)
                is MedSheet.Type -> stringResource(Res.string.med_type)
                else -> stringResource(Res.string.med_scan)
            },
            doneLabel = stringResource(Res.string.cancel),
            onDismiss = {
                // closing without checking keeps a sure scan (code found in the list) as a draft
                (open as? MedSheet.Choose)?.choices?.singleOrNull()?.let { draft ->
                    scope.launch { repository.addMedication(requestId, widget.id, draft, now()) }
                }
                sheet = null
            },
        ) {
            when (open) {
                MedSheet.Reading -> Hint(stringResource(Res.string.med_reading))
                is MedSheet.Choose -> ChooseForm(open.choices, problems, saving,
                    save = { chosen, dose -> run { repository.addMedication(requestId, widget.id, chosen.confirmedWith(dose), now()) } },
                    type = { problems = emptyList(); sheet = MedSheet.Type(null) })
                is MedSheet.Check -> CheckForm(open.item, problems, saving,
                    save = { dose -> run { repository.confirm(requestId, widget.id, open.item, dose, now()) } },
                    delete = { scope.launch { open.item.draftIds.forEach { repository.removeEntry(requestId, it) }; sheet = null } })
                is MedSheet.Type -> TypeForm(open.code != null || open.unreadable, open.readName, problems, saving) { name, dose ->
                    run { repository.addMedication(requestId, widget.id, typedMedication(name, dose, open.code), now()) }
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun MedicineRow(item: MedicineItem, timeZone: TimeZone, saving: Boolean, check: () -> Unit, takeNow: () -> Unit) {
    val taken = item.takenToday.joinToString(", ") { timeOfDay(it, timeZone) }
    ListRow(
        title = item.name,
        subtitle = listOfNotNull(item.dose?.takeIf { it !in item.name }, taken.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.med_taken_today, it) })
            .joinToString(" · ").ifEmpty { null },
        chevron = item.isDraft,
        onClick = if (item.isDraft) check else null,
        trailing = {
            if (item.isDraft) DraftBadge(stringResource(Res.string.draft))
            else TintedButton(stringResource(Res.string.med_take_now), onClick = takeNow, enabled = !saving)
        },
    )
}

@Composable
private fun ColumnScope.ChooseForm(
    choices: List<Medication>,
    problems: List<EntryProblem>,
    saving: Boolean,
    save: (Medication, String) -> Unit,
    type: () -> Unit,
) {
    var chosen by remember(choices) { mutableStateOf(choices.singleOrNull()) }
    var dose by remember(choices) { mutableStateOf("") }
    ChoiceList(choices.map { it.name }, chosen?.name, onSelect = { name -> chosen = choices.first { it.name == name } })
    DoseField(dose) { dose = it }
    Problems(problems)
    SaveButton(saving, enabled = chosen != null) { chosen?.let { save(it, dose) } }
    PlainButton(stringResource(Res.string.med_other), onClick = type, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ColumnScope.CheckForm(item: MedicineItem, problems: List<EntryProblem>, saving: Boolean, save: (String) -> Unit, delete: () -> Unit) {
    var dose by remember(item) { mutableStateOf(item.dose.orEmpty()) }
    GroupedSection { ListRow(item.name, subtitle = item.code, chevron = false) }
    DoseField(dose) { dose = it }
    Problems(problems)
    SaveButton(saving) { save(dose) }
    PlainButton(stringResource(Res.string.delete), onClick = delete, enabled = !saving, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ColumnScope.TypeForm(notRecognised: Boolean, readName: String?, problems: List<EntryProblem>, saving: Boolean, save: (String, String) -> Unit) {
    var name by remember(readName) { mutableStateOf(readName.orEmpty()) }
    var dose by remember { mutableStateOf("") }
    if (readName != null) Hint(stringResource(Res.string.med_not_in_list))
    else if (notRecognised) Hint(stringResource(Res.string.med_not_found))
    GroupedSection { TextAreaRow(name, { name = it }, placeholder = stringResource(Res.string.med_name)) }
    DoseField(dose) { dose = it }
    Problems(problems)
    SaveButton(saving, enabled = name.isNotBlank()) { save(name, dose) }
}

@Composable
private fun DoseField(dose: String, onChange: (String) -> Unit) {
    GroupedSection(Modifier.padding(top = EasyPaperTheme.spacing.l)) {
        TextAreaRow(dose, onChange, placeholder = stringResource(Res.string.med_dose))
    }
}

@Composable
private fun ColumnScope.SaveButton(saving: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Spacer(Modifier.height(EasyPaperTheme.spacing.xl))
    ProminentButton(stringResource(Res.string.save), onClick = onClick, enabled = enabled && !saving, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Hint(text: String) {
    val spacing = EasyPaperTheme.spacing
    Text(text, style = EasyPaperTheme.typography.subheadline, color = EasyPaperTheme.colors.secondaryLabel,
        modifier = Modifier.padding(horizontal = spacing.m, vertical = spacing.s).semantics { liveRegion = LiveRegionMode.Polite })
}

/** The only problem a medicine can have is an empty name; anything else is shown the same way. */
@Composable
private fun Problems(problems: List<EntryProblem>) {
    if (problems.isEmpty()) return
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    Row(Modifier.padding(start = spacing.m, end = spacing.m, top = spacing.s).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top) {
        Icon(painterResource(Res.drawable.ic_remove), contentDescription = null, tint = colors.destructive,
            modifier = Modifier.size(spacing.rowIcon).padding(end = spacing.xxs))
        Text(stringResource(Res.string.problem_empty), style = EasyPaperTheme.typography.subheadline, color = colors.destructive)
    }
}
