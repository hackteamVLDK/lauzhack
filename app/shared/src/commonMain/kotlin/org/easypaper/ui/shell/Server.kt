package org.easypaper.ui.shell

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.easypaper.ai.AiClient

/**
 * Address of the Easy Paper server (#8/#29) the doctor app talks to; null where the app does not use it
 * (the patient app on Android). Web: the page's own origin, the server serves the doctor app.
 */
expect fun defaultServerUrl(): String?

/** The AI summary takes one model call on the server; give it time, but not forever. */
private const val AI_TIMEOUT_MILLIS = 120_000L

/** The doctor app's connection to the server; the platform's Ktor engine is found on the classpath. */
class ServerConnection(val baseUrl: String) {
    private val http = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = AI_TIMEOUT_MILLIS
            socketTimeoutMillis = AI_TIMEOUT_MILLIS
        }
    }
    val ai = AiClient(baseUrl.trimEnd('/'), http)
}
