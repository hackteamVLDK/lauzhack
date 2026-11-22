package org.easypaper.catalog

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutorAPI
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.easypaper.AiMode
import org.easypaper.AiService
import org.easypaper.ai.AiConfig
import org.easypaper.ai.AiStep
import org.easypaper.model.EasyPaperJson
import org.easypaper.module
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private class LabelExecutor : PromptExecutorAPI {
    var calls = 0
    val models = mutableListOf<String>()
    var reply = """{"brand":"НУРОФЄН","strengths":["200 мг"],"inn":["ібупрофен"],"form":"таблетки","text":"НУРОФЄН 200 мг таблетки ібупрофен"}"""
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        calls++
        models += model.id
        return Message.Assistant(reply, ResponseMetaInfo(Instant.fromEpochMilliseconds(0), inputTokensCount = 2_000, outputTokensCount = 200))
    }
    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = emptyFlow()
    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("unused")
    override fun close() {}
}

class ReadLabelTest {
    private val jpeg = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", it) }.toByteArray()
    private fun cloud(ex: LabelExecutor, budget: Double = 9.0) = AiService(AiMode.CLOUD, ex, AiConfig.cloud(), budget, keyConfigured = true)

    private fun app(ai: AiService, test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val dir = Files.createTempDirectory("read-label-catalog").toFile()
        try {
            val snapshot = EasyPaperJson.decodeFromString(SourceSnapshot.serializer(), javaClass.getResource("/catalog/recognition/ua.json")!!.readText())
            val store = CatalogStore(dir)
            store.write(snapshot)
            // No clinic token: package reading is available to patients.
            application { module(ai = ai, aiToken = null, catalog = CatalogService(store, indications = null)) }
            test()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun anonymousReadUsesSmallModelResolvesUaAndCachesPhotoHash() {
        val ex = LabelExecutor()
        val ai = cloud(ex)
        app(ai) {
            val catalog = HttpCatalog("", client)
            repeat(2) {
                val result = catalog.readLabel(jpeg)
                assertEquals(ResolutionKind.EXACT, result.kind)
                assertEquals("ua-drlz:nurofen", result.candidates.single().product.id)
            }
            assertEquals(listOf(ai.config!!.small.id), ex.models)
            assertEquals(1, ex.calls)
            assertTrue(ai.ledger.totalCostUsd > 0)
            assertEquals(2, ai.ledger.items.size)
            assertTrue(ai.ledger.items.all { it.step == AiStep.MedicineLabel })
            assertTrue(ai.ledger.items.last().cached)
            assertEquals(0.0, ai.ledger.items.last().costUsd)
        }
    }

    @Test
    fun offAndExhaustedOrInsufficientBudgetReturn503WithoutCallingModel() {
        val ex = LabelExecutor()
        for (ai in listOf(AiService.off(), cloud(ex, 0.0), cloud(ex, 0.000001))) app(ai) {
            val response = client.post("/catalog/read-label") { contentType(ContentType.Image.JPEG); setBody(jpeg) }
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals("Reading online is not available right now", response.bodyAsText())
        }
        assertEquals(0, ex.calls)
    }

    @Test
    fun brokenModelJsonReturns503ButItsUsageIsStillCounted() {
        val ex = LabelExecutor().apply { reply = "not JSON" }
        val ai = cloud(ex)
        app(ai) {
            assertEquals(HttpStatusCode.ServiceUnavailable, client.post("/catalog/read-label") { setBody(jpeg) }.status)
            assertTrue(ai.ledger.totalCostUsd > 0)
        }
    }

    @Test
    fun photoBodyIsBoundedAndMustBeJpeg() {
        val ex = LabelExecutor()
        app(cloud(ex)) {
            assertEquals(HttpStatusCode.BadRequest, client.post("/catalog/read-label") { setBody("not JPEG") }.status)
            assertEquals(HttpStatusCode.PayloadTooLarge, client.post("/catalog/read-label") { setBody(ByteArray(1_500_001)) }.status)
            assertEquals(0, ex.calls)
        }
    }

    @Test
    fun minuteLimitIncludesCacheHitsAndUsesLastForwardedAddress() {
        val ex = LabelExecutor()
        app(cloud(ex)) {
            repeat(10) {
                assertEquals(HttpStatusCode.OK, client.post("/catalog/read-label") {
                    header("X-Forwarded-For", "forged-$it, 203.0.113.1"); setBody(jpeg)
                }.status)
            }
            assertEquals(HttpStatusCode.TooManyRequests, client.post("/catalog/read-label") {
                header("X-Forwarded-For", "another-forged, 203.0.113.1"); setBody(jpeg)
            }.status)
            assertEquals(HttpStatusCode.OK, client.post("/catalog/read-label") {
                header("X-Forwarded-For", "203.0.113.2"); setBody(jpeg)
            }.status)
            assertEquals(1, ex.calls)
        }
    }

    @Test
    fun dailyLimitSurvivesMinuteExpiryAndExpiresAfter24Hours() {
        var now = 0L
        val minute = 60_000_000_000L
        val limiter = LabelReadLimiter { now }
        repeat(6) {
            repeat(10) { assertTrue(limiter.allow("patient")) }
            assertTrue(!limiter.allow("patient"))
            now += minute
        }
        assertTrue(!limiter.allow("patient"))
        assertTrue(limiter.allow("other patient"))
        now = 24 * 60 * minute
        assertTrue(limiter.allow("patient"))
    }
}
