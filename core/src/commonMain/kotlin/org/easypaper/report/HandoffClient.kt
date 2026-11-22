package org.easypaper.report

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.easypaper.model.EasyPaperJson
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

const val HANDOFF_TTL_MILLIS: Long = 5 * 60 * 1000L
const val HANDOFF_CREATOR_HEADER = "X-Handoff-Secret"
const val HANDOFF_CLAIM_HEADER = "X-Claim-Secret"
const val HANDOFF_CLINIC_HEADER = "X-EasyPaper-Clinic-Token"

@Serializable
data class HandoffSlot(val code: String, val secret: String, val expiresAt: Long)

@Serializable
data class HandoffPublicKey(val publicKey: String)

@Serializable
data class HandoffClaim(val claimSecret: String)

class HandoffException(val kind: Kind, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Kind { NotFound, Expired, Unauthorized, TooMany, Conflict, Network }
}

/** No ContentNegotiation plugin required. IOExceptions and caller cancellation propagate unchanged. */
@OptIn(ExperimentalTime::class, ExperimentalEncodingApi::class)
class HandoffClient(
    private val baseUrl: String,
    private val http: HttpClient,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    suspend fun create(): HandoffSlot {
        val response = http.post(baseUrl) {
            expectSuccess = false
            url { appendPathSegments("handoff") }
        }
        response.expect(HttpStatusCode.Created)
        return response.json(HandoffSlot.serializer())
    }

    suspend fun awaitDoctorKey(slot: HandoffSlot): ByteArray = poll(slot.expiresAt) {
        val response = http.get(baseUrl) {
            expectSuccess = false
            url { appendPathSegments(listOf("handoff", slot.code, "key"), encodeSlash = true) }
            header(HANDOFF_CREATOR_HEADER, slot.secret)
        }
        if (response.status == HttpStatusCode.NoContent) null else {
            response.expect(HttpStatusCode.OK)
            val key = response.json(HandoffPublicKey.serializer()).publicKey
            try {
                checkedPublicKey(Base64.decode(key))
            } catch (e: IllegalArgumentException) {
                throw HandoffException(HandoffException.Kind.Network, "Invalid doctor public key", e)
            }
        }
    }

    suspend fun upload(slot: HandoffSlot, bytes: ByteArray) {
        val response = http.post(baseUrl) {
            expectSuccess = false
            url { appendPathSegments(listOf("handoff", slot.code, "report"), encodeSlash = true) }
            header(HANDOFF_CREATOR_HEADER, slot.secret)
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }
        response.expect(HttpStatusCode.Created)
    }

    suspend fun claim(code: String, clinicToken: String, publicKey: ByteArray): String {
        val response = http.post(baseUrl) {
            expectSuccess = false
            url { appendPathSegments(listOf("handoff", code, "claim"), encodeSlash = true) }
            header(HANDOFF_CLINIC_HEADER, clinicToken)
            contentType(ContentType.Application.Json)
            setBody(EasyPaperJson.encodeToString(HandoffPublicKey.serializer(), HandoffPublicKey(Base64.encode(checkedPublicKey(publicKey)))))
        }
        response.expect(HttpStatusCode.OK)
        return response.json(HandoffClaim.serializer()).claimSecret
    }

    suspend fun awaitReport(code: String, clinicToken: String, claimSecret: String): ByteArray = poll(now() + HANDOFF_TTL_MILLIS) {
        val response = http.get(baseUrl) {
            expectSuccess = false
            url { appendPathSegments(listOf("handoff", code, "report"), encodeSlash = true) }
            header(HANDOFF_CLINIC_HEADER, clinicToken)
            header(HANDOFF_CLAIM_HEADER, claimSecret)
        }
        if (response.status == HttpStatusCode.NoContent) null else {
            response.expect(HttpStatusCode.OK)
            response.readRawBytes()
        }
    }

    private suspend fun <T : Any> poll(expiresAt: Long, read: suspend () -> T?): T {
        val remaining = expiresAt - now()
        if (remaining <= 0) throw expired()
        // The timeout covers in-flight HTTP calls too. Caller CancellationException is never caught.
        return withTimeoutOrNull(remaining) {
            while (true) {
                if (now() >= expiresAt) throw expired()
                val value = read()
                if (value != null) return@withTimeoutOrNull value
                delay(minOf(2_000L, (expiresAt - now()).coerceAtLeast(0)))
            }
            @Suppress("UNREACHABLE_CODE")
            error("Unreachable")
        } ?: throw expired()
    }

    private fun expired() = HandoffException(HandoffException.Kind.Expired, "Handoff expired")

    private fun HttpResponse.expect(status: HttpStatusCode) {
        if (this.status == status) return
        val kind = when (this.status) {
            HttpStatusCode.NotFound -> HandoffException.Kind.NotFound
            HttpStatusCode.Gone -> HandoffException.Kind.Expired
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> HandoffException.Kind.Unauthorized
            HttpStatusCode.TooManyRequests -> HandoffException.Kind.TooMany
            HttpStatusCode.Conflict -> HandoffException.Kind.Conflict
            else -> HandoffException.Kind.Network
        }
        throw HandoffException(kind, "Handoff request failed: ${this.status}")
    }

    private suspend fun <T> HttpResponse.json(serializer: KSerializer<T>): T {
        val text = bodyAsText() // Preserve IOExceptions while reading the body.
        return try {
            EasyPaperJson.decodeFromString(serializer, text)
        } catch (e: SerializationException) {
            throw HandoffException(HandoffException.Kind.Network, "Invalid handoff response", e)
        }
    }
}
