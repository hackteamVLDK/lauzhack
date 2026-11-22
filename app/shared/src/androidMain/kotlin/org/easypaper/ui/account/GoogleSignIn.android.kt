package org.easypaper.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal actual val googleSignInSupported: Boolean = true

@Composable
actual fun rememberGoogleSignIn(): (onResult: (idToken: String?) -> Unit) -> Unit {
    val context = LocalContext.current
    val manager = remember(context) { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()
    return remember(context, manager, scope) {
        { onResult ->
            if (!googleSignInConfigured) onResult(null)
            else scope.launch {
                val token = try {
                    val option = GetSignInWithGoogleOption.Builder(GOOGLE_WEB_CLIENT_ID).build()
                    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                    val credential = manager.getCredential(context, request).credential
                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                        GoogleIdTokenCredential.createFrom(credential.data).idToken else null
                } catch (e: CancellationException) { throw e
                } catch (_: Exception) { null }
                onResult(token)
            }
            Unit
        }
    }
}
