package org.easypaper

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.easypaper.account.AccountClient
import org.easypaper.account.AccountSession
import org.easypaper.model.EasyPaperJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleCodeTest {
    private val verifier = "v".repeat(43)
    private val secret = "test-only-desktop-secret"
    private fun body(redirect: String = "http://127.0.0.1:54321", code: String = "authorization-code", pkce: String = verifier) =
        """{"code":"$code","codeVerifier":"$pkce","redirectUri":"$redirect"}"""

    @Test
    fun exchangesFormAndVerifiesTokenThroughTokenInfoWithoutNetwork() = testApplication {
        var exchanges = 0
        var checks = 0
        externalServices {
            hosts("https://oauth2.googleapis.com") {
                routing {
                    post("/token") {
                        val form = call.receiveParameters()
                        assertEquals(setOf("grant_type", "client_id", "client_secret", "code", "code_verifier", "redirect_uri"), form.names())
                        assertEquals("authorization_code", form["grant_type"])
                        assertEquals("desktop-client", form["client_id"])
                        assertEquals(secret, form["client_secret"])
                        assertEquals("authorization-code", form["code"])
                        assertEquals(verifier, form["code_verifier"])
                        assertTrue(form["redirect_uri"] in setOf("http://127.0.0.1:54321", "http://localhost:54321"))
                        exchanges++
                        call.respondText("""{"id_token":"desktop-id-token","access_token":"unused-access-token"}""")
                    }
                    post("/tokeninfo") {
                        assertEquals("desktop-id-token", call.request.queryParameters["id_token"])
                        checks++
                        call.respondText("""{"aud":"desktop-client","iss":"https://accounts.google.com","email_verified":"true","exp":"2000","email":"alice@example.com","name":"Alice"}""")
                    }
                }
            }
        }
        val accounts = Accounts(googleVerifier = GoogleTokenInfo(client), googleClientIds = setOf("web-client"),
            googleDesktopClientId = "desktop-client", googleCodeExchange = GoogleTokenExchange(client, "desktop-client", secret), now = { 1_000_000 })
        application { routing { accountRoutes(accounts) } }
        val response = client.post("/account/google/code") { setBody(body()) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers["Cache-Control"])
        val text = response.bodyAsText()
        for (sensitive in listOf(secret, "desktop-id-token", "unused-access-token", "authorization-code", verifier)) assertFalse(sensitive in text)
        val session = EasyPaperJson.decodeFromString<AccountSession>(text)
        assertEquals("google", session.account.provider)
        assertEquals("Alice", session.account.name)
        val api = AccountClient("http://localhost", client)
        assertEquals(session.account, api.me(session.token))
        assertEquals(session.account.id, api.googleCode("authorization-code", verifier, "http://localhost:54321").account.id)
        assertEquals(session.account.id, api.google("desktop-id-token").account.id)
        assertEquals(2, exchanges)
        assertEquals(3, checks)
    }

    @Test
    fun invalidRedirectsAndPkceAre400BeforeExchange() = testApplication {
        val accounts = Accounts(googleDesktopClientId = "desktop-client", googleCodeExchange = GoogleCodeExchange { _, _, _ -> error("Must not exchange") })
        application { routing { accountRoutes(accounts) } }
        for (redirect in listOf("https://127.0.0.1:54321", "http://example.com:54321", "http://127.0.0.1.evil:54321",
            "http://localhost", "http://127.0.0.1:0", "http://localhost:65536", "http://user@localhost:54321",
            "http://127.0.0.1:54321/path", "http://127.0.0.1:54321?x=1", "http://localhost:54321#fragment",
            "http://[::1]:54321", "http://localhost:54321/")) {
            assertEquals(HttpStatusCode.BadRequest, client.post("/account/google/code") { setBody(body(redirect)) }.status, redirect)
        }
        for (pkce in listOf("v".repeat(42), "v".repeat(129), "=".repeat(43))) {
            assertEquals(HttpStatusCode.BadRequest, client.post("/account/google/code") { setBody(body(pkce = pkce)) }.status)
        }
    }

    @Test
    fun missingDesktopConfigurationIs503() {
        for (accounts in listOf(Accounts(), Accounts(googleDesktopClientId = "desktop-client"),
            Accounts(googleCodeExchange = GoogleCodeExchange { _, _, _ -> error("Must not exchange") }))) {
            testApplication {
                application { routing { accountRoutes(accounts) } }
                val response = client.post("/account/google/code") { setBody(body()) }
                assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
                assertFalse(secret in response.bodyAsText())
            }
        }
    }

    @Test
    fun googleErrorsNeverExposeSecretOrUpstreamDetails() {
        for (status in listOf(HttpStatusCode.BadRequest, HttpStatusCode.InternalServerError, HttpStatusCode.OK)) {
            testApplication {
                externalServices {
                    hosts("https://oauth2.googleapis.com") {
                        routing { post("/token") { call.respondText("private upstream details: $secret", status = status) } }
                    }
                }
                val accounts = Accounts(googleDesktopClientId = "desktop-client",
                    googleCodeExchange = GoogleTokenExchange(client, "desktop-client", secret))
                application { routing { accountRoutes(accounts) } }
                val response = client.post("/account/google/code") { setBody(body()) }
                assertEquals(if (status == HttpStatusCode.BadRequest) HttpStatusCode.Unauthorized else HttpStatusCode.BadGateway, response.status)
                val text = response.bodyAsText()
                assertFalse(secret in text)
                assertFalse("private upstream details" in text)
            }
        }
    }

    @Test
    fun codeRouteRequiresDesktopAudienceAndSharesGoogleRateLimit() = testApplication {
        val accounts = Accounts(googleClientIds = setOf("web-client"), googleDesktopClientId = "desktop-client",
            googleCodeExchange = GoogleCodeExchange { _, _, _ -> "id-token" },
            googleVerifier = GoogleVerifier { GoogleIdentity("web-client", "accounts.google.com", true, 2_000, "alice@example.com") },
            now = { 1_000_000 })
        application { routing { accountRoutes(accounts) } }
        repeat(30) {
            assertEquals(HttpStatusCode.Unauthorized, client.post("/account/google/code") { setBody(body()) }.status)
            assertEquals(HttpStatusCode.OK, client.post("/account/google") { setBody("""{"idToken":"id-token"}""") }.status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, client.post("/account/google/code") { setBody(body()) }.status)
    }
}
