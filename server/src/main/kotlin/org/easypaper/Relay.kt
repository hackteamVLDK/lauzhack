package org.easypaper

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/**
 * Blind relay for sealed reports (#9/#14): stores the encrypted bytes it is given, hands each object out once and
 * forgets it, or forgets it after [ttlMillis]. It never sees a key (the key is only in the patient's QR) and never
 * logs bodies or ids.
 * shortcut: kept in memory, lost on restart — enough for the demo; use a disk/DB store for a real deployment.
 */
class Relay(
    val maxBytes: Long = 2L * 1024 * 1024,
    val maxTotalBytes: Long = 256L * 1024 * 1024,
    val ttlMillis: Long = 72L * 3600 * 1000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Stored(val bytes: ByteArray, val expires: Long)
    private val objects = HashMap<String, Stored>()

    enum class PutResult { CREATED, EXISTS, FULL }

    @Synchronized
    fun put(key: String, bytes: ByteArray): PutResult {
        purgeExpired()
        if (key in objects) return PutResult.EXISTS
        if (objects.values.sumOf { it.bytes.size.toLong() } + bytes.size > maxTotalBytes) return PutResult.FULL
        objects[key] = Stored(bytes, now() + ttlMillis)
        return PutResult.CREATED
    }

    /** Returns the object once and removes it; null when absent or expired. */
    @Synchronized
    fun take(key: String): ByteArray? {
        purgeExpired()
        return objects.remove(key)?.bytes
    }

    @Synchronized
    internal fun peek(key: String): ByteArray? = objects[key]?.bytes // tests only

    /** Drops everything past its TTL; also called on every request. */
    @Synchronized
    fun purge() = purgeExpired()

    private fun purgeExpired() {
        val t = now()
        objects.entries.removeAll { it.value.expires <= t }
    }

    companion object {
        fun fromEnv(env: (String) -> String? = System::getenv) = Relay(
            maxBytes = env("RELAY_MAX_BYTES")?.toLongOrNull() ?: 2L * 1024 * 1024,
            maxTotalBytes = env("RELAY_MAX_TOTAL_BYTES")?.toLongOrNull() ?: 256L * 1024 * 1024,
            ttlMillis = (env("RELAY_TTL_HOURS")?.toLongOrNull() ?: 72L) * 3600 * 1000,
        )
    }
}

private val ID = Regex("[A-Za-z0-9_-]{8,64}")
// photo refs look like "photo/<entryId>" (#27): slashes allowed, no empty, "." or ".." parts
private val REF = Regex("[A-Za-z0-9_./-]{1,128}")

fun Route.relayRoutes(relay: Relay) {
    post("/reports/{id}") { upload(relay, call, key(call, photo = false)) }
    post("/reports/{id}/photos/{ref}") { upload(relay, call, key(call, photo = true)) }
    get("/reports/{id}") { download(relay, call, key(call, photo = false)) }
    get("/reports/{id}/photos/{ref}") { download(relay, call, key(call, photo = true)) }
}

/** Storage key, or null when the id/ref is not acceptable. */
private fun key(call: RoutingCall, photo: Boolean): String? {
    val id = call.parameters["id"]?.takeIf { ID.matches(it) } ?: return null
    if (!photo) return "report/$id"
    val ref = call.parameters["ref"]?.takeIf { r -> REF.matches(r) && r.split('/').none { it.isEmpty() || it == "." || it == ".." } }
        ?: return null
    return "photo/$id/$ref"
}

private suspend fun upload(relay: Relay, call: RoutingCall, key: String?) {
    if (key == null) return call.respond(HttpStatusCode.BadRequest)
    val declared = call.request.contentLength()
    if (declared != null && declared > relay.maxBytes) return call.respond(HttpStatusCode.PayloadTooLarge)
    val bytes = call.receiveChannel().readRemaining(relay.maxBytes + 1).readByteArray()
    if (bytes.size > relay.maxBytes) return call.respond(HttpStatusCode.PayloadTooLarge)
    if (bytes.isEmpty()) return call.respond(HttpStatusCode.BadRequest)
    when (relay.put(key, bytes)) {
        Relay.PutResult.CREATED -> call.respond(HttpStatusCode.Created)
        Relay.PutResult.EXISTS -> call.respond(HttpStatusCode.Conflict)
        Relay.PutResult.FULL -> call.respond(HttpStatusCode.InsufficientStorage)
    }
}

private suspend fun download(relay: Relay, call: RoutingCall, key: String?) {
    val bytes = key?.let { relay.take(it) } ?: return call.respond(HttpStatusCode.NotFound)
    call.response.header(HttpHeaders.CacheControl, "no-store")
    call.respondBytes(bytes, ContentType.Application.OctetStream)
}
