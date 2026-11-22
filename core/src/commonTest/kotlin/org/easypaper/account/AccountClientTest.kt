package org.easypaper.account

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.easypaper.model.CareRequest
import org.easypaper.model.EasyPaperJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalAPI::class)
class AccountClientTest {
    private class Engine(
        override val dispatcher: CoroutineDispatcher,
        private val respond: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ) : HttpClientEngineBase("account-test") {
        override val config = HttpClientEngineConfig()
        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            val (status, body) = respond(data)
            return HttpResponseData(status, GMTDate(), Headers.Empty, HttpProtocolVersion.HTTP_1_1, ByteReadChannel(body), callContext())
        }
    }

    private val accountJson = """{"id":"account-id","email":"alice@example.com","name":"Alice","phone":null,"provider":"email"}"""
    private val sessionJson = """{"token":"session-token","account":$accountJson}"""
    private val challengeJson = """{"challengeId":"challenge","expiresAt":600000}"""

    @Test
    fun allRoutesUseContractBodiesHeadersAndResponsesWithoutPlugins() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val http = HttpClient(Engine(StandardTestDispatcher(testScheduler)) { data ->
            requests += data
            val body = when (data.url.encodedPath) {
                "/api/account/email/start", "/api/invites/invite-token/confirm" -> challengeJson
                "/api/account/email/verify", "/api/account/google" -> sessionJson
                "/api/account/me" -> accountJson
                "/api/invites/invite-token" -> """{"name":"Alice","phoneMasked":"+41 *** ** 34","emailMasked":"a***@example.com"}"""
                "/api/invites" -> """{"inviteToken":"invite-token"}"""
                "/api/account/signout" -> ""
                else -> error("Unexpected path: ${data.url.encodedPath}")
            }
            (if (body.isEmpty()) HttpStatusCode.NoContent else HttpStatusCode.OK) to body
        })
        try {
            val api = AccountClient("https://example.test/api", http)
            assertEquals(EmailChallenge("challenge", 600_000), api.startEmail("alice@example.com"))
            assertEquals("session-token", api.verifyEmail("challenge", "123456").token)
            assertEquals("account-id", api.google("google-id-token").account.id)
            assertEquals("a***@example.com", api.invitedPerson("invite-token").emailMasked)
            assertEquals("challenge", api.confirmInvite("invite-token").challengeId)
            assertEquals("Alice", api.me("session-token").name)
            api.signOut("session-token")
            assertEquals("invite-token", api.createInvite("clinic-token", "Alice \"A\"", null, "alice@example.com"))
            assertEquals(listOf(HttpMethod.Post, HttpMethod.Post, HttpMethod.Post, HttpMethod.Get, HttpMethod.Post, HttpMethod.Get, HttpMethod.Post, HttpMethod.Post), requests.map { it.method })
            fun body(index: Int) = EasyPaperJson.parseToJsonElement((requests[index].body as TextContent).text).jsonObject
            assertEquals(setOf("email"), body(0).keys)
            assertEquals(JsonPrimitive("alice@example.com"), body(0)["email"])
            assertEquals(JsonPrimitive("challenge"), body(1)["challengeId"])
            assertEquals(JsonPrimitive("123456"), body(1)["code"])
            assertEquals(JsonPrimitive("google-id-token"), body(2)["idToken"])
            assertEquals("Bearer session-token", requests[5].headers["Authorization"])
            assertEquals("Bearer session-token", requests[6].headers["Authorization"])
            assertEquals("clinic-token", requests[7].headers["X-EasyPaper-Clinic-Token"])
            assertEquals(JsonPrimitive("Alice \"A\""), body(7)["name"])
            assertEquals(JsonNull, body(7)["phone"])
            assertNull(requests[0].headers["Authorization"])
        } finally { http.close() }
    }

    @Test
    fun httpErrorsAreTypedEvenWhenExpectSuccessIsEnabled() = runTest {
        val errors = mapOf(
            HttpStatusCode.BadRequest to AccountException.Kind.BadCode,
            HttpStatusCode.Gone to AccountException.Kind.Expired,
            HttpStatusCode.TooManyRequests to AccountException.Kind.TooMany,
            HttpStatusCode.ServiceUnavailable to AccountException.Kind.MailUnavailable,
            HttpStatusCode.Unauthorized to AccountException.Kind.Unauthorized,
            HttpStatusCode.Forbidden to AccountException.Kind.Unauthorized,
            HttpStatusCode.NotFound to AccountException.Kind.NotFound,
            HttpStatusCode.BadGateway to AccountException.Kind.Network,
            HttpStatusCode.InternalServerError to AccountException.Kind.Network,
        )
        for ((status, kind) in errors) {
            val http = HttpClient(Engine(StandardTestDispatcher(testScheduler)) { status to "private server details" }) { expectSuccess = true }
            try {
                val failure = assertFailsWith<AccountException> { AccountClient("https://example.test", http).startEmail("alice@example.com") }
                assertEquals(kind, failure.kind)
                assertTrue("private" !in failure.message.orEmpty())
            } finally { http.close() }
        }
    }

    @Test
    fun transportAndMalformedResponsesAreNetworkErrors() = runTest {
        for (response in listOf("invalid-json", "{}")) {
            val http = HttpClient(Engine(StandardTestDispatcher(testScheduler)) { HttpStatusCode.OK to response })
            try {
                assertEquals(AccountException.Kind.Network, assertFailsWith<AccountException> { AccountClient("https://example.test", http).startEmail("alice@example.com") }.kind)
            } finally { http.close() }
        }
        val http = HttpClient(Engine(StandardTestDispatcher(testScheduler)) { throw IOException("private details") })
        try {
            assertEquals(AccountException.Kind.Network, assertFailsWith<AccountException> { AccountClient("https://example.test", http).me("token") }.kind)
        } finally { http.close() }
    }

    @Test
    fun cancellationPropagatesAndPathTokensCannotAddSegments() = runTest {
        val http = HttpClient(Engine(StandardTestDispatcher(testScheduler)) { data ->
            assertEquals("/invites/a%2Fb/confirm", data.url.encodedPath)
            throw CancellationException("cancelled")
        })
        try {
            assertFailsWith<CancellationException> { AccountClient("https://example.test", http).confirmInvite("a/b") }
        } finally { http.close() }
    }

    @Test
    fun personTokenIsOptionalAndSurvivesSerialization() {
        val legacy = """{"id":"r","createdAt":0,"doctorName":"Dr","complaint":"c","startDate":"2026-11-22","days":1,"locale":"en","widgets":[]}"""
        val request = EasyPaperJson.decodeFromString<CareRequest>(legacy)
        assertNull(request.personToken)
        val withPerson = request.copy(personToken = "invite-token")
        assertEquals(withPerson, EasyPaperJson.decodeFromString<CareRequest>(EasyPaperJson.encodeToString(CareRequest.serializer(), withPerson)))
    }
}
