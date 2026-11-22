package org.easypaper.ui.shell

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.easypaper.ai.AiClient

/**
 * Address of the Easy Paper server (#8/#29) the doctor app talks to; null where the app does not use it
 * (the patient app on Android). Web: the page's own origin, the server serves the doctor app.
 */
expect fun defaultServerUrl(): String?

/**
 * The team's public Easy Paper server (#63): relay, AI routes behind the clinic token, the doctor's web app, on the
 * hub VM behind Caddy with a Let's Encrypt certificate. A local server is set in the developer menu (or
 * EASYPAPER_SERVER on Desktop).
 */
const val PUBLIC_SERVER = "https://74-148-56-1.sslip.io"

/** The AI summary takes one model call on the server; give it time, but not forever. */
private const val AI_TIMEOUT_MILLIS = 120_000L

/** The doctor app's connection to the server; the platform's Ktor engine is found on the classpath. */
class ServerConnection(val baseUrl: String, private val aiToken: () -> String? = { null }) {
    private val http = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = AI_TIMEOUT_MILLIS
            socketTimeoutMillis = AI_TIMEOUT_MILLIS
        }
    }
    val ai: AiClient get() = AiClient(baseUrl.trimEnd('/'), http, token = aiToken())
}
