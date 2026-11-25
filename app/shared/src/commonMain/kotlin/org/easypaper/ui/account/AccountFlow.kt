package org.easypaper.ui.account

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import org.easypaper.account.*
import org.easypaper.ui.handover.relayUrl
import org.easypaper.ui.handover.rememberHttpClient
import org.easypaper.ui.patient.v2.PatientSettings
import org.easypaper.ui.shell.defaultServerUrl
import org.easypaper.ui.shell.rememberSavedSetting
import kotlin.time.Clock

/** Uses the same persisted server override as the developer menu. No account data is logged. */
@Composable
internal fun rememberAccountClient(serverUrl: String? = rememberSavedSetting("serverUrl").value): AccountClient {
    val http = rememberHttpClient()
    val baseUrl = relayUrl(serverUrl, defaultServerUrl())
    return remember(baseUrl, http) { AccountClient(baseUrl, http) }
}

internal fun PatientSettings.cacheAccount(account: Account) {
    accountEmail.update(account.email)
    accountName.update(account.name)
    accountProvider.update(account.provider)
    if (name.value.isNullOrBlank() && !account.name.isNullOrBlank()) name.update(account.name)
}

internal fun PatientSettings.saveSession(session: AccountSession) {
    cacheAccount(session.account)
    accountToken.update(session.token)
}

internal fun PatientSettings.clearAccount() {
    listOf(accountToken, accountEmail, accountName, accountProvider).forEach { it.update(null) }
}

/** Shared email/code state for onboarding, the profile sheet and a doctor's invitation. */
@Stable
internal class AccountFlow(private val client: AccountClient, private val settings: PatientSettings,
                           private val inviteToken: String? = null,
                           private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }) {
    var email by mutableStateOf("")
    var code by mutableStateOf("")
        private set
    var challenge by mutableStateOf<EmailChallenge?>(null)
        private set
    var person by mutableStateOf<InvitedPerson?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var problem by mutableStateOf<AccountException.Kind?>(null)
        private set
    var retryAt by mutableLongStateOf(0)
        private set
    var signedIn by mutableStateOf(false)
        private set
    var mustResend by mutableStateOf(false)
        private set

    fun changeCode(text: String) {
        if (!busy && !mustResend) { code = text.filter { it in '0'..'9' }.take(6); problem = null }
    }

    fun backToEmail() {
        if (!busy) { challenge = null; code = ""; problem = null; mustResend = false }
    }

    fun retrySeconds(): Int = ((retryAt - now() + 999) / 1000)
        .coerceAtLeast(0).toInt()

    suspend fun loadPerson() = perform {
        person = client.invitedPerson(requireNotNull(inviteToken))
    }

    suspend fun sendCode() {
        if (retrySeconds() > 0) return
        perform {
            val next = if (inviteToken != null) client.confirmInvite(inviteToken) else client.startEmail(email.trim())
            challenge = next
            code = ""
            mustResend = false
            retryAt = now() + 60_000
        }
    }

    suspend fun verify() {
        val current = challenge ?: return
        if (code.length == 6 && !mustResend) perform { accept(client.verifyEmail(current.challengeId, code)) }
    }

    suspend fun google(credential: GoogleSignInCredential) = perform { accept(credential.signIn(client)) }

    private fun accept(session: AccountSession) {
        settings.saveSession(session)
        signedIn = true
        code = ""
    }

    private suspend fun perform(action: suspend () -> Unit) {
        if (busy || signedIn) return
        busy = true
        problem = null
        try { action()
        } catch (e: CancellationException) { throw e
        } catch (e: AccountException) {
            problem = e.kind
            if (e.kind == AccountException.Kind.TooMany) retryAt = now() + 60_000
            if (challenge != null && e.kind in listOf(AccountException.Kind.Expired, AccountException.Kind.TooMany)) mustResend = true
        } catch (_: Exception) { problem = AccountException.Kind.Network
        } finally { busy = false }
    }
}
