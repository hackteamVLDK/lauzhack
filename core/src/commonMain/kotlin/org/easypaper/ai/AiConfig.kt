package org.easypaper.ai

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** List price of a model in US dollars per million tokens. Local models are free (no entry). */
@Serializable
data class ModelPrice(val inputPerMillionUsd: Double, val outputPerMillionUsd: Double) {
    fun cost(inputTokens: Int, outputTokens: Int) =
        (inputTokens * inputPerMillionUsd + outputTokens * outputPerMillionUsd) / 1_000_000.0
}

/**
 * Which models do which step, and the limits that keep the pipeline frugal (AXA challenge):
 * a small vision model only describes food photos the patient left without text, the big model runs
 * once per report on facts that were already computed without AI.
 */
data class AiConfig(
    /** Provider label shown on the price screen (#32). */
    val providerName: String,
    val small: LLModel,
    val big: LLModel,
    /** By model id; a model without an entry costs 0 (local). */
    val prices: Map<String, ModelPrice> = emptyMap(),
    /** Photos bigger than this are skipped, not sent (the phone already shrinks them, #28). */
    val maxPhotoBytes: Int = 200_000,
    /** At most this many photos are described per report. */
    val maxPhotos: Int = 6,
    val maxPhotoOutputTokens: Int = 40,
    val maxSummaryOutputTokens: Int = 700,
    /** Hard cap per report; when the estimate is above it, the doctor gets the facts without AI. */
    val budgetUsdPerReport: Double = 0.05,
    /** Provider-specific request fields, e.g. Ollama's "think": false. */
    val extraParams: Map<String, JsonElement> = emptyMap(),
) {
    fun price(model: LLModel): ModelPrice? = prices[model.id]

    companion object {
        /** Together list prices from /v1/models, in USD per 1M tokens. */
        fun together(): AiConfig = AiConfig(
            providerName = "Together AI (cloud)",
            // Koog's OpenRouterLLMClient registers as OpenRouter and the executor routes by model.provider,
            // even when its base URL points to Together's OpenAI-compatible API.
            small = LLModel(LLMProvider.OpenRouter, "nvidia/NVIDIA-Nemotron-3.5-Super-VL", VISION_CHAT),
            big = LLModel(
                LLMProvider.OpenRouter, "MiniMaxAI/MiniMax-M3.1-Flash",
                listOf(LLMCapability.Completion, LLMCapability.Temperature),
            ),
            prices = mapOf(
                "nvidia/NVIDIA-Nemotron-3.5-Super-VL" to ModelPrice(0.15, 0.50),
                "MiniMaxAI/MiniMax-M3.1-Flash" to ModelPrice(0.15, 0.60),
            ),
            // Both models reason before answering; with the default limits the reasoning used every output token
            // and the answer came back empty. 4000 tokens cost at most $0.0024 per summary.
            maxPhotoOutputTokens = 1_000,
            maxSummaryOutputTokens = 4_000,
        )

        /**
         * Cloud through OpenRouter. The API key lives only on the Ktor server (#8); clients never see it.
         * Prices: OpenRouter list prices in USD per 1M tokens at the time of writing; the server can override them.
         */
        fun cloud(
            small: LLModel = LLModel(LLMProvider.OpenRouter, "google/gemini-2.5-flash-lite", VISION_CHAT),
            big: LLModel = LLModel(LLMProvider.OpenRouter, "anthropic/claude-haiku-4.5", VISION_CHAT),
            prices: Map<String, ModelPrice> = mapOf(
                "google/gemini-2.5-flash-lite" to ModelPrice(0.10, 0.40),
                "anthropic/claude-haiku-4.5" to ModelPrice(1.00, 5.00),
            ),
        ) = AiConfig("OpenRouter (cloud)", small, big, prices)

        /** The clinic module: one local vision model through Ollama; nothing leaves the clinic, cloud cost 0. */
        fun clinic(modelId: String = "gemma4:12b"): AiConfig {
            val local = LLModel(LLMProvider.Ollama, modelId, VISION_CHAT)
            // Local tokens are free, but time is not: reasoning models (gemma4) answer directly with think = false;
            // the higher limits still leave room if a model ignores it.
            return AiConfig(
                "Ollama (clinic module)", small = local, big = local, budgetUsdPerReport = Double.MAX_VALUE,
                maxPhotoOutputTokens = 1_500, maxSummaryOutputTokens = 4_000,
                extraParams = mapOf("think" to JsonPrimitive(false)),
            )
        }

        private val VISION_CHAT: List<LLMCapability> = listOf(LLMCapability.Completion, LLMCapability.Temperature, LLMCapability.Vision.Image)
    }
}
