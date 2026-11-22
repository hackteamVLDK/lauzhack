package org.easypaper.ui.handover

import easypaper.app.shared.generated.resources.open_opening
import easypaper.app.shared.generated.resources.share_creating
import easypaper.app.shared.generated.resources.busy
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.ic_qr_code_2
import easypaper.app.shared.generated.resources.ic_remove
import easypaper.app.shared.generated.resources.open_scan
import easypaper.app.shared.generated.resources.open_scan_failed
import easypaper.app.shared.generated.resources.open_failed
import easypaper.app.shared.generated.resources.open_not_ours
import easypaper.app.shared.generated.resources.open_offline
import easypaper.app.shared.generated.resources.open_report
import easypaper.app.shared.generated.resources.open_report_button
import easypaper.app.shared.generated.resources.open_report_hint
import easypaper.app.shared.generated.resources.open_used
import easypaper.app.shared.generated.resources.share_create
import easypaper.app.shared.generated.resources.share_explain
import easypaper.app.shared.generated.resources.share_failed
import easypaper.app.shared.generated.resources.share_new
import easypaper.app.shared.generated.resources.share_none
import easypaper.app.shared.generated.resources.share_none_message
import easypaper.app.shared.generated.resources.share_offline_at
import easypaper.app.shared.generated.resources.share_local_network
import easypaper.app.shared.generated.resources.share_qr
import easypaper.app.shared.generated.resources.share_show
import easypaper.app.shared.generated.resources.tab_share
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.ktor.client.HttpClient
import kotlinx.io.IOException
import kotlinx.coroutines.launch
import org.easypaper.report.ReceivedReport
import org.easypaper.report.RelayClient
import org.easypaper.report.RelayException
import org.easypaper.report.handover
import org.easypaper.report.receive
import org.easypaper.storage.PatientRepository
import org.easypaper.model.PatientRecord
import org.easypaper.ui.components.BackAction
import org.easypaper.ui.components.EmptyState
import org.easypaper.ui.components.GroupedSection
import org.easypaper.ui.components.LargeTitleScaffold
import org.easypaper.ui.components.PlainButton
import org.easypaper.ui.components.ProminentButton
import org.easypaper.ui.components.TextAreaRow
import org.easypaper.ui.components.TintedButton
import org.easypaper.ui.shell.current
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/** The relay the apps use: the developer setting, the platform's server, or the computer running the emulator. */
fun relayUrl(setting: String?, platformDefault: String?): String = (setting ?: platformDefault ?: EMULATOR_HOST).trimEnd('/')

// the host computer as seen from the Android emulator; a real phone gets the address in the developer menu
private const val EMULATOR_HOST = "http://10.0.2.2:8080"

/** One HTTP client per screen, closed when the screen goes; the platform's Ktor engine is found on the classpath. */
@Composable
private fun rememberHttpClient(): HttpClient {
    val http = remember { HttpClient() }
    DisposableEffect(http) { onDispose { http.close() } }
    return http
}

/**
 * The patient's Share tab (task 30): seals the current record with its photos on the phone, uploads only
 * ciphertext to the relay and shows the QR with the key. Nothing leaves the phone before the patient taps.
 */
