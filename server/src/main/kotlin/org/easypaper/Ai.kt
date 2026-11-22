package org.easypaper

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutorAPI
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLDecodeException
import io.ktor.http.decodeURLPart
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.contentLength
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import org.easypaper.ai.AiConfig
import org.easypaper.ai.AiStatus
import org.easypaper.ai.DocumentExtraction
import org.easypaper.ai.DocumentExtractor
import org.easypaper.ai.DocumentRequest
import org.easypaper.ai.InMemoryAiCache
import org.easypaper.ai.MedicineLabelReader
import org.easypaper.ai.AiStep
import org.easypaper.ai.AiUsage
import org.easypaper.ai.SummaryPipeline
import org.easypaper.ai.SummaryRequest
import org.easypaper.ai.SummaryResult
import org.easypaper.ai.UsageLedger
import org.easypaper.ai.clinicExecutor
import org.easypaper.ai.cloudExecutor
import org.easypaper.ai.togetherExecutor
import org.easypaper.model.EasyPaperJson
import java.security.MessageDigest
import java.util.ArrayDeque
import kotlin.io.encoding.Base64

enum class AiMode { CLOUD, CLINIC, OFF }

/**
 * The server's AI (task 8). CLOUD: Koog + Together/OpenRouter with the key from this process's environment only — it is
 * never logged, returned or sent to a client — and a hard spending cap for the team's $10 key. CLINIC: Koog +
 * local Ollama, nothing leaves the clinic, documents can be read. OFF: summaries are the facts without AI.
 * One request at a time: the demo's laptop runs a 12B model, and the ledger stays in order.
 */
class AiService(
    val mode: AiMode,
    private val executor: PromptExecutorAPI?,
    val config: AiConfig?,
    val budgetUsd: Double,
    private val keyConfigured: Boolean,
) {
    private val cache = InMemoryAiCache()
    val ledger = UsageLedger()
    private val lock = Mutex()
    // Hashes and printed text only: no photos retained. Bounded for the public patient endpoint.
    private val labels = LinkedHashMap<String, String>()

    suspend fun readMedicineLabel(jpeg: ByteArray): String? = lock.withLock {
        val ex = executor ?: return@withLock null
        val cfg = config ?: return@withLock null
        if (mode == AiMode.OFF || budgetUsd <= ledger.totalCostUsd) return@withLock null
        val hash = MessageDigest.getInstance("SHA-256").digest(jpeg).joinToString("") { "%02x".format(it) }
        labels[hash]?.let {
            ledger.add(AiUsage(AiStep.MedicineLabel, cfg.providerName, cfg.small.id, 0, 0, 0.0, cached = true, millis = 0))
            return@withLock it
        }
        // A bounded label cache owns this endpoint's answers; avoid duplicating photos' answers in the report cache.
        val text = MedicineLabelReader(ex, cfg, InMemoryAiCache(), ledger).read(jpeg, budgetUsd - ledger.totalCostUsd)
        if (text != null) {
            if (labels.size >= 128) labels.remove(labels.keys.first())
            labels[hash] = text
        }
        text
    }

    fun status() = AiStatus(
        mode = mode.name.lowercase(),
        provider = config?.providerName,
        smallModel = config?.small?.id,
        bigModel = config?.big?.id,
        prices = config?.prices.orEmpty(),
        keyConfigured = keyConfigured,
        spentUsd = ledger.totalCostUsd,
        budgetUsd = budgetUsd.takeIf { it.isFinite() && it < Double.MAX_VALUE },
        calls = ledger.items.count { !it.cached },
        cacheHits = ledger.cacheHits,
        inputTokens = ledger.totalInputTokens,
        outputTokens = ledger.totalOutputTokens,
    )

    suspend fun summarize(request: SummaryRequest): SummaryResult = lock.withLock {
        val left = budgetUsd - ledger.totalCostUsd
        val base = config ?: AiConfig.cloud()
        // Over the cap or no AI: the same pipeline gives the facts without a single model call.
        val cfg = if (executor == null || left <= 0.0) base.copy(budgetUsdPerReport = 0.0)
        else base.copy(budgetUsdPerReport = minOf(base.budgetUsdPerReport, left))
        val photos = request.photos.mapValues { Base64.Default.decode(it.value) }
        SummaryPipeline(executor ?: NoAi, cfg, cache, ledger)
            .summarize(request.record, photos, request.utcOffsetMinutes, request.language)
            .let { if (executor == null) it.copy(note = "AI is off on this server: facts without AI") else it }
    }

    suspend fun extract(request: DocumentRequest): DocumentExtraction = lock.withLock {
        val ex = executor ?: error("no AI")
        DocumentExtractor(ex, config!!, cache, ledger)
            .extract(request.documentId, request.text, request.imageBase64?.let { Base64.Default.decode(it) }, request.imageFormat)
    }

    companion object {
        /**
         * EASYPAPER_AI = cloud | clinic | off (default: cloud when either cloud key is set, otherwise off).
         * EASYPAPER_CLOUD = together | openrouter (default: together when TOGETHER_API_KEY is set, otherwise openrouter).
         * EASYPAPER_BUDGET_USD caps cloud spending (default 9.00 of the team's $10). EASYPAPER_OLLAMA_URL and
         * EASYPAPER_OLLAMA_MODEL select the clinic module's local model.
         */
        fun fromEnv(env: (String) -> String? = System::getenv): AiService {
            val togetherKey = env("TOGETHER_API_KEY")?.trim().orEmpty()
            val openRouterKey = env("OPENROUTER_API_KEY")?.trim().orEmpty()
            val useTogether = when (env("EASYPAPER_CLOUD")?.lowercase()) {
                "together" -> true
                "openrouter" -> false
                else -> togetherKey.isNotEmpty()
            }
            val key = if (useTogether) togetherKey else openRouterKey
            val mode = when (env("EASYPAPER_AI")?.lowercase()) {
                "cloud" -> AiMode.CLOUD
                "clinic" -> AiMode.CLINIC
                "off" -> AiMode.OFF
                else -> if (togetherKey.isNotEmpty() || openRouterKey.isNotEmpty()) AiMode.CLOUD else AiMode.OFF
            }
            return when (mode) {
                AiMode.CLOUD -> AiService(
                    AiMode.CLOUD,
                    key.takeIf { it.isNotEmpty() }?.let { if (useTogether) togetherExecutor(it) else cloudExecutor(it) },
                    if (useTogether) AiConfig.together() else AiConfig.cloud(),
                    env("EASYPAPER_BUDGET_USD")?.toDoubleOrNull() ?: 9.0, keyConfigured = key.isNotEmpty(),
                )
                AiMode.CLINIC -> AiService(
                    AiMode.CLINIC, clinicExecutor(env("EASYPAPER_OLLAMA_URL") ?: "http://localhost:11434"),
                    AiConfig.clinic(env("EASYPAPER_OLLAMA_MODEL") ?: "gemma4:12b"), Double.MAX_VALUE, keyConfigured = false,
                )
                AiMode.OFF -> off()
            }
        }

        fun off() = AiService(AiMode.OFF, null, null, 0.0, keyConfigured = false)
    }
}

