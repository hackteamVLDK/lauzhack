package org.easypaper.ui.account

import androidx.compose.runtime.Composable
import org.easypaper.account.AccountClient
import org.easypaper.account.AccountSession

/** A platform credential is redeemed by AccountFlow, keeping session persistence and errors shared. */
fun interface GoogleSignInCredential {
    suspend fun signIn(client: AccountClient): AccountSession
}

internal fun googleIdTokenCredential(token: String?): GoogleSignInCredential? =
    token?.takeIf { it.isNotBlank() }?.let { idToken -> GoogleSignInCredential { it.google(idToken) } }

@Composable
expect fun rememberGoogleSignIn(): (onResult: (GoogleSignInCredential?) -> Unit) -> Unit

internal expect val googleSignInSupported: Boolean
