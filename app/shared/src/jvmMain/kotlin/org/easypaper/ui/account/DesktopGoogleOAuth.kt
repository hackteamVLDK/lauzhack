package org.easypaper.ui.account

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.concurrent.Executors

internal data class DesktopGoogleCode(val code: String, val verifier: String, val redirectUri: String)

internal fun newGoogleOAuthSecret(): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

internal fun googlePkceChallenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

internal fun desktopGoogleAuthUrl(clientId: String, redirectUri: String, verifier: String, state: String): URI {
    val parameters = listOf(
        "client_id" to clientId,
        "redirect_uri" to redirectUri,
        "response_type" to "code",
        "scope" to "openid email profile",
        "code_challenge" to googlePkceChallenge(verifier),
        "code_challenge_method" to "S256",
        "state" to state,
        "prompt" to "select_account",
    )
    return URI("https://accounts.google.com/o/oauth2/v2/auth?" + parameters.joinToString("&") { (key, value) ->
        "$key=${URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")}"
    })
}

/** Reject missing/duplicate state before accepting either a code or a provider cancellation. */
internal fun desktopGoogleCallback(query: String?, expectedState: String): String? {
    require(query != null && query.length <= 16_384)
    val fields = query.split('&').map { field ->
        URLDecoder.decode(field.substringBefore('='), Charsets.UTF_8) to
            URLDecoder.decode(field.substringAfter('=', ""), Charsets.UTF_8)
    }.groupBy({ it.first }, { it.second })
    val state = fields["state"]?.singleOrNull()
    require(!state.isNullOrEmpty() && expectedState.isNotEmpty() &&
        MessageDigest.isEqual(state.toByteArray(Charsets.UTF_8), expectedState.toByteArray(Charsets.UTF_8)))
    if (fields.containsKey("error")) {
        require(fields["error"]?.singleOrNull()?.isNotBlank() == true && !fields.containsKey("code"))
        return null
    }
    return fields["code"]?.singleOrNull()?.takeIf { it.isNotBlank() && it.length <= 8_192 }
        ?: throw IllegalArgumentException("Invalid OAuth callback")
}

internal suspend fun desktopGoogleAuthorization(
    clientId: String,
    openBrowser: (URI) -> Unit = ::openGoogleBrowser,
    timeoutMillis: Long = 180_000,
): DesktopGoogleCode? = withTimeoutOrNull(timeoutMillis) {
    withContext(Dispatchers.IO) {
        val verifier = newGoogleOAuthSecret()
        val state = newGoogleOAuthSecret()
        val result = CompletableDeferred<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "google-oauth-callback").apply { isDaemon = true } }
        try {
            val redirectUri = "http://127.0.0.1:${server.address.port}"
            server.executor = executor
            server.createContext("/") { exchange ->
                var accepted = false
                var code: String? = null
                val status = if (exchange.requestMethod != "GET" || exchange.requestURI.rawPath != "/") 404 else {
                    try {
                        code = desktopGoogleCallback(exchange.requestURI.rawQuery, state)
                        accepted = true
                        200
                    } catch (_: IllegalArgumentException) { 400 }
                }
                // Never reflect codes, state or provider errors into the browser page.
                val body = "<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><title>Easy Paper</title><p>You can return to Easy Paper</p></html>"
                    .toByteArray(Charsets.UTF_8)
                try {
                    exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                    exchange.responseHeaders.set("Cache-Control", "no-store")
                    exchange.responseHeaders.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
                    exchange.responseHeaders.set("Referrer-Policy", "no-referrer")
                    exchange.sendResponseHeaders(status, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                } finally {
                    exchange.close()
                    if (accepted) result.complete(code)
                }
            }
            server.start()
            openBrowser(desktopGoogleAuthUrl(clientId, redirectUri, verifier, state))
            result.await()?.let { DesktopGoogleCode(it, verifier, redirectUri) }
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }
}

private fun openGoogleBrowser(uri: URI) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri)
            return
        }
    } catch (_: Exception) { /* Fall back to the operating system's URL handler. */ }
    val os = System.getProperty("os.name").lowercase(Locale.ROOT)
    val command = when {
        os.startsWith("windows") -> listOf("rundll32", "url.dll,FileProtocolHandler", uri.toASCIIString())
        os.contains("linux") -> listOf("xdg-open", uri.toASCIIString())
        else -> throw IllegalStateException("Browser unavailable")
    }
    ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
        .start().outputStream.close()
}
