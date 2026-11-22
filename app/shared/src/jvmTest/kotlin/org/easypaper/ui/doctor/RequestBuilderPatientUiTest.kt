package org.easypaper.ui.doctor

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
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
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.easypaper.invite.InviteResult
import org.easypaper.invite.inviteLink
import org.easypaper.invite.parseInvite
import org.easypaper.model.CareRequest
import org.easypaper.model.EasyPaperJson
import org.easypaper.ui.theme.EasyPaperTheme
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class, InternalAPI::class)
class RequestBuilderPatientUiTest {
    private class Engine(
        val respond: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ) : HttpClientEngineBase("patient-invite-test") {
        override val dispatcher = Dispatchers.Default
        override val config = HttpClientEngineConfig()
        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            val (status, body) = respond(data)
            return HttpResponseData(status, GMTDate(), Headers.Empty, HttpProtocolVersion.HTTP_1_1,
                ByteReadChannel(body), callContext())
        }
    }

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.type(description: String, text: String) {
        val field = hasSetTextAction() and hasContentDescription(description)
        onNode(hasScrollAction()).performScrollToNode(field)
        onNode(field).performTextInput(text)
    }

    private fun ComposeUiTest.click(text: String) {
        onNode(hasScrollAction()).performScrollToNode(hasText(text))
        onNodeWithText(text).performClick()
    }

    private fun ComposeUiTest.fillRequest() {
        waitForText("New request")
        type("For example: headache in the morning for two weeks", "Morning headache")
        type("Dr. Anna Keller", "Dr. Weber")
    }

    @Test
    fun emailAndClinicTokenCreateALinkWithThePersonToken() {
        val received = CompletableDeferred<HttpRequestData>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val http = HttpClient(Engine { request ->
            calls.incrementAndGet()
            received.complete(request)
            release.await()
            HttpStatusCode.Created to """{"inviteToken":"person-token"}"""
        })
        try {
            runComposeUiTest {
                var created: CareRequest? = null
                setContent {
                    EasyPaperTheme(false) {
                        RequestBuilderScreen(null, serverUrl = "https://clinic.test", clinicToken = "clinic-secret",
                            http = http, onCreated = { created = it })
                    }
                }
                fillRequest()
                type("Name", "  Anna Keller  ")
                type("Phone", "  +41 79 123 45 67  ")
                type("Email", "  anna@example.com  ")
                click("Create invite")
                waitUntil(timeoutMillis = 10_000) { received.isCompleted }
                onNodeWithText("Create invite").assertIsNotEnabled()
                assertNull(created)
                release.complete(Unit)
                waitForText("Copy link")
                waitForText("For: Anna Keller, a***@example.com")

                val request = assertNotNull(created)
                assertEquals("person-token", request.personToken)
                assertEquals(request, assertIs<InviteResult.Ok>(parseInvite(inviteLink(request))).request)
                val json = EasyPaperJson.encodeToString(CareRequest.serializer(), request)
                assertFalse(json.contains("Anna Keller"))
                assertFalse(json.contains("anna@example.com"))
                assertFalse(json.contains("+41 79"))
            }
            val sent = runBlocking { received.await() }
            assertEquals(HttpMethod.Post, sent.method)
            assertEquals("/invites", sent.url.encodedPath)
            assertEquals("clinic-secret", sent.headers["X-EasyPaper-Clinic-Token"])
            val body = EasyPaperJson.parseToJsonElement((sent.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
            assertEquals("Anna Keller", body.getValue("name").jsonPrimitive.content)
            assertEquals("+41 79 123 45 67", body.getValue("phone").jsonPrimitive.content)
            assertEquals("anna@example.com", body.getValue("email").jsonPrimitive.content)
            assertEquals(1, calls.get())
        } finally {
            release.complete(Unit)
            http.close()
        }
    }

    @Test
    fun missingClinicTokenExplainsTheProblemAndOffersAnAnonymousInvite() {
        val calls = AtomicInteger()
        val http = HttpClient(Engine {
            calls.incrementAndGet()
            HttpStatusCode.InternalServerError to ""
        })
        try {
            runComposeUiTest {
                var created: CareRequest? = null
                setContent {
                    EasyPaperTheme(false) {
                        RequestBuilderScreen(null, serverUrl = "https://clinic.test", clinicToken = "  ",
                            http = http, onCreated = { created = it })
                    }
                }
                fillRequest()
                type("Name", "Anna Keller")
                type("Email", "anna@example.com")
                click("Create invite")
                waitForText("Add the clinic token in settings to invite by name and email")
                assertNull(created)
                assertEquals(0, calls.get())
                click("Create invite without patient details")
                waitForText("Copy link")
                val request = assertNotNull(created)
                assertNull(request.personToken)
                assertEquals(request, assertIs<InviteResult.Ok>(parseInvite(inviteLink(request))).request)
                assertEquals(0, onAllNodesWithText("For:", substring = true).fetchSemanticsNodes().size)
                assertEquals(0, calls.get())
            }
        } finally { http.close() }
    }

    @Test
    fun noEmailUsesTheExistingOfflineInviteFlow() {
        val calls = AtomicInteger()
        val http = HttpClient(Engine {
            calls.incrementAndGet()
            HttpStatusCode.InternalServerError to ""
        })
        try {
            runComposeUiTest {
                var created: CareRequest? = null
                setContent {
                    EasyPaperTheme(false) { RequestBuilderScreen(null, http = http, onCreated = { created = it }) }
                }
                fillRequest()
                type("Name", "Anna Keller")
                type("Phone", "+41 79 123 45 67")
                click("Create invite")
                waitForText("Copy link")
                val request = assertNotNull(created)
                assertNull(request.personToken)
                assertEquals(request, assertIs<InviteResult.Ok>(parseInvite(inviteLink(request))).request)
                assertEquals("Dr. Weber", request.doctorName)
                assertEquals(listOf("q", "bp", "m", "d", "dq"), request.widgets.map { it.id })
                assertEquals(0, calls.get())
            }
        } finally { http.close() }
    }

    @Test
    fun networkFailureCanBeRetriedOrSkipped() {
        val calls = AtomicInteger()
        val http = HttpClient(Engine {
            calls.incrementAndGet()
            throw java.io.IOException("offline")
        })
        try {
            runComposeUiTest {
                var created: CareRequest? = null
                setContent {
                    EasyPaperTheme(false) {
                        RequestBuilderScreen(null, serverUrl = "https://clinic.test", clinicToken = "clinic-secret",
                            http = http, onCreated = { created = it })
                    }
                }
                fillRequest()
                type("Email", "anna@example.com")
                click("Create invite")
                waitForText("Could not reach the server. Check your connection and try again.")
                assertNull(created)
                assertEquals(1, calls.get())
                click("Create invite")
                waitUntil(timeoutMillis = 10_000) { calls.get() == 2 }
                waitForText("Could not reach the server. Check your connection and try again.")
                click("Create invite without patient details")
                waitForText("Copy link")
                assertNull(assertNotNull(created).personToken)
                assertEquals(2, calls.get())
            }
        } finally { http.close() }
    }

    @Test
    fun invalidEmailIsRejectedBeforeAnyHttpRequest() {
        val calls = AtomicInteger()
        val http = HttpClient(Engine {
            calls.incrementAndGet()
            HttpStatusCode.InternalServerError to ""
        })
        try {
            runComposeUiTest {
                var created: CareRequest? = null
                setContent {
                    EasyPaperTheme(false) {
                        RequestBuilderScreen(null, serverUrl = "https://clinic.test", clinicToken = "clinic-secret",
                            http = http, onCreated = { created = it })
                    }
                }
                fillRequest()
                type("Email", "anna@example")
                click("Create invite")
                waitForText("Enter a valid email address")
                assertNull(created)
                assertEquals(0, calls.get())
                click("Create invite without patient details")
                waitForText("Copy link")
                assertNull(assertNotNull(created).personToken)
                assertEquals(0, calls.get())
            }
        } finally { http.close() }
    }
}
