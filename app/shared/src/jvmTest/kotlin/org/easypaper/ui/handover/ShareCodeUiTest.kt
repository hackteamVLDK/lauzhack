package org.easypaper.ui.handover

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.*
import org.easypaper.demo.Demo
import org.easypaper.model.EasyPaperJson
import org.easypaper.report.*
import org.easypaper.ui.patient.v2.photoReference
import org.easypaper.ui.shell.Role
import org.easypaper.ui.shell.ShellState
import org.easypaper.ui.theme.EasyPaperTheme
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*
import kotlin.time.Clock

@OptIn(ExperimentalTestApi::class, InternalAPI::class, ExperimentalEncodingApi::class)
class ShareCodeUiTest {
    private class Engine(val respond: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>) : HttpClientEngineBase("share-code-test") {
        override val dispatcher = Dispatchers.Default
        override val config = HttpClientEngineConfig()
        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            val (status, body) = respond(data)
            return HttpResponseData(status, GMTDate(), Headers.Empty, HttpProtocolVersion.HTTP_1_1, ByteReadChannel(body), callContext())
        }
    }

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @Test
    fun codeThenDoctorKeyThenEncryptedUploadAndSuccess() = runComposeUiTest {
        val doctor = runBlocking { HandoffKeys.generate() }
        val claimed = CompletableDeferred<Unit>()
        val uploaded = CompletableDeferred<ByteArray>()
        val accepted = CompletableDeferred<Unit>()
        val history = AtomicInteger()
        val record = Demo.report.record.copy(entries = Demo.report.record.entries.filter { it.photoReference == null })
        val http = HttpClient(Engine { request ->
            when (request.url.encodedPath) {
                "/handoff" -> HttpStatusCode.Created to EasyPaperJson.encodeToString(HandoffSlot.serializer(),
                    HandoffSlot("123456", "creator-secret", Clock.System.now().toEpochMilliseconds() + HANDOFF_TTL_MILLIS))
                "/handoff/123456/key" -> {
                    assertEquals("creator-secret", request.headers[HANDOFF_CREATOR_HEADER])
                    claimed.await()
                    HttpStatusCode.OK to EasyPaperJson.encodeToString(HandoffPublicKey.serializer(), HandoffPublicKey(Base64.encode(doctor.publicKey)))
                }
                "/handoff/123456/report" -> {
                    assertEquals("creator-secret", request.headers[HANDOFF_CREATOR_HEADER])
                    uploaded.complete((request.body as OutgoingContent.ByteArrayContent).bytes())
                    accepted.await()
                    HttpStatusCode.Created to ""
                }
                else -> error("Unexpected request: ${request.url}")
            }
        })
        try {
            val state = ShellState(Role.Patient)
            setContent { EasyPaperTheme(false) { ShareCodeScreen(state.repository, "https://relay.test", record,
                onSent = { history.incrementAndGet() }, http = http) } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Create a code").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Create a code").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithContentDescription("1 2 3 4 5 6").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithContentDescription("1 2 3 4 5 6").assertIsDisplayed()
            onNodeWithText("Waiting for your doctor to enter the code…").assertIsDisplayed()
            assertEquals(0, history.get())
            claimed.complete(Unit)
            waitUntil(timeoutMillis = 10_000) { uploaded.isCompleted }
            onNodeWithText("Sending…").assertIsDisplayed()
            assertEquals(0, history.get())
            val opened = runBlocking { openFromPatient(uploaded.await(), doctor) }
            assertEquals(record, opened.first.record)
            assertTrue(opened.second.isEmpty())
            accepted.complete(Unit)
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Sent to your doctor").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Sent to your doctor").assertIsDisplayed()
            assertEquals(1, history.get())
        } finally { http.close() }
    }

    @Test
    fun expiredCodeCanBeReplaced() = runComposeUiTest {
        val creates = AtomicInteger()
        val http = HttpClient(Engine {
            creates.incrementAndGet()
            HttpStatusCode.Created to EasyPaperJson.encodeToString(HandoffSlot.serializer(),
                HandoffSlot("123456", "secret", Clock.System.now().toEpochMilliseconds() - 1))
        })
        try {
            val state = ShellState(Role.Patient)
            setContent { EasyPaperTheme(false) { ShareCodeScreen(state.repository, "https://relay.test", Demo.report.record, http = http) } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Create a code").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Create a code").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("The code has expired").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("New code").performClick()
            waitUntil(timeoutMillis = 10_000) { creates.get() == 2 }
        } finally { http.close() }
    }

    @Test
    fun tooManyCodesExplainsWhenToRetry() = runComposeUiTest {
        val http = HttpClient(Engine { HttpStatusCode.TooManyRequests to "" })
        val history = AtomicInteger()
        try {
            val state = ShellState(Role.Patient)
            setContent { EasyPaperTheme(false) { ShareCodeScreen(state.repository, "https://relay.test", Demo.report.record,
                onSent = { history.incrementAndGet() }, http = http) } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Create a code").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Create a code").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Too many codes, try again in a minute").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("New code").assertIsEnabled()
            assertEquals(0, history.get())
        } finally { http.close() }
    }

    @Test
    fun leavingTheScreenCancelsPolling() = runComposeUiTest {
        val polling = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val visible = mutableStateOf(true)
        val history = AtomicInteger()
        val http = HttpClient(Engine { request ->
            if (request.url.encodedPath == "/handoff") HttpStatusCode.Created to EasyPaperJson.encodeToString(HandoffSlot.serializer(),
                HandoffSlot("123456", "secret", Clock.System.now().toEpochMilliseconds() + HANDOFF_TTL_MILLIS))
            else {
                polling.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
        })
        try {
            val state = ShellState(Role.Patient)
            setContent { EasyPaperTheme(false) { if (visible.value) ShareCodeScreen(state.repository, "https://relay.test", Demo.report.record,
                onSent = { history.incrementAndGet() }, http = http) } }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Create a code").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Create a code").performClick()
            waitUntil(timeoutMillis = 10_000) { polling.isCompleted }
            runOnIdle { visible.value = false }
            waitUntil(timeoutMillis = 10_000) { cancelled.isCompleted }
            assertEquals(0, history.get())
        } finally { http.close() }
    }
}
