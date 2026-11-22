package org.easypaper

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutorAPI
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.easypaper.ai.AiClient
import org.easypaper.ai.AiConfig
import org.easypaper.ai.AiStatus
import org.easypaper.ai.DocumentRequest
import org.easypaper.ai.SummaryRequest
import org.easypaper.ai.SummaryResult
import org.easypaper.demo.Demo
import org.easypaper.model.EasyPaperJson
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private class FakeExecutor(private val reply: (LLModel, Prompt) -> String) : PromptExecutorAPI {
    var calls = 0
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        calls++
        return Message.Assistant(reply(model, prompt), ResponseMetaInfo(Instant.fromEpochMilliseconds(0), inputTokensCount = 2_000, outputTokensCount = 300))
    }
    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = emptyFlow()
    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("not used")
    override fun close() {}
}

class AiTest {
    private val clinicToken = "test-clinic-token-do-not-echo"
    private val summaryReply = "Morning blood pressure 128-136/84-88 mmHg [e01, e02]\nAsks about ibuprofen with coffee [e35]"
    private val documentText = "Discharge letter. Current medication: Ramipril 5 mg once daily. Known allergy: penicillin."

    private fun cloud(budget: Double = 9.0, ex: FakeExecutor = FakeExecutor { _, _ -> summaryReply }) =
        AiService(AiMode.CLOUD, ex, AiConfig.cloud(), budget, keyConfigured = true)

    private fun clinic(ex: FakeExecutor) = AiService(AiMode.CLINIC, ex, AiConfig.clinic(), Double.MAX_VALUE, keyConfigured = false)

