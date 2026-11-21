package org.easypaper.ai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutorAPI
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.easypaper.model.BloodPressure
import org.easypaper.model.BloodPressureWidget
import org.easypaper.model.CareRequest
import org.easypaper.model.DiaryNote
import org.easypaper.model.DiaryWidget
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.FoodWidget
import org.easypaper.model.PatientRecord
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Koog executor that answers from a function and counts calls; no network. */
private class FakeExecutor(
    private val inputTokens: Int = 1_000,
    private val outputTokens: Int = 200,
    private val reply: (LLModel) -> String,
) : PromptExecutorAPI {
    val calls = mutableListOf<Pair<Prompt, LLModel>>()
    var failing = false

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        if (failing) throw IllegalStateException("provider down")
        calls += prompt to model
        return Message.Assistant(
            reply(model),
            ResponseMetaInfo(Instant.fromEpochMilliseconds(0), inputTokensCount = inputTokens, outputTokensCount = outputTokens),
        )
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = emptyFlow()
    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("not used")
    override fun close() {}
}

class SummaryPipelineTest {
    private val config = AiConfig.cloud()
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

    private val record = PatientRecord(
        CareRequest(
            id = "req-1", createdAt = 0, doctorName = "Dr. Weber", complaint = "Headache", startDate = "2026-11-17",
            days = 3, locale = "en",
            widgets = listOf(BloodPressureWidget("bp", "Blood pressure", true), FoodWidget("food", "Food", false),
                DiaryWidget("diary", "Diary", false)),
        ),
        listOf(
            Entry("e1", "bp", 1_794_902_400_000L, BloodPressure(128, 84, 72)),
            Entry("e2", "food", 1_794_906_000_000L, Food(text = null, photoRef = "photo/e2")),
            Entry("e3", "food", 1_794_920_000_000L, Food(text = "soup and bread", photoRef = "photo/e3")),
            Entry("e4", "diary", 1_794_930_000_000L, DiaryNote("Headache again after lunch")),
        ),
    )
    private val photos = mapOf("photo/e2" to jpeg, "photo/e3" to jpeg)

    private fun executor() = FakeExecutor { model ->
        if (model == config.small) "Oatmeal with banana, black tea."
        else "Morning blood pressure 128/84 mmHg, pulse 72 [e1]\nHeadache after lunch [e4]"
    }

    private fun Prompt.text() = messages.joinToString("\n") { it.textContent() }
    private fun Prompt.images() = messages.flatMap { it.parts }.count { it is MessagePart.Attachment }

    @Test
    fun smallModelOnlyForUntoldPhotosAndBigModelOnce() = runTest {
        val ex = executor()
        val result = SummaryPipeline(ex, config).summarize(record, photos)

        assertEquals(listOf(config.small, config.big), ex.calls.map { it.second })
        assertEquals(1, ex.calls[0].first.images(), "the photo goes to the small model")
        assertEquals(0, ex.calls[1].first.images(), "the big model gets text facts only")
        assertTrue("Oatmeal with banana, black tea [e2]" in ex.calls[1].first.text())
        assertEquals(mapOf("e2" to "Oatmeal with banana, black tea"), result.photoDescriptions)
        assertTrue(result.usedAi)
        assertEquals(listOf(listOf("e1"), listOf("e4")), result.lines.map { it.refs })
        assertTrue(result.lines.all { it.supported })
        assertEquals("Morning blood pressure 128/84 mmHg, pulse 72 [e1]\nHeadache after lunch [e4]", result.summary)
    }

    @Test
    fun secondRunComesFromTheCacheForFree() = runTest {
        val ex = executor()
        val pipeline = SummaryPipeline(ex, config)
        val first = pipeline.summarize(record, photos)
        val second = pipeline.summarize(record, photos)

        assertEquals(2, ex.calls.size, "no new model calls")
        assertEquals(first.summary, second.summary)
        assertTrue(second.usage.all { it.cached && it.costUsd == 0.0 && it.inputTokens == 0 })
        assertEquals(2, pipeline.ledger.cacheHits)
    }

