package org.easypaper

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.routing.post
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.easypaper.account.AccountClient
import org.easypaper.account.AccountException
import org.easypaper.account.AccountException.Kind
import org.easypaper.account.EmailChallenge
import org.easypaper.model.EasyPaperJson
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountsTest {
    private class Mail : MailSender {
        val sent = mutableListOf<Pair<String, String>>()
        override suspend fun send(to: String, code: String) { sent += to to code }
        val code get() = sent.last().second
    }

    private fun api(http: HttpClient) = AccountClient("http://localhost", http)

    @Test
    fun emailCodesAreSingleUseAndSessionsCanBeRevoked() = testApplication {
        val mail = Mail()
        val accounts = Accounts(mail = mail, now = { 1_000 })
        application { routing { accountRoutes(accounts) } }
        val api = api(client)
        val challenge = api.startEmail(" Alice@Example.com ")
        assertEquals(601_000, challenge.expiresAt)
        assertEquals("alice@example.com", mail.sent.single().first)
        assertTrue(mail.code.matches(Regex("[0-9]{6}")))
        val wrong = if (mail.code == "000000") "000001" else "000000"
        assertEquals(Kind.BadCode, assertFailsWith<AccountException> { api.verifyEmail(challenge.challengeId, wrong) }.kind)
        val session = api.verifyEmail(challenge.challengeId, mail.code)
        assertEquals("email", session.account.provider)
        assertTrue(session.token.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertEquals(session.account, api.me(session.token))
        assertEquals(Kind.NotFound, assertFailsWith<AccountException> { api.verifyEmail(challenge.challengeId, mail.code) }.kind)
        assertEquals(Kind.Unauthorized, assertFailsWith<AccountException> { api.me("invalid") }.kind)
        api.signOut(session.token)
        assertEquals(Kind.Unauthorized, assertFailsWith<AccountException> { api.me(session.token) }.kind)
        assertEquals(Kind.Unauthorized, assertFailsWith<AccountException> { api.signOut(session.token) }.kind)
        val second = api.startEmail("alice@example.com")
        val nextSession = api.verifyEmail(second.challengeId, mail.code)
        assertEquals(session.account.id, nextSession.account.id)
        assertNotEquals(session.token, nextSession.token)
    }

    @Test
    fun fiveFailedAttemptsLockTheChallengeAcrossIpAddresses() = testApplication {
        val mail = Mail()
        application { routing { accountRoutes(Accounts(mail = mail)) } }
        val api = api(client)
        val challenge = api.startEmail("alice@example.com")
        val wrong = if (mail.code == "999999") "000000" else "999999"
        repeat(5) { attempt ->
            val response = client.post("/account/email/verify") {
                header("X-Forwarded-For", "203.0.113.${attempt + 1}")
                contentType(ContentType.Application.Json)
                setBody("""{"challengeId":"${challenge.challengeId}","code":"$wrong"}""")
            }
            assertEquals(if (attempt == 4) HttpStatusCode.TooManyRequests else HttpStatusCode.BadRequest, response.status)
        }
        assertEquals(Kind.TooMany, assertFailsWith<AccountException> { api.verifyEmail(challenge.challengeId, mail.code) }.kind)
    }

    @Test
    fun emailChallengeExpiresAtTenMinutes() = testApplication {
        var time = 100L
        val mail = Mail()
        application { routing { accountRoutes(Accounts(mail = mail, now = { time })) } }
        val api = api(client)
        val challenge = api.startEmail("alice@example.com")
        time = challenge.expiresAt
        assertEquals(Kind.Expired, assertFailsWith<AccountException> { api.verifyEmail(challenge.challengeId, mail.code) }.kind)
    }

    @Test
    fun googleChecksAudienceIssuerVerificationAndExpiry() = testApplication {
        val valid = GoogleIdentity("our-client", "accounts.google.com", true, 2_000, "alice@example.com", "Alice")
        val verifier = GoogleVerifier { token ->
            when (token) {
                "foreign" -> valid.copy(audience = "foreign-client")
                "issuer" -> valid.copy(issuer = "attacker.example")
                "unverified" -> valid.copy(emailVerified = false)
                "expired" -> valid.copy(expiresAtSeconds = 1_000)
                "https-issuer" -> valid.copy(issuer = "https://accounts.google.com")
                else -> valid
            }
        }
        application { routing { accountRoutes(Accounts(googleVerifier = verifier, googleClientIds = setOf("our-client"), now = { 1_000_000 })) } }
        val api = api(client)
        val session = api.google("valid")
        assertEquals("google", session.account.provider)
        assertEquals("Alice", session.account.name)
        assertEquals(session.account.id, api.google("https-issuer").account.id)
        for (token in listOf("foreign", "issuer", "unverified", "expired")) {
            assertEquals(Kind.Unauthorized, assertFailsWith<AccountException> { api.google(token) }.kind)
        }
        assertEquals(HttpStatusCode.Unauthorized, client.post("/account/google") {
            setBody("""{"idToken":"foreign"}""")
        }.status)
    }

    @Test
    fun inviteRequiresClinicTokenMasksContactsAndVerifiesSavedEmail() = testApplication {
        val mail = Mail()
        application { routing { accountRoutes(Accounts(mail = mail, clinicToken = "clinic")) } }
        val api = api(client)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/invites") { setBody("invalid") }.status)
        assertEquals(Kind.Unauthorized, assertFailsWith<AccountException> { api.createInvite("wrong", "Alice", null, "alice@example.com") }.kind)
        val token = api.createInvite("clinic", "Alice", "+41 79 123 12 34", "alice@example.com")
        val preview = api.invitedPerson(token)
        assertEquals("Alice", preview.name)
        assertEquals("a***@example.com", preview.emailMasked)
        assertEquals("+41 *** ** 34", preview.phoneMasked)
        val response = client.post("/invites/$token/confirm") {
            // Caller-supplied addresses must never redirect an invitation code.
            setBody("""{"email":"attacker@example.com"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers["Cache-Control"])
        val challenge = EasyPaperJson.decodeFromString<EmailChallenge>(response.bodyAsText())
        assertEquals("alice@example.com", mail.sent.single().first)
        val duplicate = api.confirmInvite(token)
        val session = api.verifyEmail(challenge.challengeId, mail.sent.first().second)
        assertEquals("Alice", session.account.name)
        assertEquals("+41 79 123 12 34", session.account.phone)
        assertEquals("invite", session.account.provider)
        assertEquals(Kind.NotFound, assertFailsWith<AccountException> { api.confirmInvite(token) }.kind)
        assertEquals(Kind.NotFound, assertFailsWith<AccountException> { api.verifyEmail(duplicate.challengeId, mail.code) }.kind)
    }

    @Test
    fun inviteExpiryIsFourteenDaysAndIsRecheckedDuringVerification() = testApplication {
        var time = 0L
        val mail = Mail()
        application { routing { accountRoutes(Accounts(mail = mail, clinicToken = "clinic", now = { time })) } }
        val api = api(client)
        val token = api.createInvite("clinic", "Alice", null, "alice@example.com")
        time = 14 * 24 * 60 * 60_000L - 1
        val challenge = api.confirmInvite(token)
        time++
        assertEquals(Kind.Expired, assertFailsWith<AccountException> { api.invitedPerson(token) }.kind)
        assertEquals(Kind.Expired, assertFailsWith<AccountException> { api.confirmInvite(token) }.kind)
        assertEquals(Kind.Expired, assertFailsWith<AccountException> { api.verifyEmail(challenge.challengeId, mail.code) }.kind)
        assertEquals(Kind.NotFound, assertFailsWith<AccountException> { api.invitedPerson("unknown") }.kind)
    }

    @Test
    fun missingOrBrokenMailReturns503WithoutCreatingUsableChallenge() = testApplication {
        application { routing { accountRoutes(Accounts(clinicToken = "clinic")) } }
        val api = api(client)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.post("/account/email/start") {
            setBody("""{"email":"alice@example.com"}""")
        }.status)
        assertEquals(Kind.MailUnavailable, assertFailsWith<AccountException> { api.startEmail("alice@example.com") }.kind)
        val token = api.createInvite("clinic", "Alice", null, "alice@example.com")
        assertEquals(Kind.MailUnavailable, assertFailsWith<AccountException> { api.confirmInvite(token) }.kind)
    }

    @Test
    fun mailFailureIsSanitized() = testApplication {
        application { routing { accountRoutes(Accounts(mail = MailSender { _, _ -> error("secret upstream details") })) } }
        val response = client.post("/account/email/start") { setBody("""{"email":"alice@example.com"}""") }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertFalse(response.bodyAsText().contains("secret"))
    }

    @Test
    fun mailAndGeneralRateLimitsHaveIndependentIpWindows() = runTest {
        var time = 0L
        val mail = Mail()
        val accounts = Accounts(mail = mail, now = { time })
        repeat(5) { accounts.startEmail("alice@example.com", "a") }
        assertEquals(Kind.TooMany, assertFailsWith<AccountException> { accounts.startEmail("alice@example.com", "a") }.kind)
        accounts.startEmail("alice@example.com", "b")
        repeat(60) { accounts.limit("a") }
        assertEquals(Kind.TooMany, assertFailsWith<AccountException> { accounts.limit("a") }.kind)
        accounts.limit("b")
        time = 60_000
        accounts.limit("a")
        assertEquals(Kind.TooMany, assertFailsWith<AccountException> { accounts.startEmail("alice@example.com", "a") }.kind)
        time = 600_000
        accounts.startEmail("alice@example.com", "a")
        assertEquals("198.51.100.1", accountClientIp("127.0.0.1", "203.0.113.1, 198.51.100.1")) // the last address is the one Caddy wrote
        assertEquals("198.51.100.1", accountClientIp("::1", " 203.0.113.2 , 198.51.100.1"))
        assertEquals("::1", accountClientIp("::1", null))
        assertEquals("198.51.100.1", accountClientIp("198.51.100.1", "203.0.113.1"))
    }

    @Test
    fun fileSurvivesRestartAndContainsNoRawTokensOrCodes() = runTest {
        val dir = Files.createTempDirectory("easypaper-accounts-test")
        try {
            val file = dir.resolve("accounts.json")
            val mail = Mail()
            val first = Accounts(file, mail, clinicToken = "clinic")
            val invite = first.createInvite("Alice", "+41 79 123 12 34", "alice@example.com")
            val challenge = first.startEmail("bob@example.com", "a")
            val session = first.verifyEmail(challenge.challengeId, mail.code)
            val json = Files.readString(file)
            assertFalse(json.contains(session.token))
            assertFalse(json.contains(invite))
            assertFalse(json.contains(challenge.challengeId))
            val stored = EasyPaperJson.parseToJsonElement(json).jsonObject
            assertEquals(setOf("accounts", "sessions", "invites"), stored.keys)
            assertTrue(stored["sessions"]!!.jsonObject.keys.all { it.matches(Regex("[a-f0-9]{64}")) })
            assertEquals(listOf("accounts.json"), Files.list(dir).use { stream -> stream.map { it.fileName.toString() }.toList() })
            val restarted = Accounts(file, mail, clinicToken = "clinic")
            assertEquals(session.account, restarted.me(session.token))
            assertEquals("Alice", restarted.invitedPerson(invite).name)
            restarted.signOut(session.token)
            assertEquals(Kind.Unauthorized, assertFailsWith<AccountException> { Accounts(file).me(session.token) }.kind)
            Files.writeString(file, """{"accounts":{"alice@example.com":BROKEN}}""")
            val failure = assertFailsWith<IllegalStateException> { Accounts(file) }
            assertEquals("Cannot read account database", failure.message)
            assertNull(failure.cause)
        } finally { deleteTestDirectory(dir) }
    }

    @Test
    fun concurrentVerificationCreatesOnlyOneSessionAndConcurrentWritesArePreserved() = runTest {
        val dir = Files.createTempDirectory("easypaper-accounts-concurrent")
        try {
            val file = dir.resolve("accounts.json")
            val mail = Mail()
            val accounts = Accounts(file, mail)
            val challenge = accounts.startEmail("alice@example.com", "a")
            val results = coroutineScope {
                List(10) { async { runCatching { accounts.verifyEmail(challenge.challengeId, mail.code) } } }.awaitAll()
            }
            assertEquals(1, results.count { it.isSuccess })
            val tokens = coroutineScope {
                List(10) { i -> async { accounts.createInvite("Person $i", null, "person$i@example.com") } }.awaitAll()
            }
            val restarted = Accounts(file)
            tokens.forEachIndexed { i, token -> assertEquals("Person $i", restarted.invitedPerson(token).name) }
        } finally { deleteTestDirectory(dir) }
    }

    @Test
    fun resendSendsExactContractAndGooglePostsTokenToTokenInfo() = testApplication {
        externalServices {
            hosts("https://api.resend.com") {
                routing {
                    post("/emails") {
                        assertEquals("Bearer test-key", call.request.headers["Authorization"])
                        val body = EasyPaperJson.parseToJsonElement(call.receiveText()).jsonObject
                        assertEquals(JsonPrimitive("from@example.com"), body["from"])
                        assertEquals(JsonArray(listOf(JsonPrimitive("alice@example.com"))), body["to"])
                        assertEquals(JsonPrimitive("Your Easy Paper code"), body["subject"])
                        assertEquals(JsonPrimitive("Your code: 123456. It is valid for 10 minutes. If you did not ask for it, ignore this email."), body["text"])
                        call.respondText("{}")
                    }
                }
            }
            hosts("https://oauth2.googleapis.com") {
                routing {
                    post("/tokeninfo") {
                        assertEquals("test-id-token", call.request.queryParameters["id_token"])
                        call.respondText("""{"aud":"our-client","iss":"accounts.google.com","email_verified":"true","exp":"2000","email":"alice@example.com","name":"Alice"}""")
                    }
                }
            }
        }
        ResendMail(client, "test-key", "from@example.com").send("alice@example.com", "123456")
        val verified = GoogleTokenInfo(client).verify("test-id-token")
        assertEquals("our-client", verified.audience)
        assertTrue(verified.emailVerified)
        assertEquals(2_000, verified.expiresAtSeconds)
    }

    private fun deleteTestDirectory(dir: Path) {
        // Only files created inside this test's dedicated temporary directory are removed.
        Files.list(dir).use { stream -> stream.forEach { Files.deleteIfExists(it) } }
        Files.deleteIfExists(dir)
    }
}
