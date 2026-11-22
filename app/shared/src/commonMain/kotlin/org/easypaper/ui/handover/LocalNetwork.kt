package org.easypaper.ui.handover

import androidx.compose.runtime.Composable

/**
 * Asks for access to the local network where the platform needs it before the app may reach the relay on the
 * clinic's or the demo laptop (Android 17, API 37: ACCESS_LOCAL_NETWORK), then calls back with the answer.
 * Asked at the moment the patient shares, not at start (HIG: ask in context). Other platforms answer true at once.
 */
@Composable
expect fun rememberLocalNetworkAccess(): (onResult: (granted: Boolean) -> Unit) -> Unit