@Composable
fun ShareScreen(repository: PatientRepository, relayUrl: String, onDeveloper: (() -> Unit)? = null,
                selectedRecord: PatientRecord? = null, back: BackAction? = null, onCreated: ((PatientRecord) -> Unit)? = null) {
    val records by repository.records.collectAsState()
    val record = selectedRecord ?: records.current()
    val scope = rememberCoroutineScope()
    val http = rememberHttpClient()
    var qr by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    val askLocalNetwork = rememberLocalNetworkAccess()
    LargeTitleScaffold(title = stringResource(Res.string.tab_share), back = back, onTitleLongPress = onDeveloper, bottomInset = false) {
        item {
            val spacing = EasyPaperTheme.spacing
            when {
                record == null -> EmptyState(stringResource(Res.string.share_none), stringResource(Res.string.share_none_message))
                qr == null -> Column {
                    GroupedSection {
                        Text(stringResource(Res.string.share_explain), style = EasyPaperTheme.typography.body,
                            color = EasyPaperTheme.colors.label, modifier = Modifier.padding(spacing.m))
                    }
                    Spacer(Modifier.height(spacing.xl))
                    ProminentButton(stringResource(if (busy) Res.string.share_creating else Res.string.share_create), enabled = !busy,
                        modifier = Modifier.fillMaxWidth().busy(busy), onClick = {
                        busy = true
                        error = null
                        // Android 17 asks for the local network here (the relay is on the clinic's or demo laptop);
                        // a refusal still tries, since the server may be on the internet, and explains a failure.
                        askLocalNetwork { granted ->
                            scope.launch {
                                try {
                                    qr = repository.handover(record, RelayClient(relayUrl, http), Clock.System.now().toEpochMilliseconds())
                                    onCreated?.invoke(record)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: IOException) {
                                    error = if (granted) Res.string.share_offline_at else Res.string.share_local_network
                                } catch (e: Exception) {
                                    error = Res.string.share_failed // relay full or refused; the record on the phone is untouched
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    })
                    Problem(error, relayUrl)
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    // dark modules on white with a quiet zone in both themes, as scanners expect
                    Box(Modifier.background(Color.White).padding(spacing.l)) {
                        Image(rememberQrCodePainter(qr!!), contentDescription = stringResource(Res.string.share_qr), modifier = Modifier.size(260.dp))
                    }
                    Text(stringResource(Res.string.share_show), style = EasyPaperTheme.typography.body, color = EasyPaperTheme.colors.label,
                        modifier = Modifier.padding(spacing.m))
                    PlainButton(stringResource(Res.string.share_new), onClick = { qr = null })
                }
            }
        }
    }
}

/**
 * The doctor's side (task 30): the code from the patient's QR (scanned or pasted) fetches the sealed report once,
 * opens it with the key in the code and hands it over with all its photos.
 */
@Composable
fun OpenReportScreen(relayUrl: String, onOpened: (ReceivedReport) -> Unit, back: BackAction?) {
    val scope = rememberCoroutineScope()
    val http = rememberHttpClient()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    fun open(text: String) {
        busy = true
        error = null
        scope.launch {
            try {
                onOpened(RelayClient(relayUrl, http).receive(text.trim()))
                code = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: RelayException) {
                error = if (e.status == 404) Res.string.open_used else Res.string.open_failed
            } catch (e: IllegalArgumentException) {
                error = Res.string.open_not_ours
            } catch (e: IOException) {
                error = Res.string.open_offline
            } catch (e: Exception) {
                error = Res.string.open_failed // wrong key or damaged bytes: decryption refuses
            } finally {
                busy = false
            }
        }
    }
    val readQr = rememberQrReader(onCode = { text -> code = text; open(text) }, onFailure = { error = Res.string.open_scan_failed })
    LargeTitleScaffold(title = stringResource(Res.string.open_report), back = back) {
        item {
            val spacing = EasyPaperTheme.spacing
            Column {
                if (readQr != null) {
                    TintedButton(stringResource(Res.string.open_scan), onClick = { error = null; readQr() }, enabled = !busy,
                        icon = Res.drawable.ic_qr_code_2, modifier = Modifier.padding(start = spacing.m, bottom = spacing.l))
                }
                GroupedSection(footer = stringResource(Res.string.open_report_hint)) {
                    TextAreaRow(code, { code = it; error = null }, placeholder = "ep1:…")
                }
                Spacer(Modifier.height(spacing.xl))
                ProminentButton(stringResource(if (busy) Res.string.open_opening else Res.string.open_report_button), enabled = !busy && code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().busy(busy), onClick = { open(code) })
                Problem(error)
            }
        }
    }
}

/** While a transfer runs: the screen reader hears that the button is busy (its label already says what happens). */
@Composable
private fun Modifier.busy(busy: Boolean): Modifier {
    if (!busy) return this
    val label = stringResource(Res.string.busy)
    return semantics { stateDescription = label; liveRegion = LiveRegionMode.Polite }
}

/** An error as a sentence with an icon, announced to screen readers. */
@Composable
private fun Problem(message: StringResource?, vararg args: Any) {
    if (message == null) return
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    androidx.compose.foundation.layout.Row(
        Modifier.padding(start = spacing.m, end = spacing.m, top = spacing.s).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(painterResource(Res.drawable.ic_remove), contentDescription = null, tint = colors.destructive,
            modifier = Modifier.size(spacing.rowIcon).padding(end = spacing.xxs))
        Text(stringResource(message, *args), style = EasyPaperTheme.typography.subheadline, color = colors.destructive)
    }
}