/** Stand-in executor when AI is off; never called because the budget is 0. */
private object NoAi : PromptExecutorAPI {
    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant = error("AI is off")
    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = error("AI is off")
    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("AI is off")
    override fun close() {}
}

const val MAX_AI_BODY_BYTES = 8L * 1024 * 1024

/** Sliding minute per connection IP; forwarded headers are not trusted. Shared by all AI routes. */
internal class AiRateLimiter(private val nanoTime: () -> Long = System::nanoTime) {
    private val requests = mutableMapOf<String, ArrayDeque<Long>>()

    @Synchronized
    fun allow(ip: String): Boolean {
        val now = nanoTime()
        val minute = 60_000_000_000L
        // Drop inactive IPs so the map does not retain them indefinitely.
        requests.entries.removeIf { now - it.value.peekLast() >= minute }
        val times = requests.getOrPut(ip) { ArrayDeque() }
        while (times.isNotEmpty() && now - times.peekFirst() >= minute) times.removeFirst()
        if (times.size >= 30) return false
        times.addLast(now)
        return true
    }
}

/** Runs before routing, including unknown AI paths and unsupported methods. Never logs credentials. */
internal fun Application.protectAiRoutes(mode: AiMode, token: String?) {
    val tokenDigest = token?.takeIf { it.isNotEmpty() }?.let(::hashAiToken)
    val limiter = AiRateLimiter()
    intercept(ApplicationCallPipeline.Plugins) {
        // Match Ktor's decoded path segments so encoded letters or repeated slashes cannot bypass access checks.
        val segments = try {
            call.request.path().split('/').filter { it.isNotEmpty() }.map { it.decodeURLPart() }
        } catch (_: URLDecodeException) {
            call.respondText("Bad request path", status = HttpStatusCode.BadRequest)
            finish()
            return@intercept
        }
        if (segments.firstOrNull() == "ai") {
            // The token comes first: a request without it is refused and never uses up the clinic's limit (#65).
            val access = when {
                tokenDigest == null -> {
                    if (mode == AiMode.OFF && segments == listOf("ai", "status") && call.request.httpMethod == HttpMethod.Get) null
                    else HttpStatusCode.ServiceUnavailable to "AI access token not configured"
                }
                else -> {
                    val supplied = call.request.headers["X-EasyPaper-Clinic-Token"].orEmpty()
                    // Both digests have the same fixed length, including for missing or wrong-length tokens.
                    val digest = hashAiToken(supplied)
                    if (MessageDigest.isEqual(tokenDigest, digest)) null else HttpStatusCode.Unauthorized to "Unauthorized"
                }
            }
            val client = aiClientKey(call.request.local.remoteAddress, call.request.headers["X-Forwarded-For"])
            val failure = access ?: if (!limiter.allow(client)) HttpStatusCode.TooManyRequests to "Too many AI requests" else null
            if (failure != null) {
                call.respondText(failure.second, status = failure.first)
                finish()
            }
        }
    }
}

