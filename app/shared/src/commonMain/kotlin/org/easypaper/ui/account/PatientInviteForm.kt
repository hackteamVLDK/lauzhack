package org.easypaper.ui.account

import org.easypaper.ui.patient.v2.PatientMessage
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import easypaper.app.shared.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.easypaper.account.AccountClient
import org.easypaper.account.AccountException
import org.easypaper.invite.*
import org.easypaper.model.CareRequest
import org.easypaper.ui.components.*
import org.easypaper.ui.patient.OpenInviteForm
import org.easypaper.ui.patient.v2.PatientSettings
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.stringResource

/** Intercepts person-bound invitations before the legacy Start recording action is offered. */
@Composable
fun ColumnScope.PatientInviteForm(initial: String?, settings: PatientSettings, serverUrl: String?,
                                  client: AccountClient = rememberAccountClient(serverUrl),
                                  onStart: suspend (CareRequest) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial.orEmpty()) }
    var request by remember(initial) { mutableStateOf((parseInvite(initial.orEmpty()) as? InviteResult.Ok)?.request) }
    var error by remember(initial) { mutableStateOf<InviteError?>(null) }
    var starting by remember(initial) { mutableStateOf(false) }
    var startFailed by remember(initial) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val current = request
    val personToken = current?.personToken
    if (current == null) {
        GroupedSection {
            // a pasted link is recognized at once (as before); 'Open' explains what is wrong otherwise
            TextAreaRow(text, { changed ->
                text = changed; error = null
                (parseInvite(changed) as? InviteResult.Ok)?.let { request = it.request }
            }, placeholder = stringResource(Res.string.invite_placeholder))
        }
        error?.let { reason ->
            val message = when (reason) {
                InviteError.NOT_INVITE -> Res.string.invite_not
                InviteError.NEWER_VERSION -> Res.string.invite_newer
                InviteError.DAMAGED -> Res.string.invite_damaged
                InviteError.TOO_LARGE -> Res.string.invite_too_large
            }
            AccountNotice(stringResource(message), error = true)
        }
        Spacer(Modifier.height(EasyPaperTheme.spacing.xl))
        ProminentButton(stringResource(Res.string.invite_open), enabled = text.isNotBlank(), onClick = {
            when (val result = parseInvite(text)) {
                is InviteResult.Ok -> request = result.request
                is InviteResult.Error -> error = result.reason
            }
        })
    } else if (personToken != null) {
        key(current.id, personToken) {
            InviteIdentity(settings, personToken, client, onStart = { onStart(current) })
        }
    } else {
        // The existing preview and recording action remain available for old invitations.
        OpenInviteForm(text) { accepted ->
            if (!starting) {
                starting = true; startFailed = false
                scope.launch {
                    try { onStart(accepted)
                    } catch (e: CancellationException) { throw e
                    } catch (_: Exception) { startFailed = true
                    } finally { starting = false }
                }
            }
        }
        if (starting) PatientMessage(stringResource(Res.string.p2_working))
        if (startFailed) AccountNotice(stringResource(Res.string.p2_error), error = true)
    }
}

@Composable
internal fun InviteIdentity(settings: PatientSettings, inviteToken: String, client: AccountClient,
                            onStart: suspend () -> Unit) {
    val flow = remember(client, inviteToken, settings.accountToken) { AccountFlow(client, settings, inviteToken) }
    val scope = rememberCoroutineScope()
    val currentOnStart by rememberUpdatedState(onStart)
    var notMe by remember(inviteToken) { mutableStateOf(false) }
    var starting by remember(inviteToken) { mutableStateOf(false) }
    var started by remember(inviteToken) { mutableStateOf(false) }
    var startFailed by remember(inviteToken) { mutableStateOf(false) }
    val start: () -> Unit = {
        if (!starting && !started) {
            starting = true; startFailed = false
            scope.launch {
                try { currentOnStart(); started = true
                } catch (e: CancellationException) { throw e
                } catch (_: Exception) { startFailed = true
                } finally { starting = false }
            }
        }
    }
    LaunchedEffect(flow) { flow.loadPerson() }
    LaunchedEffect(flow.signedIn) { if (flow.signedIn) start() }
    Column(verticalArrangement = Arrangement.spacedBy(EasyPaperTheme.spacing.s)) {
        if (notMe) {
            AccountNotice(stringResource(Res.string.p2_account_not_me_body))
        } else if (started) {
            AccountNotice(stringResource(Res.string.p2_account_recording_started))
        } else if (flow.signedIn || starting || startFailed) {
            if (starting) PatientMessage(stringResource(Res.string.p2_working))
            if (startFailed) {
                AccountNotice(stringResource(Res.string.p2_error), error = true)
                CapsuleButton(stringResource(Res.string.invite_start), start, prominent = true, enabled = !starting)
            }
        } else {
            Text(stringResource(Res.string.p2_account_is_this_you), style = EasyPaperTheme.typography.title1,
                color = EasyPaperTheme.colors.label, modifier = Modifier.semantics { heading() })
            val person = flow.person
            if (person != null) {
                GroupedSection {
                    ListRow(person.name, chevron = false)
                    person.phoneMasked?.let { RowSeparator(); ListRow(it, chevron = false) }
                    RowSeparator()
                    ListRow(person.emailMasked, chevron = false)
                }
                if (flow.challenge == null) {
                    val remaining = rememberRetrySeconds(flow)
                    CapsuleButton(stringResource(if (flow.busy) Res.string.p2_working else Res.string.p2_account_confirm_person),
                        { scope.launch { flow.sendCode() } }, prominent = true, enabled = !flow.busy && remaining == 0)
                    if (remaining > 0) PatientMessage(stringResource(Res.string.p2_account_resend_wait, remaining))
                } else {
                    AccountCodeForm(flow, onVerify = { scope.launch { flow.verify() } },
                        onResend = { scope.launch { flow.sendCode() } })
                }
                PlainButton(stringResource(Res.string.p2_account_not_me), { notMe = true }, enabled = !flow.busy)
            } else if (flow.busy) PatientMessage(stringResource(Res.string.p2_working))
            flow.problem?.let { AccountNotice(stringResource(accountProblem(it)), error = true) }
            if (flow.problem == AccountException.Kind.Network || flow.problem == AccountException.Kind.MailUnavailable) {
                if (person == null) PlainButton(stringResource(Res.string.p2_account_retry),
                    { scope.launch { flow.loadPerson() } }, enabled = !flow.busy)
                PatientMessage(stringResource(Res.string.p2_account_invite_offline))
                PlainButton(stringResource(Res.string.p2_continue_without_account), start, enabled = !flow.busy)
            }
        }
    }
}

