package org.easypaper.ui.account

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopGoogleOAuthTest {
    private fun parameters(uri: URI) = uri.rawQuery.split('&').associate {
        URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
    }

    @Test
    fun authUrlUsesS256AndRfc7636ChallengeWithoutPadding() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val uri = desktopGoogleAuthUrl("desktop-client", "http://127.0.0.1:54321", verifier, "random-state")
        assertEquals("https", uri.scheme)
        assertEquals("accounts.google.com", uri.host)
        assertEquals("/o/oauth2/v2/auth", uri.path)
        assertEquals(mapOf(
            "client_id" to "desktop-client", "redirect_uri" to "http://127.0.0.1:54321",
            "response_type" to "code", "scope" to "openid email profile",
            "code_challenge" to "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            "code_challenge_method" to "S256", "state" to "random-state", "prompt" to "select_account",
        ), parameters(uri))
        assertTrue("scope=openid%20email%20profile" in uri.rawQuery)
        assertFalse(parameters(uri).getValue("code_challenge").contains('='))
        assertFalse(uri.toString().contains(verifier))
        val secret = newGoogleOAuthSecret()
        assertTrue(Regex("[A-Za-z0-9_-]{43,128}").matches(secret))
        assertNotEquals(secret, newGoogleOAuthSecret())
    }

    @Test
    fun callbackRequiresExactStateIncludingForCancellation() {
        assertEquals("a+b/c", desktopGoogleCallback("code=a%2Bb%2Fc&state=expected", "expected"))
        assertNull(desktopGoogleCallback("error=access_denied&state=expected", "expected"))
        for (query in listOf(null, "code=x", "code=x&state=foreign", "code=x&state=",
            "code=x&state=expected&state=expected", "code=x&code=y&state=expected",
            "state=expected", "code=&state=expected", "error=access_denied&state=foreign",
            "error=access_denied&code=x&state=expected", "code=x&state=%XX")) {
            assertFailsWith<IllegalArgumentException> { desktopGoogleCallback(query, "expected") }
        }
    }

    @Test
    fun loopbackRejectsForeignStateThenAcceptsCodeAndCloses() = runBlocking {
        val opened = CompletableDeferred<URI>()
        val pending = async { desktopGoogleAuthorization("client", { opened.complete(it); Unit }, 5_000) }
        val auth = withTimeout(5_000) { opened.await() }
        val fields = parameters(auth)
        val redirect = fields.getValue("redirect_uri")
        fun callback(query: String): Pair<Int, String> {
            val connection = URI("$redirect/?$query").toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            return try {
                val status = connection.responseCode
                status to (if (status >= 400) connection.errorStream else connection.inputStream).bufferedReader().use { it.readText() }
            } finally { connection.disconnect() }
        }
        assertEquals(400, callback("code=foreign-code&state=foreign").first)
        assertFalse(pending.isCompleted)
        val response = callback("code=accepted-code&state=${fields.getValue("state")}")
        assertEquals(200, response.first)
        assertTrue("You can return to Easy Paper" in response.second)
        assertFalse("accepted-code" in response.second)
        val code = withTimeout(5_000) { pending.await() }!!
        assertEquals("accepted-code", code.code)
        assertEquals(redirect, code.redirectUri)
        assertEquals(fields["code_challenge"], googlePkceChallenge(code.verifier))
        assertListenerClosed(URI(redirect))
    }

    @Test
    fun timeoutBrowserFailureAndCancellationCloseListener() = runBlocking {
        var timedOutUri: URI? = null
        assertNull(desktopGoogleAuthorization("client", { timedOutUri = URI(parameters(it).getValue("redirect_uri")) }, 200))
        assertListenerClosed(timedOutUri!!)
        var failedUri: URI? = null
        assertFailsWith<IllegalStateException> {
            desktopGoogleAuthorization("client", {
                failedUri = URI(parameters(it).getValue("redirect_uri"))
                error("Browser unavailable")
            })
        }
        assertListenerClosed(failedUri!!)
        val opened = CompletableDeferred<URI>()
        val pending = async { desktopGoogleAuthorization("client", { opened.complete(it); Unit }) }
        val uri = withTimeout(5_000) { opened.await() }
        pending.cancelAndJoin()
        assertListenerClosed(URI(parameters(uri).getValue("redirect_uri")))
    }

    private fun assertListenerClosed(uri: URI) {
        assertFailsWith<java.io.IOException> { Socket().use { it.connect(InetSocketAddress(uri.host, uri.port), 1_000) } }
    }
}
