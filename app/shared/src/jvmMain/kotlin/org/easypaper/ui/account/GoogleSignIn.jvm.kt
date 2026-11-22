package org.easypaper.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

internal actual val googleSignInSupported: Boolean = false

@Composable
actual fun rememberGoogleSignIn(): (onResult: (idToken: String?) -> Unit) -> Unit =
    remember { { onResult -> onResult(null) } }
