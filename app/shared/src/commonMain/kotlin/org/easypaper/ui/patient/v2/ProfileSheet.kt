package org.easypaper.ui.patient.v2

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.easypaper.storage.PatientRepository
import org.easypaper.storage.exportBackup
import org.easypaper.ui.components.*
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.stringResource

@Composable
expect fun rememberBackupSaver(onResult: (Boolean) -> Unit): (ByteArray) -> Unit

@Composable
fun ProfileSheet(visible: Boolean, settings: PatientSettings, repository: PatientRepository, today: LocalDate,
                 onDismiss: () -> Unit, onDeleted: () -> Unit) {
    var page by remember(visible) { mutableStateOf("profile") }
    var password by remember(visible) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saveBackup = rememberBackupSaver { success -> busy = false; saved = success }
    val title = stringResource(when (page) {
        "backup" -> Res.string.p2_export
        "delete" -> Res.string.p2_delete_all
        else -> Res.string.p2_profile
    })
    Sheet(visible, title, stringResource(when { busy -> Res.string.p2_working; page == "profile" -> Res.string.p2_done; else -> Res.string.p2_cancel }),
        onDismiss = { if (!busy) { if (page == "profile") onDismiss() else { page = "profile"; password = "" } } }) {
        when (page) {
            "backup" -> {
                PatientMessage(stringResource(Res.string.p2_backup_body))
                GroupedSection {
                    TextFieldRow(password, { password = it }, stringResource(Res.string.p2_backup_password), secret = true)
                }
                ProminentButton(stringResource(if (busy) Res.string.p2_working else Res.string.p2_backup_save), {
                    busy = true; failed = false; saved = false
                    val passphrase = password
                    scope.launch {
                        try {
                            val records = repository.records.value.toList()
                            val photos = records.flatMap { it.entries }.mapNotNull { it.photoReference }.distinct().mapNotNull { ref ->
                                repository.photo(ref)?.let { ref to it }
                            }.toMap()
                            val bytes = withContext(Dispatchers.Default) { exportBackup(records, photos, passphrase) }
                            password = ""
                            saveBackup(bytes)
                        } catch (e: CancellationException) { throw e
                        } catch (e: Exception) { busy = false; failed = true }
                    }
                }, enabled = password.length >= 8 && !busy)
                if (saved) PatientMessage(stringResource(Res.string.p2_backup_saved))
            }
            "delete" -> {
                PatientMessage(stringResource(Res.string.p2_delete_all_body))
                ListRow(stringResource(if (busy) Res.string.p2_working else Res.string.p2_delete_all),
                    titleColor = EasyPaperTheme.colors.destructive, chevron = false, onClick = {
                        if (!busy) {
                            busy = true; failed = false
                            scope.launch {
                                try {
                                    for (record in repository.records.value.toList()) repository.delete(record.request.id)
                                    settings.reset(); onDeleted()
                                } catch (e: CancellationException) { throw e
                                } catch (e: Exception) { failed = true
                                } finally { busy = false }
                            }
                        }
                    })
            }
            else -> {
                GroupedSection(header = stringResource(Res.string.p2_health_details), footer = stringResource(Res.string.p2_optional_details)) {
                    HealthField(stringResource(Res.string.p2_name), settings.name)
                    RowSeparator()
                    HealthField(stringResource(Res.string.p2_birth), settings.birth)
                    if (!validBirth(settings.birth.value, today)) PatientMessage(stringResource(Res.string.p2_birth_error), error = true)
                    RowSeparator()
                    HealthField(stringResource(Res.string.p2_sex), settings.sex)
                    RowSeparator()
                    HealthField(stringResource(Res.string.p2_height), settings.height)
                    RowSeparator()
                    HealthField(stringResource(Res.string.p2_weight), settings.weight)
                    RowSeparator()
                    TextAreaRow(settings.allergies.value.orEmpty(), settings.allergies::update, stringResource(Res.string.p2_allergies))
                    ListRow(stringResource(Res.string.p2_medical_id), subtitle = stringResource(Res.string.p2_later), chevron = false)
                }
                Spacer(Modifier.height(EasyPaperTheme.spacing.sectionGap))
                GroupedSection(footer = stringResource(Res.string.p2_reminders_body)) {
                    val reminderLabel = stringResource(Res.string.p2_reminders)
                    ListRow(reminderLabel, trailing = {
                        IosSwitch(settings.reminders.value == "true", { settings.reminders.update(it.toString()) },
                            Modifier.semantics { contentDescription = reminderLabel })
                    })
                }
                PatientMessage(stringResource(Res.string.p2_language))
                LanguagePicker(settings)
                Spacer(Modifier.height(EasyPaperTheme.spacing.sectionGap))
                GroupedSection(header = stringResource(Res.string.p2_privacy), footer = stringResource(Res.string.p2_privacy_body)) {
                    ListRow(stringResource(Res.string.p2_export), onClick = { page = "backup"; saved = false; failed = false })
                    RowSeparator()
                    ListRow(stringResource(Res.string.p2_delete_all), titleColor = EasyPaperTheme.colors.destructive,
                        onClick = { page = "delete"; failed = false })
                }
                Spacer(Modifier.height(EasyPaperTheme.spacing.sectionGap))
                GroupedSection {
                    ListRow(stringResource(Res.string.p2_account), subtitle = stringResource(Res.string.p2_account_body), chevron = false)
                    RowSeparator()
                    ListRow(stringResource(Res.string.p2_about), subtitle = stringResource(Res.string.p2_about_body), chevron = false)
                }
            }
        }
        if (failed) PatientMessage(stringResource(Res.string.p2_error), error = true)
    }
}

@Composable
private fun HealthField(label: String, setting: SavedSetting) {
    Text(label, style = EasyPaperTheme.typography.footnote, color = EasyPaperTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = EasyPaperTheme.spacing.m, end = EasyPaperTheme.spacing.m, top = EasyPaperTheme.spacing.s))
    TextFieldRow(setting.value.orEmpty(), setting::update, label)
}
