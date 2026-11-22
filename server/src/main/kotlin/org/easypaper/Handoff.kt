package org.easypaper

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import org.easypaper.model.EasyPaperJson
import org.easypaper.report.HANDOFF_CLAIM_HEADER
import org.easypaper.report.HANDOFF_CLINIC_HEADER
import org.easypaper.report.HANDOFF_CREATOR_HEADER
import org.easypaper.report.HANDOFF_TTL_MILLIS
import org.easypaper.report.HandoffClaim
import org.easypaper.report.HandoffPublicKey
import org.easypaper.report.HandoffSlot
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Blind, in-memory five-minute rendezvous. All state transitions, TTL checks and guesses are atomic. */
class Handoff(
    val maxBytes: Long = 2L * 1024 * 1024,
    private val maxTotalBytes: Long = 256L * 1024 * 1024,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxSlots: Int = 10_000,
) {
    private class Slot(val creatorDigest: ByteArray, val expiresAt: Long) {
        var publicKey: String? = null
        var claimDigest: ByteArray? = null
        var report: ByteArray? = null
    }

    data class Result<T>(val status: HttpStatusCode, val value: T? = null)

    private val random = SecureRandom()
    private val slots = HashMap<String, Slot>()
    private val failures = HashMap<String, ArrayDeque<Long>>()
    private val creations = HashMap<String, ArrayDeque<Long>>()

    init {
        require(maxSlots in 1..1_000_000)
        require(maxBytes > 0 && maxBytes < Long.MAX_VALUE && maxTotalBytes > 0)
    }

    /** Null when the table is full or [ip] opened [MAX_CREATES_PER_MINUTE] windows in the last minute (no slot flooding). */
    @Synchronized
    fun create(ip: String = "local"): HandoffSlot? {
        purge()
        val recent = creations.getOrPut(ip) { ArrayDeque() }
        if (slots.size >= maxSlots || recent.size >= MAX_CREATES_PER_MINUTE) return null
        recent.addLast(now())
        var code: String
        do { code = random.nextInt(1_000_000).toString().padStart(6, '0') } while (code in slots)
        val secret = secret()
        val expiresAt = now() + HANDOFF_TTL_MILLIS
        slots[code] = Slot(digest(secret), expiresAt)
        return HandoffSlot(code, secret, expiresAt)
    }

    @Synchronized
    fun claim(code: String, publicKey: String, ip: String): Result<HandoffClaim> {
        purge()
        if ((failures[ip]?.size ?: 0) >= 5) return Result(HttpStatusCode.TooManyRequests)
        val slot = slots[code] ?: run {
            failures.getOrPut(ip) { ArrayDeque() }.addLast(now())
            return Result(HttpStatusCode.NotFound)
        }
        if (slot.publicKey != null) return Result(HttpStatusCode.Conflict)
        val claimSecret = secret()
        slot.publicKey = publicKey
        slot.claimDigest = digest(claimSecret)
        return Result(HttpStatusCode.OK, HandoffClaim(claimSecret))
    }

    @Synchronized
    fun key(code: String, secret: String?): Result<HandoffPublicKey> {
        purge()
        val slot = slots[code]?.takeIf { matches(it.creatorDigest, secret) } ?: return Result(HttpStatusCode.NotFound)
        val key = slot.publicKey ?: return Result(HttpStatusCode.NoContent)
        return Result(HttpStatusCode.OK, HandoffPublicKey(key))
    }

    @Synchronized
    fun upload(code: String, secret: String?, bytes: ByteArray): HttpStatusCode {
        purge()
        val slot = slots[code]?.takeIf { matches(it.creatorDigest, secret) } ?: return HttpStatusCode.NotFound
        if (slot.report != null || slot.publicKey == null) return HttpStatusCode.Conflict
        if (bytes.size > maxBytes) return HttpStatusCode.PayloadTooLarge
        if (bytes.isEmpty()) return HttpStatusCode.BadRequest
        if (slots.values.sumOf { it.report?.size?.toLong() ?: 0L } + bytes.size > maxTotalBytes) return HttpStatusCode.InsufficientStorage
        slot.report = bytes.copyOf()
        return HttpStatusCode.Created
    }

    @Synchronized
    fun take(code: String, claimSecret: String?): Result<ByteArray> {
        purge()
        val slot = slots[code]?.takeIf { matches(it.claimDigest, claimSecret) } ?: return Result(HttpStatusCode.NotFound)
        val bytes = slot.report ?: return Result(HttpStatusCode.NoContent)
        slots.remove(code)
        return Result(HttpStatusCode.OK, bytes)
    }

    /** Also called on every operation, so expiry never depends on the background job. */
    @Synchronized
    fun purge() {
        val time = now()
        slots.entries.removeAll { it.value.expiresAt <= time }
        failures.values.forEach { times ->
            while (times.isNotEmpty() && time - times.first() >= 60_000) times.removeFirst()
        }
        failures.entries.removeAll { it.value.isEmpty() }
        creations.values.forEach { times ->
            while (times.isNotEmpty() && time - times.first() >= 60_000) times.removeFirst()
        }
        creations.entries.removeAll { it.value.isEmpty() }
    }

    private fun secret(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
}

private const val MAX_CREATES_PER_MINUTE = 20

private fun digest(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))

