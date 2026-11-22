package org.easypaper.ui.patient.v2

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import easypaper.app.shared.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.easypaper.storage.PatientRepository
import org.easypaper.storage.exportBackup
import org.easypaper.ui.components.*
import org.easypaper.ui.account.AccountSection
import org.easypaper.ui.account.AccountSignIn
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.stringResource

@Composable
expect fun rememberFileSaver(fileName: String, onResult: (Boolean) -> Unit): (ByteArray) -> Unit

@Composable
fun rememberBackupSaver(onResult: (Boolean) -> Unit): (ByteArray) -> Unit = rememberFileSaver("EasyPaper.epb", onResult)

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
        "account" -> Res.string.p2_account_sign_in
        "backup" -> Res.string.p2_export
        "delete" -> Res.string.p2_delete_all
        else -> Res.string.p2_profile
    })
    Sheet(visible, title, stringResource(when { busy -> Res.string.p2_working; page == "profile" -> Res.string.p2_done; else -> Res.string.p2_cancel }),
        onDismiss = { if (!busy) { if (page == "profile") onDismiss() else { page = "profile"; password = "" } } }) {
        when (page) {
            "account" -> AccountSignIn(settings, onSignedIn = { page = "profile" })
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
                    HealthField(stringResource(Res.string.p2_birth), settings.birth, example = stringResource(Res.string.p2_birth_example),
                        keyboardType = KeyboardType.Number)
                    if (!validBirth(settings.birth.value, today)) PatientMessage(stringResource(Res.string.p2_birth_error), error = true)
                    RowSeparator()
                    HealthField(stringResource(Res.string.p2_sex), settings.sex)
                    RowSeparator()
                    HealthField(stringResource(Res.string.p2_height), settings.height, keyboardType = KeyboardType.Decimal)
                    RowSeparator()
                    HealthField(stringResource(Res.string.p2_weight), settings.weight, keyboardType = KeyboardType.Decimal)
                    RowSeparator()
                    RowSeparator()
                    val allergies = stringResource(Res.string.p2_allergies)
                    FieldLabel(allergies)
                    TextAreaRow(settings.allergies.value.orEmpty(), settings.allergies::update, placeholder = "", description = allergies)
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
                AccountSection(settings, onSignIn = { page = "account" })
                Spacer(Modifier.height(EasyPaperTheme.spacing.sectionGap))
                GroupedSection {
                    ListRow(stringResource(Res.string.p2_about), subtitle = stringResource(Res.string.p2_about_body), chevron = false)
                }
            }
        }
        if (failed) PatientMessage(stringResource(Res.string.p2_error), error = true)
    }
}

/**
 * One profile detail: its name above, the value in the field. The field shows an [example] of the format (or
 * nothing) instead of repeating the name; screen readers still hear the name.
 */
@Composable
private fun HealthField(label: String, setting: SavedSetting, example: String = "", keyboardType: KeyboardType = KeyboardType.Text) {
    FieldLabel(label)
    TextFieldRow(setting.value.orEmpty(), setting::update, placeholder = example, description = label, keyboardType = keyboardType)
}

@Composable
private fun FieldLabel(label: String) {
    Text(label, style = EasyPaperTheme.typography.footnote, color = EasyPaperTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = EasyPaperTheme.spacing.m, end = EasyPaperTheme.spacing.m, top = EasyPaperTheme.spacing.s))
}
