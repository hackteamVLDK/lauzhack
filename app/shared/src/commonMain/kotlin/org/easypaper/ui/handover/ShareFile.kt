package org.easypaper.ui.handover

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.*
import kotlinx.coroutines.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.easypaper.model.PatientRecord
import org.easypaper.report.prepareHandover
import org.easypaper.report.sealReportFile
import org.easypaper.storage.PatientRepository
import org.easypaper.ui.components.*
import org.easypaper.ui.patient.v2.PatientMessage
import org.easypaper.ui.patient.v2.rememberFileSaver
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
fun ShareFileScreen(repository: PatientRepository, record: PatientRecord, back: BackAction? = null, onSaved: () -> Unit = {}) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentOnSaved by rememberUpdatedState(onSaved)
    val date = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val saveFile = rememberFileSaver("easypaper-report-$date.epr") { success ->
        busy = false
        saved = success
        failed = !success
        if (success) currentOnSaved()
    }
    LargeTitleScaffold(stringResource(Res.string.p2_save_encrypted), back = back, bottomInset = false) {
        item {
            Column {
                GroupedSection(footer = stringResource(Res.string.p2_file_minimum)) {
                    TextFieldRow(password, { if (!busy) { password = it; saved = false; failed = false } },
                        stringResource(Res.string.p2_file_password), secret = true)
                    RowSeparator()
                    TextFieldRow(repeat, { if (!busy) { repeat = it; saved = false; failed = false } },
                        stringResource(Res.string.p2_file_repeat), secret = true)
                }
                PatientMessage(stringResource(Res.string.p2_file_hint))
                if (repeat.isNotEmpty() && password != repeat) PatientMessage(stringResource(Res.string.p2_file_mismatch), error = true)
                Spacer(Modifier.height(EasyPaperTheme.spacing.sectionGap))
                ProminentButton(stringResource(if (busy) Res.string.p2_file_saving else Res.string.p2_file_save), {
                    busy = true; failed = false; saved = false
                    val passphrase = password
                    scope.launch {
                        try {
                            val (report, photos) = prepareHandover(record, Clock.System.now().toEpochMilliseconds(), photo = repository::photo)
                            val bytes = withContext(Dispatchers.Default) { sealReportFile(report, photos, passphrase) }
                            password = ""; repeat = ""
                            saveFile(bytes)
                        } catch (e: CancellationException) { throw e
                        } catch (e: Exception) { busy = false; failed = true }
                    }
                }, Modifier.fillMaxWidth(), enabled = !busy && password.length >= 8 && password == repeat)
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    if (saved) PatientMessage(stringResource(Res.string.p2_file_saved))
                    if (failed) PatientMessage(stringResource(Res.string.p2_file_failed), error = true)
                }
            }
        }
    }
}