    private fun app(ai: AiService, token: String? = clinicToken, test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(ai = ai, aiToken = token) }
        client = createClient { defaultRequest { token?.let { header("X-EasyPaper-Clinic-Token", it) } } }
        test()
    }

    private val demoRequest = EasyPaperJson.encodeToString(SummaryRequest.serializer(), SummaryRequest(Demo.record))

    private suspend fun assertNoToken(response: HttpResponse) {
        val text = response.bodyAsText()
        assertFalse(clinicToken in text || "wrong-private-token" in text)
        assertFalse(response.headers.entries().any { (_, values) -> values.any { clinicToken in it || "wrong-private-token" in it } })
    }

    @Test
    fun missingAndWrongTokensAreRejectedBeforeRoutingOrReadingBodies() = app(cloud()) {
        val anonymous = createClient { }
        for (path in listOf("/ai/status", "/ai/summary", "/ai/documents", "/ai/unknown", "/a%69/status", "/ai//status")) {
            val missing = anonymous.get(path)
            assertEquals(HttpStatusCode.Unauthorized, missing.status)
            assertNoToken(missing)
            val wrong = anonymous.post(path) {
                header("X-EasyPaper-Clinic-Token", "wrong-private-token")
                setBody("not json")
            }
            assertEquals(HttpStatusCode.Unauthorized, wrong.status)
            assertNoToken(wrong)
        }
    }

    @Test
    fun correctTokenIsSentByAiClientAndNeverReturned() = app(cloud()) {
        val status = AiClient("", createClient { }, token = clinicToken).status()
        assertEquals("cloud", status.mode)
        val response = client.get("/ai/status")
        assertEquals(HttpStatusCode.OK, response.status)
        assertNoToken(response)
    }

    @Test
    fun missingServerTokenMakesAllAiRoutesUnavailable() = app(cloud(), token = null) {
        for (path in listOf("/ai/status", "/ai/summary", "/ai/documents", "/ai/unknown")) {
            val response = client.post(path) { header("X-EasyPaper-Clinic-Token", clinicToken) }
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals("AI access token not configured", response.bodyAsText())
            assertNoToken(response)
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/ai/status").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/not-ai").status)
    }

    @Test
    fun offModeExposesOnlyStatusWhenServerTokenIsMissing() = app(AiService.off(), token = null) {
        val response = client.get("/ai/status")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("off", AiClient("", client).status().mode)
        assertNoToken(response)
        for (path in listOf("/ai/summary", "/ai/documents", "/ai/status")) {
            assertEquals(HttpStatusCode.ServiceUnavailable, client.post(path).status)
        }
    }

    @Test
    fun offModeStillRequiresAConfiguredToken() = app(AiService.off()) {
        assertEquals(HttpStatusCode.Unauthorized, createClient { }.get("/ai/status").status)
        assertEquals(HttpStatusCode.OK, client.get("/ai/status").status)
    }

    @Test
    fun emptyServerTokenIsNotConfigured() = app(cloud(), token = "") {
        val response = client.get("/ai/status")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("AI access token not configured", response.bodyAsText())
    }

    @Test
    fun thirtyFirstRequestWithinAMinuteIsRateLimited() = app(cloud()) {
        repeat(30) { assertEquals(HttpStatusCode.OK, client.get("/ai/status").status) }
        val response = client.post("/ai/summary") {
            header("X-Forwarded-For", "203.0.113.123")
            setBody(demoRequest)
        }
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertNoToken(response)
    }

    @Test
    fun unauthorizedRequestsAlsoUseTheSharedAiLimit() = app(cloud()) {
        val anonymous = createClient { }
        repeat(30) {
            assertEquals(HttpStatusCode.Unauthorized, anonymous.get("/ai/unknown").status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/ai/status").status)
    }

    @Test
    fun limiterUsesASlidingMinuteAndKeepsIpsSeparate() {
        var now = 0L
        val limiter = AiRateLimiter { now }
        repeat(15) { assertTrue(limiter.allow("first-ip")) }
        now = 30_000_000_000L
        repeat(15) { assertTrue(limiter.allow("first-ip")) }
        assertFalse(limiter.allow("first-ip"))
        assertTrue(limiter.allow("second-ip"))
        now = 60_000_000_000L
        repeat(15) { assertTrue(limiter.allow("first-ip")) }
        assertFalse(limiter.allow("first-ip"))
        now = 90_000_000_000L
        assertTrue(limiter.allow("first-ip"))
    }

    @Test
    fun statusShowsModelsAndSpendingButNeverTheKey() = app(cloud()) {
        val text = client.get("/ai/status").bodyAsText()
        val status = EasyPaperJson.decodeFromString(AiStatus.serializer(), text)
        assertEquals("cloud", status.mode)
        assertTrue(status.keyConfigured)
        assertEquals("anthropic/claude-haiku-4.5", status.bigModel)
        assertEquals(9.0, status.budgetUsd)
        assertFalse("sk-" in text || "apiKey" in text || "OPENROUTER" in text)
    }

    @Test
    fun togetherKeyEnablesCloudAndStatusNeverReturnsIt() {
        val key = UUID.randomUUID().toString()
        val environment = mapOf("TOGETHER_API_KEY" to key)
        val ai = AiService.fromEnv(environment::get)
        app(ai) {
            val response = client.get("/ai/status")
            assertEquals(HttpStatusCode.OK, response.status)
            val text = response.bodyAsText()
            val status = EasyPaperJson.decodeFromString(AiStatus.serializer(), text)
            assertEquals("cloud", status.mode)
            assertEquals("Together AI (cloud)", status.provider)
            assertTrue(status.keyConfigured)
            assertEquals("nvidia/NVIDIA-Nemotron-3.5-Super-VL", status.smallModel)
            assertEquals("MiniMaxAI/MiniMax-M3.1-Flash", status.bigModel)
            assertEquals(AiConfig.together().prices, status.prices)
            assertFalse(key in text || "apiKey" in text || "TOGETHER_API_KEY" in text)
            assertFalse(response.headers.entries().any { (_, values) -> values.any { key in it } })
        }
    }

    @Test
    fun cloudSelectionUsesOnlyTheSelectedProvidersKey() {
        val key = UUID.randomUUID().toString()
        // An explicitly selected provider without its own key stays unconfigured,
        // even though the other cloud key makes the default AI mode cloud.
        for ((selected, otherKey, expectedProvider) in listOf(
            Triple("together", "OPENROUTER_API_KEY", "Together AI (cloud)"),
            Triple("openrouter", "TOGETHER_API_KEY", "OpenRouter (cloud)"),
        )) {
            val environment = mapOf("EASYPAPER_CLOUD" to selected, otherKey to key)
            val status = AiService.fromEnv(environment::get).status()
            assertEquals("cloud", status.mode)
            assertEquals(expectedProvider, status.provider)
            assertFalse(status.keyConfigured)
        }
    }

    @Test
    fun togetherHasPriorityByDefaultAndOpenRouterCanBeSelectedExplicitly() {
        val environment = mapOf(
            "TOGETHER_API_KEY" to UUID.randomUUID().toString(),
            "OPENROUTER_API_KEY" to UUID.randomUUID().toString(),
        )
        val together = AiService.fromEnv(environment::get).status()
        assertEquals("Together AI (cloud)", together.provider)
        assertTrue(together.keyConfigured)
        val selected = environment + ("EASYPAPER_CLOUD" to "openrouter")
        val openRouter = AiService.fromEnv(selected::get).status()
        assertEquals("OpenRouter (cloud)", openRouter.provider)
        assertTrue(openRouter.keyConfigured)
        val onlyOpenRouter = environment - "TOGETHER_API_KEY"
        val fallback = AiService.fromEnv(onlyOpenRouter::get).status()
        assertEquals("OpenRouter (cloud)", fallback.provider)
        assertTrue(fallback.keyConfigured)
        assertEquals("off", AiService.fromEnv { null }.status().mode)
    }

    @Test
    fun clinicStatusHasNoBudgetLimit() = app(clinic(FakeExecutor { _, _ -> summaryReply })) {
        val response = client.get("/ai/status")
        assertEquals(HttpStatusCode.OK, response.status)
        val text = response.bodyAsText()
        val status = AiClient("", createClient { }, token = clinicToken).status()
        assertEquals("clinic", status.mode)
        assertNull(status.budgetUsd)
        assertTrue("\"budgetUsd\":null" in text)
        assertFalse(Double.MAX_VALUE.toString() in text)
    }

    @Test
    fun summaryThroughTheServerCitesEntriesAndCountsCost() = app(cloud()) {
        val response = client.post("/ai/summary") { contentType(ContentType.Application.Json); setBody(demoRequest) }
        assertEquals(HttpStatusCode.OK, response.status)
        val result = EasyPaperJson.decodeFromString(SummaryResult.serializer(), response.bodyAsText())
        assertTrue(result.usedAi)
        assertEquals(listOf(listOf("e01", "e02"), listOf("e35")), result.lines.map { it.refs })
        val status = EasyPaperJson.decodeFromString(AiStatus.serializer(), client.get("/ai/status").bodyAsText())
        assertTrue(status.spentUsd > 0 && status.calls >= 1)
    }

    @Test
    fun clientAndServerPassSummaryLanguageAndSeparateCachedReplies() {
        val prompts = mutableListOf<String>()
        val ex = FakeExecutor { _, prompt ->
            prompts += prompt.messages.first().textContent()
            summaryReply
        }
        app(cloud(ex = ex)) {
            val api = AiClient("", createClient { }, token = clinicToken)
            for ((language, name) in listOf("en" to "English", "de" to "German", "uk" to "Ukrainian", "fr" to "French", "ru" to "Russian")) {
                val result = api.summarize(Demo.record, language = language)
                assertTrue(result.usedAi)
                assertTrue("Plain $name" in prompts.last())
            }
            assertEquals(5, ex.calls)
            assertTrue(api.summarize(Demo.record, language = "fr").usage.all { it.cached })
            assertEquals(5, ex.calls)
        }
    }

    @Test
    fun aiOffReturnsFrenchAndRussianFactsThroughTheClient() = app(AiService.off()) {
        val api = AiClient("", createClient { }, token = clinicToken)
        for ((language, label) in listOf("fr" to "Tension artérielle", "ru" to "Артериальное давление")) {
            val result = api.summarize(Demo.record, language = language)
            assertFalse(result.usedAi)
            assertTrue(result.lines.any { label in it.text && it.supported && it.refs.isNotEmpty() })
            assertTrue(result.usage.isEmpty())
        }
    }

    @Test
    fun spendingCapGivesFactsWithoutAi() {
        val ex = FakeExecutor { _, _ -> summaryReply }
        app(cloud(budget = 0.0, ex = ex)) {
            val result = EasyPaperJson.decodeFromString(SummaryResult.serializer(),
                client.post("/ai/summary") { contentType(ContentType.Application.Json); setBody(demoRequest) }.bodyAsText())
            assertFalse(result.usedAi)
            assertEquals(0, ex.calls)
            assertTrue(result.lines.isNotEmpty() && result.lines.all { it.refs.isNotEmpty() || "Complaint" in it.text })
        }
    }

    @Test
    fun aiOffStillGivesTheFacts() = app(AiService.off()) {
        val result = EasyPaperJson.decodeFromString(SummaryResult.serializer(),
            client.post("/ai/summary") { contentType(ContentType.Application.Json); setBody(demoRequest) }.bodyAsText())
        assertFalse(result.usedAi)
        assertTrue("off" in result.note.orEmpty())
    }

    @Test
    fun documentsNeverLeaveTheClinic() = app(cloud()) {
        val body = EasyPaperJson.encodeToString(DocumentRequest.serializer(), DocumentRequest("doc-1", text = documentText))
        val response = client.post("/ai/documents") { contentType(ContentType.Application.Json); setBody(body) }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun clinicReadsDocumentsAndKeepsOnlyQuotedFacts() {
        val ex = FakeExecutor { _, _ ->
            """
            medication | Ramipril 5 mg once daily | Ramipril 5 mg once daily
            allergy | Penicillin | allergy: penicillin
            diagnosis_in_document | Heart failure | chronic heart failure
            advice | Drink more | drink more water
            """.trimIndent()
        }
        app(clinic(ex)) {
            val facts = AiClient("", createClient { }, token = clinicToken).extractDocument("doc-1", text = documentText)
            assertEquals(listOf("medication", "allergy"), facts.facts.map { it.field })
            assertTrue(facts.facts.all { it.verified && it.documentId == "doc-1" })
            assertTrue("2 fact(s) without a matching quote removed" in facts.note.orEmpty())
        }
    }

    @Test
    fun badBodiesAreRejected() = app(cloud()) {
        assertEquals(HttpStatusCode.BadRequest, client.post("/ai/summary") { contentType(ContentType.Application.Json); setBody("{not json") }.status)
        val badPhoto = """{"record":${EasyPaperJson.encodeToString(org.easypaper.model.PatientRecord.serializer(), Demo.record)},"photos":{"p":"%%%"}}"""
        assertEquals(HttpStatusCode.BadRequest, client.post("/ai/summary") { contentType(ContentType.Application.Json); setBody(badPhoto) }.status)
        val huge = ByteArray((MAX_AI_BODY_BYTES + 10).toInt()) { 'a'.code.toByte() }
        assertEquals(HttpStatusCode.PayloadTooLarge, client.post("/ai/summary") { setBody(huge) }.status)
    }
}
