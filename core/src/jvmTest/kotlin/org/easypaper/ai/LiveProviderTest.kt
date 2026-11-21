package org.easypaper.ai

import kotlinx.coroutines.runBlocking
import org.easypaper.demo.Demo
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Real model run, off by default (no network in CI and no Ollama at night):
 *   EASYPAPER_LIVE=ollama  -> local clinic module (Ollama, gemma4:12b)
 *   EASYPAPER_LIVE=openrouter + OPENROUTER_API_KEY -> cloud
 * Prints the summary and the usage ledger; never prints the key.
 */
class LiveProviderTest {
    @Test
    fun summarizeDemoRecordWithARealModel() = runBlocking {
        val (executor, config) = when (System.getenv("EASYPAPER_LIVE")) {
            "ollama" -> clinicExecutor() to AiConfig.clinic(System.getenv("EASYPAPER_OLLAMA_MODEL") ?: "gemma4:12b")
            "openrouter" -> cloudExecutor(System.getenv("OPENROUTER_API_KEY").orEmpty()) to AiConfig.cloud()
            else -> return@runBlocking println("LiveProviderTest skipped: set EASYPAPER_LIVE=ollama or openrouter")
        }
        val pipeline = SummaryPipeline(executor, config)
        val result = pipeline.summarize(Demo.record)
        println("=== ${config.providerName}: usedAi=${result.usedAi} note=${result.note}")
        result.lines.forEach { println((if (it.supported) "  ok  " else "  ??  ") + it.text) }
        pipeline.ledger.items.forEach {
            println("  ${it.step} ${it.model}: in ${it.inputTokens}, out ${it.outputTokens}, \$${"%.6f".format(it.costUsd)}, ${it.millis} ms")
        }
        assertTrue(result.usedAi, "model did not answer: ${result.note}")
        assertTrue(result.lines.count { it.supported } >= result.lines.size / 2, "most lines must cite entries")
    }
}
