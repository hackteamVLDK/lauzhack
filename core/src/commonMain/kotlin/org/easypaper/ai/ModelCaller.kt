package org.easypaper.ai

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutorAPI
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.params.LLMParams
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/** One model call through the cache, with usage recorded in the ledger. Shared by every AI step. */
internal class ModelCaller(
    private val executor: PromptExecutorAPI,
    private val config: AiConfig,
    private val cache: AiCache,
    private val ledger: UsageLedger,
) {
    /** Last provider error (class and message, never the prompt or a key), for notes and the price screen. */
    var lastError: String? = null
        private set

    /** The answer text, or null when the call fails or the model answers nothing. */
    suspend fun call(
        step: AiStep,
        model: LLModel,
        system: String,
        user: String,
        image: ByteArray?,
        maxTokens: Int,
        imageFormat: String = "jpeg",
    ): String? {
        val key = cacheKey(model.id.encodeToByteArray(), system.encodeToByteArray(), user.encodeToByteArray(), image ?: ByteArray(0))
        cache.get(key)?.let { cached ->
            ledger.add(AiUsage(step, config.providerName, model.id, 0, 0, 0.0, cached = true, millis = 0))
            return cached
        }
        val params = LLMParams(temperature = 0.0, maxTokens = maxTokens, additionalProperties = config.extraParams.ifEmpty { null })
        val p = prompt("easypaper-${step.name.lowercase()}", params) {
            system(system)
            user {
                part(MessagePart.Text(user))
                if (image != null) image(AttachmentSource.Image(AttachmentContent.Binary.Bytes(image), imageFormat))
            }
        }
        val mark = TimeSource.Monotonic.markNow()
        val response = try {
            executor.execute(p, model)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            lastError = (e::class.simpleName ?: "error") + ": " + (e.message ?: "").take(200)
            return null
        }
        val text = response.textContent().trim()
        val input = response.metaInfo.inputTokensCount ?: estimateTokens(system + user)
        val output = response.metaInfo.outputTokensCount ?: estimateTokens(text)
        ledger.add(
            AiUsage(
                step, config.providerName, model.id, input, output, config.price(model)?.cost(input, output) ?: 0.0,
                cached = false, millis = mark.elapsedNow().inWholeMilliseconds,
            ),
        )
        if (text.isNotEmpty()) cache.put(key, text)
        return text.ifEmpty { null }
    }

    fun estimateCost(model: LLModel, inputTokens: Int, outputTokens: Int) =
        config.price(model)?.cost(inputTokens, outputTokens) ?: 0.0
}
