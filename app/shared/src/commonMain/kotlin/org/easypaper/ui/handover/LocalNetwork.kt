package org.easypaper.ui.handover

import androidx.compose.runtime.Composable

/**
 * Asks for access to the local network where the platform needs it before the app may reach the relay on the
 * clinic's or the demo laptop (Android 17, API 37: ACCESS_LOCAL_NETWORK), then calls back with the answer.
 * Asked at the moment the patient shares, not at start (HIG: ask in context). Other platforms answer true at once.
 */
@Composable
expect fun rememberLocalNetworkAccess(): (onResult: (granted: Boolean) -> Unit) -> Unit

/**
 * Whether [url] points into the local network (10.x, 172.16–31.x, 192.168.x, 169.254.x, loopback, localhost, *.local):
 * only then Android 17 needs the permission; the team's public server needs none.
 */
fun isLocalNetworkUrl(url: String): Boolean {
    val host = url.substringAfter("://").substringBefore('/').let { authority ->
        if (authority.startsWith("[")) authority.substringAfter('[').substringBefore(']') else authority.substringBefore(':')
    }.lowercase()
    if (host == "localhost" || host.endsWith(".local") || host == "::1") return true
    val parts = host.split('.').map { it.toIntOrNull() ?: return false }
    if (parts.size != 4) return false
    val (a, b) = parts
    return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
}