    @Test
    fun costFollowsTokensAndThePriceTable() = runTest {
        val result = SummaryPipeline(executor(), config).summarize(record, photos)
        val (photo, summary) = result.usage
        assertEquals(AiStep.PhotoDescription, photo.step)
        assertEquals(AiStep.Summary, summary.step)
        assertNear((1_000 * 0.10 + 200 * 0.40) / 1e6, photo.costUsd)
        assertNear((1_000 * 1.00 + 200 * 5.00) / 1e6, summary.costUsd)
        assertEquals(2_000, result.usage.sumOf { it.inputTokens })
    }

    @Test
    fun clinicModuleCostsNothing() = runTest {
        val clinic = AiConfig.clinic()
        val ex = FakeExecutor { "Morning blood pressure 128/84 mmHg [e1]" }
        val result = SummaryPipeline(ex, clinic).summarize(record, photos)
        assertTrue(result.usedAi)
        assertEquals(0.0, result.usage.sumOf { it.costUsd })
        assertTrue(ex.calls.all { it.second.id == "gemma4:12b" })
    }

    @Test
    fun overBudgetGivesFactsWithoutAi() = runTest {
        val ex = executor()
        val result = SummaryPipeline(ex, config.copy(budgetUsdPerReport = 1e-7)).summarize(record, photos)
        assertTrue(ex.calls.isEmpty())
        assertFalse(result.usedAi)
        assertTrue("budget" in result.note.orEmpty())
        assertTrue(result.lines.isNotEmpty() && result.lines.any { "128" in it.text && it.refs == listOf("e1") })
    }

    @Test
    fun providerFailureGivesFactsWithoutAi() = runTest {
        val ex = executor().apply { failing = true }
        val result = SummaryPipeline(ex, config).summarize(record, photos)
        assertFalse(result.usedAi)
        assertTrue("not reachable" in result.note.orEmpty())
        assertTrue(result.summary.isNotBlank())
    }

    @Test
    fun adviceDiagnosesAndInventedReferencesAreRemoved() = runTest {
        val ex = FakeExecutor { model ->
            if (model == config.small) "oatmeal" else """
                - Blood pressure 128/84 mmHg [e1]
                - You should reduce salt [e1]
                - This is likely migraine [e4]
                - Headache every day [e99]
                - Patient feels tired
                - Headache after lunch [e4, e77]
            """.trimIndent()
        }
        val result = SummaryPipeline(ex, config).summarize(record, photos)
        assertEquals(
            listOf(
                SummaryLine("Blood pressure 128/84 mmHg [e1]", listOf("e1"), true),
                SummaryLine("Headache every day", emptyList(), false),
                SummaryLine("Patient feels tired", emptyList(), false),
                SummaryLine("Headache after lunch [e4]", listOf("e4"), false),
            ),
            result.lines,
        )
        assertTrue("2 line(s) with advice or diagnosis removed" in result.note.orEmpty())
    }

    @Test
    fun noDoctorNameOrRequestIdInPrompts() = runTest {
        val ex = executor()
        SummaryPipeline(ex, config).summarize(record, photos)
        for ((prompt, _) in ex.calls) {
            assertFalse("Weber" in prompt.text())
            assertFalse("req-1" in prompt.text())
        }
    }

    @Test
    fun oversizedPhotosAreNotSent() = runTest {
        val ex = executor()
        val big = ByteArray(config.maxPhotoBytes + 1) { 1 }
        val result = SummaryPipeline(ex, config).summarize(record, mapOf("photo/e2" to big))
        assertEquals(listOf(config.big), ex.calls.map { it.second })
        assertTrue("too large" in assertNotNull(result.note))
    }

    @Test
    fun photoLabelsStayShortAndPlain() {
        assertEquals("oatmeal with banana", SummaryPipeline.cleanDescription("\"Oatmeal with banana.\"\nExtra")?.lowercase())
        assertEquals(null, SummaryPipeline.cleanDescription("Not food"))
        assertEquals(null, SummaryPipeline.cleanDescription("Pizza; you should eat less of it"))
    }

    private fun assertNear(expected: Double, actual: Double) = assertTrue(abs(expected - actual) < 1e-12, "$expected vs $actual")
}
