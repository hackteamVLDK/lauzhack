package org.easypaper.ui.doctor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.doc_receive_code_field
import easypaper.app.shared.generated.resources.doc_receive_receive
import easypaper.app.shared.generated.resources.doc_receive_receiving
import easypaper.app.shared.generated.resources.doc_receive_waiting
import easypaper.app.shared.generated.resources.doc_receive_token_required
import easypaper.app.shared.generated.resources.doc_receive_invalid
import easypaper.app.shared.generated.resources.doc_receive_too_many
import easypaper.app.shared.generated.resources.doc_receive_unauthorized
import easypaper.app.shared.generated.resources.doc_receive_conflict
import easypaper.app.shared.generated.resources.doc_file_choose
import easypaper.app.shared.generated.resources.doc_file_hint
import easypaper.app.shared.generated.resources.doc_file_selected
import easypaper.app.shared.generated.resources.doc_file_password
import easypaper.app.shared.generated.resources.doc_file_open
import easypaper.app.shared.generated.resources.doc_file_wrong_password
import easypaper.app.shared.generated.resources.open_failed
import easypaper.app.shared.generated.resources.open_offline
import easypaper.app.shared.generated.resources.open_opening
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import org.easypaper.report.HandoffClient
import org.easypaper.report.HandoffException
import org.easypaper.report.HandoffKeys
import org.easypaper.report.ReceivedReport
import org.easypaper.report.openFromPatient
import org.easypaper.report.openReportFile
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.ProminentButton
import org.easypaper.ui.components.TextFieldRow
import org.easypaper.ui.components.TintedButton
import org.easypaper.ui.handover.Problem
import org.easypaper.ui.handover.busy
import org.easypaper.ui.handover.rememberFilePicker
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.coroutines.cancellation.CancellationException

@Composable
internal fun ReceiveCodeForm(relayUrl: String, clinicToken: String?, http: HttpClient, onOpened: (ReceivedReport) -> Unit) {
    val scope = rememberCoroutineScope()
    val token = clinicToken?.trim().orEmpty()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    val spacing = EasyPaperTheme.spacing
    Column {
        GroupedSection {
            TextFieldRow(code, { text ->
                if (!busy) {
                    code = text.filter { it in '0'..'9' }.take(6)
                    error = null
                }
            }, placeholder = stringResource(Res.string.doc_receive_code_field), keyboardType = KeyboardType.Number)
        }
        Spacer(Modifier.height(spacing.xl))
        ProminentButton(
            stringResource(if (busy) Res.string.doc_receive_receiving else Res.string.doc_receive_receive),
            enabled = !busy && code.length == 6 && token.isNotEmpty(),
            busy = busy,
            modifier = Modifier.fillMaxWidth().busy(busy),
            onClick = {
                if (!busy && code.length == 6 && token.isNotEmpty()) {
                    val submittedCode = code
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val keys = HandoffKeys.generate()
                            val client = HandoffClient(relayUrl, http)
                            val secret = client.claim(submittedCode, token, keys.publicKey)
                            waiting = true
                            val bytes = client.awaitReport(submittedCode, token, secret)
                            val (report, photos) = openFromPatient(bytes, keys)
                            ensureActive()
                            onOpened(ReceivedReport(report, photos))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: HandoffException) {
                            error = handoffProblem(e.kind)
                        } catch (e: IOException) {
                            error = Res.string.open_offline
                        } catch (e: Exception) {
                            error = Res.string.open_failed
                        } finally {
                            busy = false
                            waiting = false
                        }
                    }
                }
            },
        )
        if (waiting) ReceiveStatus(stringResource(Res.string.doc_receive_waiting))
        Problem(if (token.isEmpty()) Res.string.doc_receive_token_required else error)
    }
}

private fun handoffProblem(kind: HandoffException.Kind): StringResource = when (kind) {
    HandoffException.Kind.NotFound, HandoffException.Kind.Expired -> Res.string.doc_receive_invalid
    HandoffException.Kind.TooMany -> Res.string.doc_receive_too_many
    HandoffException.Kind.Unauthorized -> Res.string.doc_receive_unauthorized
    HandoffException.Kind.Conflict -> Res.string.doc_receive_conflict
    HandoffException.Kind.Network -> Res.string.open_offline
}

@Composable
internal fun ReceiveFileForm(onOpened: (ReceivedReport) -> Unit) {
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    val pick = rememberFilePicker { picked ->
        if (!busy && picked != null) {
            bytes = picked
            password = ""
            error = null
        }
    }
    val spacing = EasyPaperTheme.spacing
    Column {
        GroupedSection(footer = stringResource(Res.string.doc_file_hint)) {
            TintedButton(stringResource(Res.string.doc_file_choose), onClick = { pick() }, enabled = !busy,
                modifier = Modifier.padding(spacing.m))
            if (bytes != null) {
                ReceiveStatus(stringResource(Res.string.doc_file_selected))
                TextFieldRow(password, { if (!busy) { password = it; error = null } },
                    placeholder = stringResource(Res.string.doc_file_password), secret = true)
            }
        }
        Spacer(Modifier.height(spacing.xl))
        ProminentButton(
            stringResource(if (busy) Res.string.open_opening else Res.string.doc_file_open),
            enabled = !busy && bytes != null && password.isNotEmpty(),
            busy = busy,
            modifier = Modifier.fillMaxWidth().busy(busy),
            onClick = {
                val selected = bytes
                if (!busy && selected != null && password.isNotEmpty()) {
                    val submittedPassword = password
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val (report, photos) = withContext(Dispatchers.Default) { openReportFile(selected, submittedPassword) }
                            ensureActive()
                            password = ""
                            bytes = null
                            onOpened(ReceivedReport(report, photos))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = Res.string.doc_file_wrong_password
                        } finally {
                            busy = false
                        }
                    }
                }
            },
        )
        Problem(error)
    }
}

@Composable
private fun ReceiveStatus(text: String) {
    Text(text, style = EasyPaperTheme.typography.subheadline, color = EasyPaperTheme.colors.secondaryLabel,
        modifier = Modifier.padding(EasyPaperTheme.spacing.m).semantics { liveRegion = LiveRegionMode.Polite })
}
