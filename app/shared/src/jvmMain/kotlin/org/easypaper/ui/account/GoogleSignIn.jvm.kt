package org.easypaper.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal actual val googleSignInSupported: Boolean = true
internal actual val googleSignInClientId: String
    get() = System.getProperty("easypaper.googleDesktopClientId")?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv("EASYPAPER_GOOGLE_DESKTOP_CLIENT_ID")?.trim()?.takeIf { it.isNotEmpty() }
        ?: GOOGLE_DESKTOP_CLIENT_ID

private class DesktopGoogleAttempt {
    var job: Job? = null
}

@Composable
actual fun rememberGoogleSignIn(): (onResult: (GoogleSignInCredential?) -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    val attempt = remember { DesktopGoogleAttempt() }
    return remember(scope, attempt) {
        { onResult ->
            attempt.job?.cancel()
            attempt.job = scope.launch {
                val code = try {
                    if (googleSignInConfigured) desktopGoogleAuthorization(googleSignInClientId) else null
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                onResult(code?.let { credential ->
                    GoogleSignInCredential { client -> client.googleCode(credential.code, credential.verifier, credential.redirectUri) }
                })
            }
            Unit
        }
    }
}
