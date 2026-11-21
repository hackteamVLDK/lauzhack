package org.easypaper.ui.handover

import androidx.compose.runtime.Composable

// shortcut: the doctor's web app ships as Wasm; the JS fallback build pastes the code. Port the Wasm reader if JS is used.
@Composable
actual fun rememberQrReader(onCode: (String) -> Unit, onFailure: () -> Unit): (() -> Unit)? = null
