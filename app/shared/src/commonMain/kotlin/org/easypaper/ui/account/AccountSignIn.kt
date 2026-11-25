package org.easypaper.ui.account

import org.easypaper.ui.patient.v2.PatientMessage
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import easypaper.app.shared.generated.resources.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.easypaper.account.AccountClient
import org.easypaper.account.AccountException
import org.easypaper.ui.components.*
import org.easypaper.ui.patient.v2.PatientSettings
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The same sign-in choices are used in onboarding and in the profile. */
@Composable
fun AccountSignIn(settings: PatientSettings, onSignedIn: () -> Unit,
                  onContinueWithoutAccount: (() -> Unit)? = null,
                  client: AccountClient = rememberAccountClient()) {
    val flow = remember(client, settings.accountToken) { AccountFlow(client, settings) }
    val scope = rememberCoroutineScope()
    var emailPage by remember { mutableStateOf(false) }
    var appleSoon by remember { mutableStateOf(false) }
    var googlePending by remember { mutableStateOf(false) }
    var googleMessage by remember { mutableStateOf<StringResource?>(null) }
    val google = rememberGoogleSignIn()
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(flow.signedIn) { if (flow.signedIn) currentOnSignedIn() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EasyPaperTheme.spacing.s)) {
        if (flow.challenge != null) {
            AccountCodeForm(flow, onVerify = { scope.launch { flow.verify() } },
                onResend = { scope.launch { flow.sendCode() } })
            PlainButton(stringResource(Res.string.p2_account_change_email), { flow.backToEmail() }, enabled = !flow.busy)
        } else if (emailPage) {
            PatientMessage(stringResource(Res.string.p2_account_email_body))
            GroupedSection {
                TextFieldRow(flow.email, { if (!flow.busy) flow.email = it },
                    stringResource(Res.string.p2_account_email), keyboardType = KeyboardType.Email)
            }
            val remaining = rememberRetrySeconds(flow)
            CapsuleButton(stringResource(if (flow.busy) Res.string.p2_working else Res.string.p2_account_send_code),
                { scope.launch { flow.sendCode() } }, prominent = true,
                enabled = !flow.busy && validAccountEmail(flow.email) && remaining == 0)
            if (remaining > 0) PatientMessage(stringResource(Res.string.p2_account_resend_wait, remaining))
            PlainButton(stringResource(Res.string.p2_account_other_options), { emailPage = false }, enabled = !flow.busy)
        } else {
            CapsuleButton(stringResource(Res.string.p2_continue_google), {
                googleMessage = null
                when {
                    !googleSignInConfigured -> googleMessage = Res.string.p2_account_google_unconfigured
                    !googleSignInSupported -> googleMessage = Res.string.p2_account_google_platform
                    else -> {
                        googlePending = true
                        google { credential ->
                            googlePending = false
                            if (credential == null) googleMessage = Res.string.p2_account_google_cancelled
                            else scope.launch { flow.google(credential) }
                        }
                    }
                }
            }, enabled = !flow.busy && !googlePending)
            CapsuleButton(stringResource(Res.string.p2_continue_apple), { appleSoon = true }, enabled = !flow.busy && !googlePending)
            CapsuleButton(stringResource(Res.string.p2_continue_email), { emailPage = true; googleMessage = null },
                prominent = true, enabled = !flow.busy && !googlePending)
            if (googlePending || flow.busy) PatientMessage(stringResource(Res.string.p2_working))
            googleMessage?.let { AccountNotice(stringResource(it)) }
        }
        flow.problem?.let {
            val message = if (!emailPage && flow.challenge == null && it == AccountException.Kind.MailUnavailable)
                Res.string.p2_account_google_unavailable else accountProblem(it)
            AccountNotice(stringResource(message), error = true)
        }
        if (onContinueWithoutAccount != null) PlainButton(stringResource(Res.string.p2_continue_without_account),
            onContinueWithoutAccount, enabled = !flow.busy && !googlePending)
    }
    if (appleSoon) AlertDialog(
        onDismissRequest = { appleSoon = false },
        confirmButton = { PlainButton(stringResource(Res.string.p2_account_ok), { appleSoon = false }) },
        text = { Text(stringResource(Res.string.p2_account_apple_soon), style = EasyPaperTheme.typography.body) },
        containerColor = EasyPaperTheme.colors.cellRaised,
        textContentColor = EasyPaperTheme.colors.label,
        shape = EasyPaperTheme.shapes.sheet,
    )
}

internal fun validAccountEmail(email: String): Boolean {
    val value = email.trim()
    return value.length <= 254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value)
}

@Composable
internal fun AccountCodeForm(flow: AccountFlow, onVerify: () -> Unit, onResend: () -> Unit) {
    PatientMessage(stringResource(Res.string.p2_account_code_body))
    GroupedSection {
        TextFieldRow(flow.code, flow::changeCode, stringResource(Res.string.p2_account_code), keyboardType = KeyboardType.Number)
    }
    CapsuleButton(stringResource(if (flow.busy) Res.string.p2_working else Res.string.p2_account_verify), onVerify,
        prominent = true, enabled = !flow.busy && !flow.mustResend && flow.code.length == 6)
    val remaining = rememberRetrySeconds(flow)
    PlainButton(if (remaining == 0) stringResource(Res.string.p2_account_resend)
        else stringResource(Res.string.p2_account_resend_wait, remaining), onResend, enabled = !flow.busy && remaining == 0)
}

@Composable
internal fun rememberRetrySeconds(flow: AccountFlow): Int {
    var remaining by remember(flow, flow.retryAt) { mutableIntStateOf(flow.retrySeconds()) }
    LaunchedEffect(flow, flow.retryAt) {
        remaining = flow.retrySeconds()
        while (remaining > 0) { delay(1_000); remaining = flow.retrySeconds() }
    }
    return remaining
}

@Composable
internal fun AccountNotice(text: String, error: Boolean = false) {
    Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { PatientMessage(text, error = error) }
}

internal fun accountProblem(kind: AccountException.Kind): StringResource = when (kind) {
    AccountException.Kind.BadCode -> Res.string.p2_account_bad_code
    AccountException.Kind.Expired -> Res.string.p2_account_expired
    AccountException.Kind.TooMany -> Res.string.p2_account_too_many
    AccountException.Kind.MailUnavailable -> Res.string.p2_account_mail_unavailable
    AccountException.Kind.Unauthorized -> Res.string.p2_account_unauthorized
    AccountException.Kind.NotFound -> Res.string.p2_account_not_found
    AccountException.Kind.Network -> Res.string.p2_account_network
}
