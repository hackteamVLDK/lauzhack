package org.easypaper.catalog

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.easypaper.aiClientKey
import org.easypaper.AiService
import org.easypaper.report.stripJpegMetadata
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.easypaper.model.EasyPaperJson

/** Requests per minute and IP: lookups are cheap, but the catalog must not become a free bulk API. */
private const val PER_MINUTE = 60
private const val MAX_QUERY_BYTES = 16_000L

/**
 * Public, read-only catalog routes. The app sends only what it read on a package (codes and printed text):
 * A patient can separately request reading a package photo online. A null country searches all loaded snapshots.
 */
fun Route.catalogRoutes(catalog: CatalogService, ai: AiService = AiService.off(), labelLimiter: LabelReadLimiter = LabelReadLimiter()) {
    val limiter = MinuteLimiter(PER_MINUTE)
    get("/catalog/coverage") {
        call.respondText(EasyPaperJson.encodeToString(Coverage.serializer(), catalog.coverage()), ContentType.Application.Json)
    }
    post("/catalog/resolve") {
        // behind Caddy every request comes from 127.0.0.1: the client is the last X-Forwarded-For entry (#65)
        if (!limiter.allow(aiClientKey(call.request.local.remoteAddress, call.request.headers["X-Forwarded-For"]))) {
            return@post call.respondText("Too many requests", status = HttpStatusCode.TooManyRequests)
        }
        // never read more than the limit, whatever the client claims
        if ((call.request.contentLength() ?: 0) > MAX_QUERY_BYTES) return@post call.respondText("Query too long", status = HttpStatusCode.PayloadTooLarge)
        val bytes = call.receiveChannel().readRemaining(MAX_QUERY_BYTES + 1).readByteArray()
        if (bytes.size > MAX_QUERY_BYTES) return@post call.respondText("Query too long", status = HttpStatusCode.PayloadTooLarge)
        val body = bytes.decodeToString()
        val query = runCatching { EasyPaperJson.decodeFromString(CatalogQuery.serializer(), body) }.getOrNull()
            ?: return@post call.respondText("Bad query", status = HttpStatusCode.BadRequest)
        call.respondText(EasyPaperJson.encodeToString(Resolution.serializer(), catalog.resolve(query)), ContentType.Application.Json)
    }
    post("/catalog/read-label") {
        val address = aiClientKey(call.request.local.remoteAddress, call.request.headers["X-Forwarded-For"])
        if (!labelLimiter.allow(address)) return@post call.respondText("Too many requests", status = HttpStatusCode.TooManyRequests)
        if ((call.request.contentLength() ?: 0) > MAX_LABEL_BYTES) return@post call.respondText("Photo too large", status = HttpStatusCode.PayloadTooLarge)
        val bytes = call.receiveChannel().readRemaining(MAX_LABEL_BYTES + 1).readByteArray()
        if (bytes.size > MAX_LABEL_BYTES) return@post call.respondText("Photo too large", status = HttpStatusCode.PayloadTooLarge)
        // Reuse photo privacy handling, including validation and keeping only minimal orientation metadata.
        val photo = runCatching { stripJpegMetadata(bytes) }.getOrNull()
            ?: return@post call.respondText("Bad JPEG", status = HttpStatusCode.BadRequest)
        val text = ai.readMedicineLabel(photo)
            ?: return@post call.respondText("Reading online is not available right now", status = HttpStatusCode.ServiceUnavailable)
        val result = catalog.resolve(CatalogQuery(text = text))
        call.respondText(EasyPaperJson.encodeToString(Resolution.serializer(), result), ContentType.Application.Json)
    }
}

private const val MAX_LABEL_BYTES = 1_500_000L

/** Both limits are sliding windows; rejected requests never extend either window. */
class LabelReadLimiter(private val nanoTime: () -> Long = System::nanoTime) {
    private val requests = HashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun allow(address: String): Boolean {
        val now = nanoTime()
        val minute = 60_000_000_000L
        val day = 24 * 60 * minute
        requests.entries.removeIf { now - it.value.last() >= day }
        val times = requests.getOrPut(address) { ArrayDeque() }
        while (times.isNotEmpty() && now - times.first() >= day) times.removeFirst()
        if (times.size >= 60 || times.count { now - it < minute } >= 10) return false
        times.addLast(now)
        return true
    }
}

/** Sliding minute per connection address. */
internal class MinuteLimiter(private val limit: Int, private val nanoTime: () -> Long = System::nanoTime) {
    private val requests = HashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun allow(key: String): Boolean {
        val now = nanoTime()
        val minute = 60_000_000_000L
        requests.entries.removeIf { now - it.value.last() >= minute }
        val times = requests.getOrPut(key) { ArrayDeque() }
        while (times.isNotEmpty() && now - times.first() >= minute) times.removeFirst()
        if (times.size >= limit) return false
        times.addLast(now)
        return true
    }
}
