package org.easypaper.ui.account

import androidx.compose.runtime.*
import easypaper.app.shared.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.easypaper.account.AccountClient
import org.easypaper.account.AccountException
import org.easypaper.ui.components.*
import org.easypaper.ui.patient.v2.PatientSettings
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AccountSection(settings: PatientSettings, onSignIn: () -> Unit,
                            client: AccountClient = rememberAccountClient()) {
    val token = settings.accountToken.value
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<AccountException.Kind?>(null) }
    val scope = rememberCoroutineScope()
    // Cached details are for display only; /me is the authority for the current account.
    LaunchedEffect(token, client) {
        problem = null
        if (token != null) {
            try {
                val account = client.me(token)
                if (settings.accountToken.value == token) settings.cacheAccount(account)
            } catch (e: CancellationException) { throw e
            } catch (e: AccountException) {
                if (e.kind == AccountException.Kind.Unauthorized && settings.accountToken.value == token) settings.clearAccount()
                problem = e.kind
            } catch (_: Exception) { problem = AccountException.Kind.Network }
        }
    }
    GroupedSection(header = stringResource(Res.string.p2_account)) {
        if (token == null) {
            ListRow(stringResource(Res.string.p2_account_sign_in), onClick = onSignIn)
        } else {
            ListRow(settings.accountEmail.value.orEmpty(), subtitle = settings.accountName.value, chevron = false)
            val provider = when (settings.accountProvider.value) {
                "google" -> Res.string.p2_account_provider_google
                "invite" -> Res.string.p2_account_provider_invite
                "email" -> Res.string.p2_account_provider_email
                else -> Res.string.p2_account_provider_unknown
            }
            RowSeparator()
            ListRow(stringResource(Res.string.p2_account_provider), subtitle = stringResource(provider), chevron = false)
            RowSeparator()
            ListRow(stringResource(if (busy) Res.string.p2_working else Res.string.p2_account_sign_out), chevron = false,
                onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            try { client.signOut(token)
                            } catch (e: CancellationException) { throw e
                            } catch (e: AccountException) { problem = e.kind
                            } catch (_: Exception) { problem = AccountException.Kind.Network
                            } finally {
                                if (settings.accountToken.value == token) settings.clearAccount()
                                busy = false
                            }
                        }
                    }
                })
        }
    }
    problem?.let { AccountNotice(stringResource(accountProblem(it)), error = true) }
}
