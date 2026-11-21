package org.easypaper.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.easypaper.model.EasyPaperJson
import org.easypaper.model.PatientRecord
import kotlin.io.encoding.Base64

/** What the server's AI is and what it has spent; never contains the key. Shown on the price screen (#32). */
@Serializable
data class AiStatus(
    /** "cloud", "clinic" or "off". */
    val mode: String,
    val provider: String? = null,
    val smallModel: String? = null,
    val bigModel: String? = null,
    val prices: Map<String, ModelPrice> = emptyMap(),
    val keyConfigured: Boolean = false,
    val spentUsd: Double = 0.0,
    val budgetUsd: Double = 0.0,
    val calls: Int = 0,
    val cacheHits: Int = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
)

@Serializable
data class SummaryRequest(
    val record: PatientRecord,
    /** photoRef -> JPEG as base64, only photos the report references. */
    val photos: Map<String, String> = emptyMap(),
    val utcOffsetMinutes: Int = 0,
)

@Serializable
data class DocumentRequest(
    val documentId: String,
    val text: String? = null,
    val imageBase64: String? = null,
    val imageFormat: String = "jpeg",
)

class AiException(val status: Int, message: String) : Exception(message)

/**
 * The doctor's apps talk to the AI only through the Easy Paper server (#8): the OpenRouter key stays there,
 * the clinic module keeps documents inside the clinic. The app passes an [HttpClient] with its platform engine.
 */
class AiClient(private val baseUrl: String, private val http: HttpClient) {
    suspend fun status(): AiStatus =
        EasyPaperJson.decodeFromString(AiStatus.serializer(), checked(http.get(baseUrl) { url { appendPathSegments("ai", "status") } }))

    suspend fun summarize(record: PatientRecord, photos: Map<String, ByteArray> = emptyMap(), utcOffsetMinutes: Int = 0): SummaryResult {
        val body = SummaryRequest(record, photos.mapValues { Base64.Default.encode(it.value) }, utcOffsetMinutes)
        return EasyPaperJson.decodeFromString(SummaryResult.serializer(), post("summary", EasyPaperJson.encodeToString(SummaryRequest.serializer(), body)))
    }

    /** Clinic module only; the cloud server answers 403 because documents must not leave the clinic. */
    suspend fun extractDocument(documentId: String, text: String? = null, image: ByteArray? = null, imageFormat: String = "jpeg"): DocumentExtraction {
        val body = DocumentRequest(documentId, text, image?.let { Base64.Default.encode(it) }, imageFormat)
        return EasyPaperJson.decodeFromString(DocumentExtraction.serializer(), post("documents", EasyPaperJson.encodeToString(DocumentRequest.serializer(), body)))
    }

    private suspend fun post(path: String, json: String): String = checked(
        http.post(baseUrl) {
            url { appendPathSegments("ai", path) }
            contentType(ContentType.Application.Json)
            setBody(json)
        },
    )

    private suspend fun checked(response: HttpResponse): String {
        val text = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) throw AiException(response.status.value, text.ifBlank { response.status.description })
        return text
    }
}
