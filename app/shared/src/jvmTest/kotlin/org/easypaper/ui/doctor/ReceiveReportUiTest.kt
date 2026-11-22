package org.easypaper.ui.doctor

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
import kotlinx.coroutines.awaitCancellation
import org.easypaper.demo.Demo
import org.easypaper.model.EasyPaperJson
import org.easypaper.report.HANDOFF_CLAIM_HEADER
import org.easypaper.report.HANDOFF_CLINIC_HEADER
import org.easypaper.report.HandoffPublicKey
import org.easypaper.report.ReceivedReport
import org.easypaper.report.sealForDoctor
import org.easypaper.ui.handover.OpenReportScreen
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellTheme
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Uses the real screen, handoff HTTP client, ECDH encryption and photo unpacking. */
@OptIn(ExperimentalTestApi::class, InternalAPI::class, ExperimentalEncodingApi::class)
class ReceiveReportUiTest {
    private class Engine(val respond: suspend (HttpRequestData) -> Pair<HttpStatusCode, ByteArray>) : HttpClientEngineBase("doctor-receive-test") {
        override val dispatcher = Dispatchers.Default
        override val config = HttpClientEngineConfig()
        val requests = CopyOnWriteArrayList<HttpRequestData>()

        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            requests += data
            val (status, body) = respond(data)
            return HttpResponseData(status, GMTDate(), Headers.Empty, HttpProtocolVersion.HTTP_1_1, ByteReadChannel(body), callContext())
        }
    }

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.openCodeScreen(http: HttpClient, token: String? = "clinic", onOpened: (ReceivedReport) -> Unit = {}) {
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                OpenReportScreen("https://example.test", onOpened, back = null, clinicToken = token, httpClient = http)
            }
        }
        waitForText("Enter a 6-digit code")
        onNodeWithText("Enter a 6-digit code").performScrollTo().performClick()
        waitForText("Receive")
    }

    private fun ComposeUiTest.codeField() = onNode(hasSetTextAction() and hasContentDescription("6-digit code"))

    @Test
    fun incompleteCodeCannotBeReceivedAndSpacedPasteIsAccepted() {
        val engine = Engine { error("Typing must not send a request") }
        val http = HttpClient(engine)
        try {
            runComposeUiTest {
                openCodeScreen(http)
                onNodeWithText("Receive").assertIsNotEnabled()
                codeField().performScrollTo().performTextInput("12345")
                onNodeWithText("Receive").assertIsNotEnabled()
                codeField().performTextReplacement("123 456")
                codeField().assertTextEquals("123456")
                onNodeWithText("Receive").assertIsEnabled()
                assertTrue(engine.requests.isEmpty())
            }
        } finally { http.close() }
    }

    @Test
    fun missingClinicTokenExplainsSettingsAndDoesNotSendRequests() {
        val engine = Engine { error("A missing token must never contact the relay") }
        val http = HttpClient(engine)
        try {
            runComposeUiTest {
                openCodeScreen(http, token = null)
                waitForText("Add the clinic token in settings to receive by code")
                codeField().performScrollTo().performTextInput("123456")
                onNodeWithText("Receive").assertIsNotEnabled()
                assertTrue(engine.requests.isEmpty())
            }
        } finally { http.close() }
    }

    @Test
    fun claimThenWaitThenDecryptOpensTheReportWithItsPhotos() {
        val patientReady = CompletableDeferred<Unit>()
        val opened = AtomicReference<ReceivedReport?>()
        val photo = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", it)
        }.toByteArray()
        var packet: ByteArray? = null
        val engine = Engine { request ->
            assertEquals("clinic", request.headers[HANDOFF_CLINIC_HEADER])
            when (request.url.encodedPath) {
                "/handoff/123456/claim" -> {
                    assertEquals(HttpMethod.Post, request.method)
                    val json = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    val key = EasyPaperJson.decodeFromString(HandoffPublicKey.serializer(), json)
                    packet = sealForDoctor(Demo.report, mapOf("demo-photo-1" to photo), Base64.decode(key.publicKey))
                    HttpStatusCode.OK to """{"claimSecret":"claim-secret"}""".encodeToByteArray()
                }
                "/handoff/123456/report" -> {
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("claim-secret", request.headers[HANDOFF_CLAIM_HEADER])
                    patientReady.await()
                    HttpStatusCode.OK to assertNotNull(packet)
                }
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val http = HttpClient(engine)
        try {
            runComposeUiTest {
                openCodeScreen(http, onOpened = { opened.set(it) })
                codeField().performScrollTo().performTextInput("123 456")
                onNodeWithText("Receive").performScrollTo().performClick()
                waitForText("Waiting for the patient's phone…")
                assertNull(opened.get())
                onNodeWithText("Receiving…").assertIsNotEnabled()
                patientReady.complete(Unit)
                waitUntil(timeoutMillis = 10_000) { opened.get() != null }
                val result = assertNotNull(opened.get())
                assertEquals(Demo.report, result.report)
                assertEquals(setOf("demo-photo-1"), result.photos.keys)
                assertContentEquals(photo, result.photos.getValue("demo-photo-1"))
                assertEquals(listOf("/handoff/123456/claim", "/handoff/123456/report"), engine.requests.map { it.url.encodedPath })
            }
        } finally { http.close() }
    }

    @Test
    fun invalidExpiredAndRefusedCodesShowActionableErrors() {
        val messages = mapOf(
            HttpStatusCode.NotFound to "This code is not valid or has expired. Ask the patient for a new code.",
            HttpStatusCode.Gone to "This code is not valid or has expired. Ask the patient for a new code.",
            HttpStatusCode.TooManyRequests to "Too many tries. Wait a minute.",
            HttpStatusCode.Unauthorized to "The clinic token was not accepted.",
        )
        for ((status, message) in messages) {
            val http = HttpClient(Engine { status to ByteArray(0) })
            try {
                runComposeUiTest {
                    openCodeScreen(http)
                    codeField().performScrollTo().performTextInput("123456")
                    onNodeWithText("Receive").performScrollTo().performClick()
                    waitForText(message)
                    onNodeWithText("Receive").assertIsEnabled()
                }
            } finally { http.close() }
        }
    }

    @Test
    fun switchingAwayFromCodeCancelsAnInFlightReportRequest() {
        val requesting = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val opened = AtomicReference<ReceivedReport?>()
        val http = HttpClient(Engine { request ->
            if (request.url.encodedPath.endsWith("/claim")) {
                HttpStatusCode.OK to """{"claimSecret":"claim-secret"}""".encodeToByteArray()
            } else {
                requesting.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
        })
        try {
            runComposeUiTest {
                openCodeScreen(http, onOpened = { opened.set(it) })
                codeField().performScrollTo().performTextInput("123456")
                onNodeWithText("Receive").performScrollTo().performClick()
                waitUntil(timeoutMillis = 10_000) { requesting.isCompleted }
                onNodeWithText("Open a file").performScrollTo().performClick()
                waitForText("Choose a file")
                waitUntil(timeoutMillis = 10_000) { cancelled.isCompleted }
                assertNull(opened.get())
            }
        } finally { http.close() }
    }
}
