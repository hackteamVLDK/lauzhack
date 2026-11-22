package org.easypaper.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.easypaper.account.*
import org.easypaper.demo.Demo
import org.easypaper.invite.encodeInvite
import org.easypaper.model.EasyPaperJson
import org.easypaper.ui.patient.v2.PatientLanguage
import org.easypaper.ui.patient.v2.PatientSettings
import org.easypaper.ui.shell.Role
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.shell.ShellState
import org.easypaper.ui.theme.EasyPaperTheme
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Clock

@OptIn(ExperimentalTestApi::class, InternalAPI::class)
class AccountUiTest {
    private class Engine(val respond: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>) : HttpClientEngineBase("account-ui-test") {
        override val dispatcher = Dispatchers.Default
        override val config = HttpClientEngineConfig()
        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            val (status, body) = respond(data)
            return HttpResponseData(status, GMTDate(), headersOf(HttpHeaders.ContentType, "application/json"),
                HttpProtocolVersion.HTTP_1_1, ByteReadChannel(body), callContext())
        }
    }

    private fun settings(name: String? = null): PatientSettings {
        val setting = { SavedSetting(null) {} }
        return PatientSettings(setting(), setting(), setting(), SavedSetting(name) {}, setting(), setting(),
            setting(), setting(), setting(), setting(), setting())
    }

    private fun challenge() = EasyPaperJson.encodeToString(EmailChallenge.serializer(),
        EmailChallenge("challenge", Clock.System.now().toEpochMilliseconds() + 600_000))

    private fun session(provider: String = "email") = EasyPaperJson.encodeToString(AccountSession.serializer(),
        AccountSession("session-secret", Account("account-1", "alex@example.test", "Alex", provider = provider)))

    private fun person() = EasyPaperJson.encodeToString(InvitedPerson.serializer(),
        InvitedPerson("Alex", "+41 *** ** 12", "a***@example.test"))

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.click(text: String) = onNodeWithText(text).performScrollTo().performClick()

    @Composable
    private fun TestContent(content: @Composable () -> Unit) {
        EasyPaperTheme(false) {
            PatientLanguage("en") { Column(Modifier.verticalScroll(rememberScrollState())) { content() } }
        }
    }

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @Test
    fun emailThenPastedCodeSavesSessionAndFillsAnEmptyName() = runComposeUiTest {
        val patient = settings()
        val signedIn = AtomicInteger()
        val sends = AtomicInteger()
        val http = HttpClient(Engine { request ->
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            when (request.url.encodedPath) {
                "/account/email/start" -> {
                    assertContains(body, "alex@example.test")
                    sends.incrementAndGet()
                    HttpStatusCode.OK to challenge()
                }
                "/account/email/verify" -> {
                    assertContains(body, "123456")
                    assertContains(body, "challenge")
                    HttpStatusCode.OK to session()
                }
                else -> error("Unexpected account request")
            }
        })
        try {
            val client = AccountClient("https://account.test", http)
            setContent { TestContent { AccountSignIn(patient, { signedIn.incrementAndGet() }, client = client) } }
            waitForText("Continue with email")
            click("Continue with email")
            onNodeWithContentDescription("Email address").performTextInput("alex@example.test")
            click("Send me a code")
            waitForText("Verify code")
            onNodeWithText("Resend in", substring = true).assertIsNotEnabled()
            assertEquals(1, sends.get())
            onNodeWithContentDescription("6-digit code").performTextInput("123 456")
            click("Verify code")
            waitUntil(timeoutMillis = 10_000) { signedIn.get() == 1 }
            runOnIdle {
                assertEquals("session-secret", patient.accountToken.value)
                assertEquals("alex@example.test", patient.accountEmail.value)
                assertEquals("Alex", patient.accountName.value)
                assertEquals("Alex", patient.name.value)
                assertEquals("email", patient.accountProvider.value)
            }
        } finally { http.close() }
    }

    @Test
    fun appleShowsSoonAndGooglePlaceholderExplainsConfiguration() = runComposeUiTest {
        val http = HttpClient(Engine { error("No network request is expected") })
        try {
            val patient = settings()
            val signedIn = AtomicInteger()
            val client = AccountClient("https://account.test", http)
            setContent { TestContent { AccountSignIn(patient, { signedIn.incrementAndGet() }, client = client) } }
            waitForText("Continue with Apple")
            onNodeWithText("Continue with Apple").assertIsEnabled()
            click("Continue with Apple")
            waitForText("Sign in with Apple is coming soon.")
            onNodeWithText("Sign in with Apple is coming soon.").assertIsDisplayed()
            onNodeWithText("OK").performClick()
            onNodeWithText("Sign in with Apple is coming soon.").assertDoesNotExist()
            click("Continue with Google")
            // the real web client ID is configured; on Desktop (JVM) Google sign-in is not offered
            waitForText(if (org.easypaper.ui.account.googleSignInConfigured) "Google sign-in is available on Android and the web"
                else "Google sign-in is not configured yet")
            assertEquals(0, signedIn.get())
            assertNull(patient.accountToken.value)
        } finally { http.close() }
    }

    @Test
    fun invitationShowsMaskedDetailsAndStartsRecordingOnlyAfterCode() = runComposeUiTest {
        val patient = settings("My chosen name")
        val state = ShellState(Role.Patient)
        val request = Demo.request.copy(id = "account-invite", personToken = "person-secret")
        val confirmations = AtomicInteger()
        val started = AtomicInteger()
        val http = HttpClient(Engine { call ->
            when (call.url.encodedPath) {
                "/invites/person-secret" -> HttpStatusCode.OK to person()
                "/invites/person-secret/confirm" -> {
                    confirmations.incrementAndGet()
                    HttpStatusCode.OK to challenge()
                }
                "/account/email/verify" -> HttpStatusCode.OK to session("invite")
                else -> error("Unexpected invitation request")
            }
        })
        try {
            val client = AccountClient("https://account.test", http)
            setContent {
                TestContent {
                    Column {
                        PatientInviteForm(encodeInvite(request), patient, "https://account.test", client) { accepted ->
                            state.repository.start(accepted)
                            started.incrementAndGet()
                        }
                    }
                }
            }
            waitForText("a***@example.test")
            onNodeWithText("Is this you?").assertExists()
            onNodeWithText("Alex").assertExists()
            onNodeWithText("+41 *** ** 12").assertExists()
            onNodeWithText("Start recording").assertDoesNotExist()
            assertEquals(0, started.get())
            click("Yes, send me a code")
            waitForText("Verify code")
            assertEquals(1, confirmations.get())
            assertEquals(0, started.get())
            onNodeWithContentDescription("6-digit code").performTextInput("123 456")
            click("Verify code")
            waitForText("Recording started")
            assertEquals(1, started.get())
            runOnIdle {
                assertTrue(state.repository.records.value.any { it.request.id == request.id })
                assertEquals("invite", patient.accountProvider.value)
                assertEquals("session-secret", patient.accountToken.value)
                assertEquals("My chosen name", patient.name.value)
            }
        } finally { http.close() }
    }

    @Test
    fun wrongPersonNeverConfirmsOrStartsRecording() = runComposeUiTest {
        val patient = settings()
        val started = AtomicInteger()
        val http = HttpClient(Engine { call ->
            assertEquals("/invites/person-secret", call.url.encodedPath)
            HttpStatusCode.OK to person()
        })
        try {
            val client = AccountClient("https://account.test", http)
            setContent { TestContent { InviteIdentity(patient, "person-secret", client) { started.incrementAndGet(); Unit } } }
            waitForText("This is not me")
            click("This is not me")
            waitForText("You have not been connected. Ask your doctor to check the contact details and send a new invitation.")
            onNodeWithText("Yes, send me a code").assertDoesNotExist()
            onNodeWithText("Continue without an account").assertDoesNotExist()
            assertEquals(0, started.get())
            assertNull(patient.accountToken.value)
        } finally { http.close() }
    }

    @Test
    fun offlineInvitationCanStartLocallyWithoutCreatingASession() = runComposeUiTest {
        val patient = settings()
        val started = AtomicInteger()
        val http = HttpClient(Engine { throw IOException("offline") })
        try {
            val client = AccountClient("https://account.test", http)
            setContent { TestContent { InviteIdentity(patient, "person-secret", client) { started.incrementAndGet(); Unit } } }
            waitForText("Continue without an account")
            onNodeWithText("Could not reach the server. Check your connection and try again.").assertExists()
            assertEquals(0, started.get())
            click("Continue without an account")
            waitForText("Recording started")
            assertEquals(1, started.get())
            assertNull(patient.accountToken.value)
        } finally { http.close() }
    }

    @Test
    fun resetClearsSessionAndDisplayCache() {
        val patient = settings()
        patient.saveSession(AccountSession("token", Account("id", "alex@example.test", "Alex", provider = "email")))
        patient.reset()
        assertNull(patient.accountToken.value)
        assertNull(patient.accountEmail.value)
        assertNull(patient.accountName.value)
        assertNull(patient.accountProvider.value)
    }

    @Test
    fun resendingWaitsSixtySecondsAndReplacesTheChallenge() = runBlocking {
        val patient = settings()
        var time = 1_000_000L
        val sends = AtomicInteger()
        val http = HttpClient(Engine {
            val count = sends.incrementAndGet()
            HttpStatusCode.OK to EasyPaperJson.encodeToString(EmailChallenge.serializer(), EmailChallenge("challenge-$count", time + 600_000))
        })
        try {
            val flow = AccountFlow(AccountClient("https://account.test", http), patient, now = { time })
            flow.email = "alex@example.test"
            flow.sendCode()
            assertEquals(60, flow.retrySeconds())
            flow.changeCode("123 456")
            time += 59_999
            flow.sendCode()
            assertEquals(1, sends.get())
            assertEquals("challenge-1", flow.challenge?.challengeId)
            time++
            flow.sendCode()
            assertEquals(2, sends.get())
            assertEquals("challenge-2", flow.challenge?.challengeId)
            assertEquals("", flow.code)
        } finally { http.close() }
    }

    @Test
    fun profileRefreshesTheDisplayCacheAndSignsOutWithTheSessionToken() = runComposeUiTest {
        val patient = settings()
        patient.saveSession(AccountSession("session-secret", Account("id", "cached@example.test", provider = "email")))
        val signOuts = AtomicInteger()
        val http = HttpClient(Engine { call ->
            assertEquals("Bearer session-secret", call.headers[HttpHeaders.Authorization])
            when (call.url.encodedPath) {
                "/account/me" -> HttpStatusCode.OK to EasyPaperJson.encodeToString(Account.serializer(),
                    Account("id", "alex@example.test", "Alex", provider = "google"))
                "/account/signout" -> { signOuts.incrementAndGet(); HttpStatusCode.NoContent to "" }
                else -> error("Unexpected account request")
            }
        })
        try {
            val client = AccountClient("https://account.test", http)
            setContent { TestContent { AccountSection(patient, onSignIn = {}, client = client) } }
            waitForText("alex@example.test")
            onNodeWithText("Google").assertExists()
            click("Sign out")
            waitForText("Sign in")
            assertEquals(1, signOuts.get())
            runOnIdle {
                assertNull(patient.accountToken.value)
                assertNull(patient.accountEmail.value)
                assertNull(patient.accountName.value)
                assertNull(patient.accountProvider.value)
                assertEquals("Alex", patient.name.value)
            }
        } finally { http.close() }
    }
}