private fun matches(expected: ByteArray?, supplied: String?): Boolean =
    expected != null && supplied != null && MessageDigest.isEqual(expected, digest(supplied))

fun Route.handoffRoutes(handoff: Handoff, clinicToken: String?) {
    val clinicDigest = clinicToken?.takeIf { it.isNotEmpty() }?.let(::digest)

    post("/handoff") {
        val slot = handoff.create(call.handoffIp()) ?: return@post call.respond(HttpStatusCode.TooManyRequests)
        call.handoffJson(HandoffSlot.serializer(), slot, HttpStatusCode.Created)
    }
    post("/handoff/{code}/claim") {
        call.noHandoffCache()
        if (!matches(clinicDigest, call.request.headers[HANDOFF_CLINIC_HEADER])) return@post call.respond(HttpStatusCode.Unauthorized)
        val body = call.handoffBody(1024) ?: return@post
        val key = try {
            EasyPaperJson.decodeFromString(HandoffPublicKey.serializer(), body.decodeToString()).publicKey.also {
                val decoded = Base64.getDecoder().decode(it)
                require(decoded.size == 65 && decoded[0] == 4.toByte())
            }
        } catch (_: SerializationException) {
            return@post call.respond(HttpStatusCode.BadRequest)
        } catch (_: IllegalArgumentException) {
            return@post call.respond(HttpStatusCode.BadRequest)
        }
        val result = handoff.claim(call.parameters["code"].orEmpty(), key, call.handoffIp())
        val value = result.value ?: return@post call.respond(result.status)
        call.handoffJson(HandoffClaim.serializer(), value)
    }
    get("/handoff/{code}/key") {
        call.noHandoffCache()
        val result = handoff.key(call.parameters["code"].orEmpty(), call.request.headers[HANDOFF_CREATOR_HEADER])
        val value = result.value ?: return@get call.respond(result.status)
        call.handoffJson(HandoffPublicKey.serializer(), value)
    }
    post("/handoff/{code}/report") {
        call.noHandoffCache()
        val code = call.parameters["code"].orEmpty()
        val secret = call.request.headers[HANDOFF_CREATOR_HEADER]
        // Authenticate before receiving a potentially large body; recheck under the upload lock after reading.
        if (handoff.key(code, secret).status == HttpStatusCode.NotFound) return@post call.respond(HttpStatusCode.NotFound)
        val bytes = call.handoffBody(handoff.maxBytes) ?: return@post
        call.respond(handoff.upload(code, secret, bytes))
    }
    get("/handoff/{code}/report") {
        call.noHandoffCache()
        if (!matches(clinicDigest, call.request.headers[HANDOFF_CLINIC_HEADER])) return@get call.respond(HttpStatusCode.Unauthorized)
        val result = handoff.take(call.parameters["code"].orEmpty(), call.request.headers[HANDOFF_CLAIM_HEADER])
        val bytes = result.value ?: return@get call.respond(result.status)
        call.respondBytes(bytes, ContentType.Application.OctetStream)
    }
}

private fun RoutingCall.handoffIp(): String {
    return handoffClientIp(request.local.remoteAddress, request.headers["X-Forwarded-For"])
}

internal fun handoffClientIp(remote: String, forwardedFor: String?): String {
    return if (remote == "127.0.0.1" || remote == "::1") {
        forwardedFor?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() } ?: remote
    } else remote
}

private fun RoutingCall.noHandoffCache() = response.header(HttpHeaders.CacheControl, "no-store")

private suspend fun <T> RoutingCall.handoffJson(serializer: KSerializer<T>, value: T, status: HttpStatusCode = HttpStatusCode.OK) {
    noHandoffCache()
    respondText(EasyPaperJson.encodeToString(serializer, value), ContentType.Application.Json, status)
}

private suspend fun RoutingCall.handoffBody(limit: Long): ByteArray? {
    if ((request.contentLength() ?: 0) > limit) {
        respond(HttpStatusCode.PayloadTooLarge)
        return null
    }
    val bytes = receiveChannel().readRemaining(limit + 1).readByteArray()
    if (bytes.size > limit) {
        respond(HttpStatusCode.PayloadTooLarge)
        return null
    }
    return bytes
}