/**
 * Whom the AI limit counts: behind the local reverse proxy (Caddy on the VM, connecting from loopback) the client is
 * the last X-Forwarded-For address — the one the proxy itself wrote (Caddy replaces a client's header by default; a
 * proxy that appends still puts the real address last, while earlier ones can be forged). A connection from anywhere
 * else is its own address, so a forged header from the internet changes nothing.
 */
internal fun aiClientKey(remote: String, forwardedFor: String?): String {
    val fromProxy = remote == "127.0.0.1" || remote == "::1" || remote == "0:0:0:0:0:0:0:1" || remote == "localhost"
    val client = forwardedFor?.substringAfterLast(',')?.trim()?.takeIf { it.isNotEmpty() }
    return if (fromProxy && client != null) client else remote
}

private fun hashAiToken(token: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))

fun Route.aiRoutes(ai: AiService) {
    get("/ai/status") { call.json(EasyPaperJson.encodeToString(AiStatus.serializer(), ai.status())) }
    post("/ai/summary") {
        val request = call.receiveJson(SummaryRequest.serializer()) ?: return@post
        val result = try {
            ai.summarize(request)
        } catch (e: IllegalArgumentException) { // photo that is not base64
            return@post call.respondText("Bad photo data", status = HttpStatusCode.BadRequest)
        }
        call.json(EasyPaperJson.encodeToString(SummaryResult.serializer(), result))
    }
    post("/ai/documents") {
        if (ai.mode != AiMode.CLINIC) {
            return@post call.respondText("Documents are read only by the clinic module; they must not leave the clinic.",
                status = HttpStatusCode.Forbidden)
        }
        val request = call.receiveJson(DocumentRequest.serializer()) ?: return@post
        if (request.text == null && request.imageBase64 == null) {
            return@post call.respondText("Send the document as text or imageBase64", status = HttpStatusCode.BadRequest)
        }
        call.json(EasyPaperJson.encodeToString(DocumentExtraction.serializer(), ai.extract(request)))
    }
}

private suspend fun RoutingCall.json(text: String) = respondText(text, ContentType.Application.Json)

/** Reads a size-limited JSON body; answers 413/400 itself and returns null when the body is not acceptable. */
private suspend fun <T> RoutingCall.receiveJson(serializer: KSerializer<T>): T? {
    val declared = request.contentLength()
    if (declared != null && declared > MAX_AI_BODY_BYTES) { respondText("Too large", status = HttpStatusCode.PayloadTooLarge); return null }
    val bytes = receiveChannel().readRemaining(MAX_AI_BODY_BYTES + 1).readByteArray()
    if (bytes.size > MAX_AI_BODY_BYTES) { respondText("Too large", status = HttpStatusCode.PayloadTooLarge); return null }
    return try {
        EasyPaperJson.decodeFromString(serializer, bytes.decodeToString())
    } catch (e: SerializationException) {
        respondText("Bad request body", status = HttpStatusCode.BadRequest); null
    } catch (e: IllegalArgumentException) {
        respondText("Bad request body", status = HttpStatusCode.BadRequest); null
    }
}
