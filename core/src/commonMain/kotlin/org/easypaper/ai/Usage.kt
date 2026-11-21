package org.easypaper.ai

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.serialization.Serializable

@Serializable
enum class AiStep { PhotoDescription, Summary, DocumentExtraction }

/** One model call (or cache hit) as the price screen (#32) shows it. */
@Serializable
data class AiUsage(
    val step: AiStep,
    val provider: String,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val costUsd: Double,
    val cached: Boolean,
    val millis: Long,
    val reportId: String? = null,
    /** At least one token count was estimated from characters rather than returned by the provider. */
    val estimated: Boolean = false,
)

/** Every AI call of a session, in order. Never holds prompts, answers or keys — only counts and prices. */
class UsageLedger {
    private val list = mutableListOf<AiUsage>()
    val items: List<AiUsage> get() = list.toList()
    val totalCostUsd: Double get() = list.sumOf { it.costUsd }
    val totalInputTokens: Int get() = list.sumOf { it.inputTokens }
    val totalOutputTokens: Int get() = list.sumOf { it.outputTokens }
    val cacheHits: Int get() = list.count { it.cached }
    fun add(usage: AiUsage) { list += usage }
}

/** Answers by request hash. A repeated request (same model, prompt and photo) costs nothing. */
interface AiCache {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
}

class InMemoryAiCache : AiCache {
    private val map = HashMap<String, String>()
    override suspend fun get(key: String) = map[key]
    override suspend fun put(key: String, value: String) { map[key] = value }
}

/** SHA-256 hex of the parts, each length-prefixed so ("ab","c") and ("a","bc") differ. */
internal suspend fun cacheKey(vararg parts: ByteArray): String {
    val joined = parts.fold(ByteArray(0)) { acc, part -> acc + part.size.toString().encodeToByteArray() + byteArrayOf(0) + part }
    val digest = CryptographyProvider.Default.get(SHA256).hasher().hash(joined)
    return digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

/** Rough token estimate (≈ 4 characters per token) for budgets and missing provider token counts. */
internal fun estimateTokens(text: String) = text.length / 4 + 1
