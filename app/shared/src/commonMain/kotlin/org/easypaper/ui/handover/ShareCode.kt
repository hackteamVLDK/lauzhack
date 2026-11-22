package org.easypaper.ui.handover

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.*
import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlinx.io.IOException
import org.easypaper.model.PatientRecord
import org.easypaper.report.*
import org.easypaper.storage.PatientRepository
import org.easypaper.ui.components.*
import org.easypaper.ui.theme.EasyPaperTheme
import org.easypaper.ui.theme.tabular
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

private enum class CodeStatus { Ready, Creating, Waiting, Sending, Sent, Expired, Failed }

@Composable
fun ShareCodeScreen(repository: PatientRepository, relayUrl: String, record: PatientRecord,
                    back: BackAction? = null, onSent: () -> Unit = {}, http: HttpClient = rememberHttpClient()) {
    val scope = rememberCoroutineScope()
    val currentOnSent by rememberUpdatedState(onSent)
    val askLocalNetwork = rememberLocalNetworkAccess()
    var status by remember { mutableStateOf(CodeStatus.Ready) }
    var slot by remember { mutableStateOf<HandoffSlot?>(null) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    var remaining by remember { mutableLongStateOf(0) }
    LaunchedEffect(slot, status) {
        val active = slot
        if (active != null && (status == CodeStatus.Waiting || status == CodeStatus.Sending)) {
            while (isActive) {
                remaining = ((active.expiresAt - Clock.System.now().toEpochMilliseconds() + 999) / 1000).coerceAtLeast(0)
                delay(1_000)
            }
        }
    }
    val create: () -> Unit = {
        status = CodeStatus.Creating
        slot = null
        error = null
        val ask: ((Boolean) -> Unit) -> Unit = if (isLocalNetworkUrl(relayUrl)) askLocalNetwork else { go -> go(true) }
        ask { granted ->
            scope.launch {
                try {
                    val client = HandoffClient(relayUrl, http)
                    val created = client.create()
                    slot = created
                    remaining = ((created.expiresAt - Clock.System.now().toEpochMilliseconds() + 999) / 1000).coerceAtLeast(0)
                    status = CodeStatus.Waiting
                    val sent = withTimeoutOrNull((created.expiresAt - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0)) {
                        val key = client.awaitDoctorKey(created)
                        status = CodeStatus.Sending
                        val (report, photos) = prepareHandover(record, Clock.System.now().toEpochMilliseconds(), photo = repository::photo)
                        val bytes = withContext(Dispatchers.Default) { sealForDoctor(report, photos, key) }
                        client.upload(created, bytes)
                        true
                    } ?: false
                    status = if (sent) CodeStatus.Sent else CodeStatus.Expired
                    if (sent) currentOnSent()
                } catch (e: CancellationException) { throw e
                } catch (e: HandoffException) {
                    if (e.kind == HandoffException.Kind.Expired) status = CodeStatus.Expired
                    else {
                        status = CodeStatus.Failed
                        error = when (e.kind) {
                            HandoffException.Kind.TooMany -> Res.string.p2_code_too_many
                            HandoffException.Kind.Network -> if (granted) Res.string.share_offline_at else Res.string.share_local_network
                            else -> Res.string.share_failed
                        }
                    }
                } catch (e: IOException) {
                    status = CodeStatus.Failed
                    error = if (granted) Res.string.share_offline_at else Res.string.share_local_network
                } catch (e: Exception) {
                    status = CodeStatus.Failed
                    error = Res.string.share_failed
                }
            }
        }
    }
    LargeTitleScaffold(stringResource(Res.string.p2_code_title), back = back, bottomInset = false) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val active = slot
                if (active != null && (status == CodeStatus.Waiting || status == CodeStatus.Sending)) {
                    Text(active.code.chunked(3).joinToString(" "), style = EasyPaperTheme.typography.largeTitle.tabular(),
                        color = EasyPaperTheme.colors.label, modifier = Modifier.clearAndSetSemantics {
                            contentDescription = active.code.toList().joinToString(" ")
                        })
                    // The clock is readable on demand; only the separate status is a live region.
                    Text("${(remaining / 60).toString().padStart(2, '0')}:${(remaining % 60).toString().padStart(2, '0')}",
                        style = EasyPaperTheme.typography.body.tabular(), color = EasyPaperTheme.colors.secondaryLabel)
                }
                if (status == CodeStatus.Sent) Icon(painterResource(Res.drawable.ic_check_circle), null,
                    tint = EasyPaperTheme.colors.confirmed, modifier = Modifier.size(EasyPaperTheme.spacing.heroIcon))
                val message = when (status) {
                    CodeStatus.Ready -> Res.string.p2_code_body
                    CodeStatus.Creating -> Res.string.share_creating
                    CodeStatus.Waiting -> Res.string.p2_code_waiting
                    CodeStatus.Sending -> Res.string.p2_code_sending
                    CodeStatus.Sent -> Res.string.p2_code_sent
                    CodeStatus.Expired -> Res.string.p2_code_expired
                    CodeStatus.Failed -> null
                }
                if (message != null) Text(stringResource(message), style = EasyPaperTheme.typography.body, color = EasyPaperTheme.colors.label,
                    modifier = Modifier.padding(EasyPaperTheme.spacing.m).semantics { liveRegion = LiveRegionMode.Polite })
                if (status == CodeStatus.Ready || status == CodeStatus.Expired || status == CodeStatus.Failed) {
                    ProminentButton(stringResource(if (status == CodeStatus.Ready) Res.string.p2_code_create else Res.string.p2_code_new),
                        create, Modifier.fillMaxWidth())
                }
                Problem(error, relayUrl)
            }
        }
    }
}
