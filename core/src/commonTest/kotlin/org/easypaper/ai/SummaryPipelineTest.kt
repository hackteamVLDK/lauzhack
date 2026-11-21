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
import org.easypaper.report.factDigest
import org.easypaper.report.toLocalizedText
import org.easypaper.report.toText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Koog executor that answers from a function and counts calls; no network. */
private class FakeExecutor(
    private val inputTokens: Int? = 1_000,
    private val outputTokens: Int? = 200,
    private val reply: (LLModel) -> String,
) : PromptExecutorAPI {
    val calls = mutableListOf<Pair<Prompt, LLModel>>()
    var failing = false

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        calls += prompt to model
        if (failing) throw IllegalStateException("provider down")
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
        // the big model left the photo out: its label still reaches the doctor, marked as the vision model's words (#36)
        assertEquals(listOf(listOf("e1"), listOf("e4"), listOf("e2")), result.lines.map { it.refs })
        assertTrue(result.lines.all { it.supported })
        assertEquals(
            "Morning blood pressure 128/84 mmHg, pulse 72 [e1]\nHeadache after lunch [e4]\n" +
                "Food photo, described by AI (not by the patient): Oatmeal with banana, black tea [e2]",
            result.summary,
        )
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
        assertTrue(result.usage.all { it.reportId == record.request.id && !it.estimated })
    }

    @Test
    fun cachedCallsAreAssignedToTheCurrentReport() = runTest {
        val ex = executor()
        val pipeline = SummaryPipeline(ex, config)
        val first = pipeline.summarize(record, photos)
        val other = record.copy(request = record.request.copy(id = "req-2"))
        val second = pipeline.summarize(other, photos)

        assertEquals(2, ex.calls.size)
        assertTrue(first.usage.all { it.reportId == "req-1" })
        assertEquals(2, second.usage.size)
        assertTrue(second.usage.all { it.reportId == "req-2" && it.cached && !it.estimated })
        assertEquals(first.usage + second.usage, pipeline.ledger.items)
    }

    @Test
    fun missingProviderCountsAreEstimatedIndependentlyAndCacheHitsStayFree() = runTest {
        for ((input, output) in listOf(null to null, null to 0, 0 to null, 0 to 0, 100 to 20)) {
            val ex = FakeExecutor(inputTokens = input, outputTokens = output) { "answer" }
            val ledger = UsageLedger()
            val caller = ModelCaller(ex, config, InMemoryAiCache(), ledger)
            repeat(2) {
                caller.call(AiStep.Summary, config.big, "system", "user", null, 100, reportId = "req-1")
            }
            val (call, cached) = ledger.items
            assertEquals(input ?: estimateTokens("systemuser"), call.inputTokens)
            assertEquals(output ?: estimateTokens("answer"), call.outputTokens)
            assertEquals(input == null || output == null, call.estimated)
            assertEquals(config.price(config.big)!!.cost(call.inputTokens, call.outputTokens), call.costUsd)
            assertEquals("req-1", call.reportId)
            assertEquals("req-1", cached.reportId)
            assertTrue(cached.cached)
            assertFalse(cached.estimated)
            assertEquals(0, cached.inputTokens)
            assertEquals(0, cached.outputTokens)
            assertEquals(0.0, cached.costUsd)
            assertEquals(1, ex.calls.size)
        }
    }

    @Test
    fun photoLimitCountsAttemptsEvenForRejectedEmptyAndFailedAnswers() = runTest {
        val entries = (1..7).map { i -> Entry("e$i", "food", i.toLong(), Food(photoRef = "photo/$i")) }
        val input = record.copy(entries = entries)
        val images = (1..7).associate { i -> "photo/$i" to (jpeg + i.toByte()) }
        for (limit in listOf(0, 2, 6)) {
            for (reply in listOf("not food", "   ", "You should eat less salt", "failure")) {
                val ex = FakeExecutor { model -> if (model == config.small) reply else "Recorded food photo [e1]" }
                    .apply { failing = reply == "failure" }
                val result = SummaryPipeline(ex, config.copy(maxPhotos = limit)).summarize(input, images)

                assertEquals(limit, ex.calls.count { it.second == config.small }, "$reply, limit $limit")
                assertEquals(1, ex.calls.count { it.second == config.big })
                assertTrue(result.photoDescriptions.isEmpty())
            }
        }
    }

    @Test
    fun missingAndOversizedPhotosDoNotConsumeAttempts() = runTest {
        val input = record.copy(entries = (1..3).map { i -> Entry("e$i", "food", i.toLong(), Food(photoRef = "photo/$i")) })
        val ex = executor()
        val images = mapOf("photo/2" to ByteArray(config.maxPhotoBytes + 1), "photo/3" to jpeg)
        val result = SummaryPipeline(ex, config.copy(maxPhotos = 1)).summarize(input, images)

        assertEquals(1, ex.calls.count { it.second == config.small })
        assertEquals(setOf("e3"), result.photoDescriptions.keys)
    }

    @Test
    fun bothPromptsUseTheRequestedLanguageAndCacheIsSeparatedByLanguage() = runTest {
        val ex = executor()
        val pipeline = SummaryPipeline(ex, config)
        for ((language, name) in listOf("en" to "English", "de" to "German", "uk" to "Ukrainian")) {
            val result = pipeline.summarize(record, photos, language = language)
            assertTrue(result.usedAi)
            val calls = ex.calls.takeLast(2)
            assertTrue("in $name" in calls[0].first.messages.first().textContent())
            val system = calls[1].first.messages.first().textContent()
            assertTrue("Plain $name" in system)
            assertTrue("No diagnosis" in system)
            assertTrue("no advice" in system)
            assertTrue(record.factDigest().toText() in calls[1].first.text(), "the facts in the prompt stay in English")
            assertTrue("not food" in calls[0].first.messages.first().textContent())
        }
        assertEquals(6, ex.calls.size)
        val cached = pipeline.summarize(record, photos, language = "de")
        assertEquals(6, ex.calls.size)
        assertTrue(cached.usage.all { it.cached })
    }

    @Test
    fun germanFallbackKeepsReferencesAndPatientTextForBudgetAndProviderFailure() = runTest {
        for (budget in listOf(true, false)) {
            val ex = executor().apply { failing = !budget }
            val pipeline = SummaryPipeline(ex, if (budget) config.copy(budgetUsdPerReport = 0.0) else config)
            val result = pipeline.summarize(record, photos, language = "de")

            assertFalse(result.usedAi)
            assertEquals(record.factDigest().toLocalizedText("de"), result.summary)
            assertTrue("Blutdruck" in result.summary)
            assertTrue("Tagebuch" in result.summary)
            assertTrue("Headache again after lunch [e4]" in result.summary)
            assertTrue(result.lines.any { "Blutdruck" in it.text && it.refs == listOf("e1") && it.supported })
            assertTrue(result.lines.any { it.refs == listOf("e4") && it.supported })
        }
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
    fun emptyPhotoAnswersStillUseTheRemainingBudget() = runTest {
        val price = ModelPrice(0.0, 1.0)
        val limited = config.copy(
            prices = mapOf(config.small.id to price, config.big.id to price),
            budgetUsdPerReport = 0.00015,
            maxPhotoOutputTokens = 100,
            maxSummaryOutputTokens = 100,
        )
        val extraPhoto = Entry("e5", "food", 1_794_940_000_000L, Food(photoRef = "photo/e5"))
        for (input in listOf(record, record.copy(entries = record.entries + extraPhoto))) {
            val ex = FakeExecutor(outputTokens = 100) { model ->
                if (model == config.small) "   " else "Morning BP 128/84 mmHg [e1]"
            }
            val result = SummaryPipeline(ex, limited).summarize(input, photos + ("photo/e5" to jpeg))

            assertEquals(listOf(config.small), ex.calls.map { it.second }, "no budget for another photo or the summary")
            assertFalse(result.usedAi)
            assertTrue(result.photoDescriptions.isEmpty())
            assertEquals(AiStep.PhotoDescription, result.usage.single().step)
            assertNear(0.0001, result.usage.single().costUsd)
            assertTrue("budget: summary without AI" in result.note.orEmpty())
            if (input != record) assertTrue("budget reached before photo e5" in result.note.orEmpty())
        }
    }

    @Test
    fun earlierLedgerCostsDoNotReduceThisReportsBudget() = runTest {
        val ledger = UsageLedger().apply {
            add(AiUsage(AiStep.Summary, config.providerName, config.big.id, 0, 0, 10.0, cached = false, millis = 0))
        }
        val ex = executor()
        val result = SummaryPipeline(ex, config, ledger = ledger).summarize(record, photos)

        assertTrue(result.usedAi)
        assertEquals(listOf(config.small, config.big), ex.calls.map { it.second })
        assertEquals(ledger.items.drop(1), result.usage)
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
                SummaryLine("Food photo, described by AI (not by the patient): oatmeal [e2]", listOf("e2"), true),
            ),
            result.lines,
        )
        assertTrue("2 line(s) with advice or diagnosis removed" in result.note.orEmpty())
    }

    @Test
    fun quotedPatientQuestionsAreKeptButAdviceOutsideQuotesIsRemoved() {
        for ((open, close) in listOf('"' to '"', '“' to '”', '«' to '»', '\'' to '\'')) {
            val question = "Patient asks: ${open}Should I keep taking aspirin?${close} [e5]"
            val advice = "Patient asks: ${open}Should I keep taking aspirin?${close}; you should stop [e5]"
            val (lines, dropped) = SummaryPipeline.checkLines("$question\n$advice", setOf("e5"))

            assertEquals(listOf(SummaryLine(question, listOf("e5"), true)), lines)
            assertEquals(1, dropped)
        }
        val apostrophes = "Patient's symptoms should improve before today's visit [e5]"
        val (lines, dropped) = SummaryPipeline.checkLines(apostrophes, setOf("e5"))
        assertTrue(lines.isEmpty(), "apostrophes in words must not hide advice")
        assertEquals(1, dropped)
    }

    @Test
    fun punctuationAfterReferencesIsAcceptedAndRemoved() {
        for (tail in listOf(".", ";", ",", ":", " . ; , : ")) {
            val (lines, dropped) = SummaryPipeline.checkLines("Morning BP 128/84 mmHg [e1]$tail", setOf("e1"))

            assertEquals(listOf(SummaryLine("Morning BP 128/84 mmHg [e1]", listOf("e1"), true)), lines)
            assertEquals(0, dropped)
        }
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

    @Test
    fun factsOnOneLineAreSplitAtReferences() {
        val (lines, _) = SummaryPipeline.checkLines(
            "Blood pressure 128/84 mmHg [e1]. Headache after lunch [e4]. Asks: \"Can I take ibuprofen?\" [e2]",
            setOf("e1", "e2", "e4"),
        )
        assertEquals(listOf(listOf("e1"), listOf("e4"), listOf("e2")), lines.map { it.refs })
        assertTrue(lines.all { it.supported })
    }

    private fun assertNear(expected: Double, actual: Double) = assertTrue(abs(expected - actual) < 1e-12, "$expected vs $actual")

    @Test
    fun aPhotoTheSummaryCitesIsNotRepeatedAndLabelsFollowTheLanguage() = runTest {
        val cites = FakeExecutor { model -> if (model == config.small) "Oatmeal with banana" else "Oatmeal with banana for breakfast [e2]\nHeadache after lunch [e4]" }
        assertEquals(listOf(listOf("e2"), listOf("e4")), SummaryPipeline(cites, config).summarize(record, photos).lines.map { it.refs })
        val leaves = FakeExecutor { model -> if (model == config.small) "Haferbrei mit Banane" else "Kopfschmerzen nach dem Mittag [e4]" }
        assertEquals(
            "Essensfoto, von KI beschrieben (nicht von der Person selbst): Haferbrei mit Banane [e2]",
            SummaryPipeline(leaves, config).summarize(record, photos, language = "de").lines.last().text,
        )
    }

    @Test
    fun anAnswerCutAtTheTokenLimitLosesItsBrokenLastLine() = runTest {
        // reasoning models can spend every output token; the answer then stops mid-fact
        val cut = FakeExecutor(outputTokens = config.maxSummaryOutputTokens) { model ->
            if (model == config.small) "Oatmeal" else "Morning blood pressure 128/84 mmHg [e1]\nMedica"
        }
        val result = SummaryPipeline(cut, config).summarize(record, photos)
        assertEquals(listOf(listOf("e1"), listOf("e2")), result.lines.map { it.refs })
        assertTrue("answer cut at the token limit" in result.note.orEmpty())
        // the model answers nothing at all (every token spent reasoning): facts without AI, the photo label still added
        val empty = FakeExecutor(outputTokens = config.maxSummaryOutputTokens) { model -> if (model == config.small) "Oatmeal" else "" }
        val facts = SummaryPipeline(empty, config).summarize(record, photos)
        assertEquals(false, facts.usedAi)
        assertEquals("Food photo, described by AI (not by the patient): Oatmeal [e2]", facts.lines.last().text)
                // an unsupported last line in a complete answer stays, marked unsupported
        val whole = FakeExecutor(outputTokens = 50) { model -> if (model == config.small) "Oatmeal" else "BP 128/84 [e1]\nPatient feels tired" }
        assertEquals(false, SummaryPipeline(whole, config).summarize(record, photos).lines[1].supported)
    }
}
