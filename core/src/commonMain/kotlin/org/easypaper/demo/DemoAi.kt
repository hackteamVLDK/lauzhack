package org.easypaper.demo

import org.easypaper.ai.AiStep
import org.easypaper.ai.AiUsage

/** Captured usage from a real local model run; these figures do not update live. */
object DemoAi {
    val usage: List<AiUsage> = listOf(
        AiUsage(AiStep.Summary, "Ollama (clinic module)", "gemma4:12b", 1279, 443, 0.0, cached = false, millis = 8734),
    )
}
