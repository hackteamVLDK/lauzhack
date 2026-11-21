package org.easypaper

import io.ktor.server.testing.testApplication
import org.easypaper.ai.AiClient
import org.easypaper.demo.Demo
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The server with a real model, off by default. EASYPAPER_LIVE=ollama runs the clinic module against the local
 * Ollama (daytime only); EASYPAPER_LIVE=openrouter uses OPENROUTER_API_KEY from the environment (never printed).
 * EASYPAPER_LIVE=together uses TOGETHER_API_KEY from the environment (never printed).
 */
class LiveAiServerTest {
    @Test
    fun clinicModuleEndToEnd() {
        val live = System.getenv("EASYPAPER_LIVE")
        if (live !in listOf("ollama", "openrouter", "together")) {
            return println("LiveAiServerTest skipped: set EASYPAPER_LIVE=ollama, openrouter or together")
        }
        val ai = AiService.fromEnv { name ->
            when (name) {
                "EASYPAPER_AI" -> if (live == "ollama") "clinic" else "cloud"
                "EASYPAPER_CLOUD" -> live
                else -> System.getenv(name)
            }
        }
        val token = System.getenv("EASYPAPER_CLINIC_TOKEN")
            ?: error("Set EASYPAPER_CLINIC_TOKEN for the live server test")
        testApplication {
            application { module(ai = ai, aiToken = token) }
            val client = AiClient("", client, token = token)
            val summary = client.summarize(Demo.record)
            println("=== ${ai.status().provider}: usedAi=${summary.usedAi} note=${summary.note}")
            summary.lines.forEach { println((if (it.supported) "  ok  " else "  ??  ") + it.text) }
            assertTrue(summary.usedAi, "no AI answer: ${summary.note}")
            if (ai.mode == AiMode.CLINIC) {
                val doc = client.extractDocument(
                    "letter-2026-10",
                    text = "Discharge letter, 12 October 2026. Diagnosis: migraine without aura. Current medication: " +
                        "Ramipril 5 mg once daily, Pantoprazol 20 mg in the morning. Allergies: penicillin (rash). " +
                        "Lab: creatinine 0.9 mg/dL.",
                )
                println("=== document: usedAi=${doc.usedAi} note=${doc.note}")
                doc.facts.forEach { println("  ${it.field}: ${it.value}   <- \"${it.quote}\" verified=${it.verified}") }
                assertTrue(doc.facts.isNotEmpty(), "no facts from the document: ${doc.note}")
            }
            val status = client.status()
            println("=== spent \$${status.spentUsd}, calls ${status.calls}, tokens ${status.inputTokens}/${status.outputTokens}")
        }
    }
}
