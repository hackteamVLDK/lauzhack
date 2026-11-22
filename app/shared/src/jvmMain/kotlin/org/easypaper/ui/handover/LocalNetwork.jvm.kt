package org.easypaper.ui.handover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** No local network permission on this platform. */
@Composable
actual fun rememberLocalNetworkAccess(): (onResult: (granted: Boolean) -> Unit) -> Unit = remember { { onResult -> onResult(true) } }
