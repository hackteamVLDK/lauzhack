package org.easypaper.ui.account

import androidx.compose.runtime.Composable

@Composable
expect fun rememberGoogleSignIn(): (onResult: (idToken: String?) -> Unit) -> Unit

internal expect val googleSignInSupported: Boolean
