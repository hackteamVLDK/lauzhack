package org.easypaper.catalog

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
import org.easypaper.model.EasyPaperJson
import org.easypaper.report.stripJpegMetadata

/** The existing registry resolution is also returned after server-side label reading. */
typealias CatalogResult = Resolution

/** Looks a package up in the official registries; implemented over HTTP by [HttpCatalog], in tests directly. */
fun interface Catalog {
    suspend fun resolve(query: CatalogQuery): Resolution

    suspend fun readLabel(jpeg: ByteArray): CatalogResult = throw CatalogException(503, "Reading online is not available right now")
}

class CatalogException(val status: Int, message: String) : Exception(message)

/**
 * The patient app asks the Easy Paper server, which holds the imported registries (task 71). Only what was read
 * on the package is sent for resolve. Reading online sends a package photo after the patient presses its button.
 * Patient queries search every registry.
 */
class HttpCatalog(private val baseUrl: String, private val http: HttpClient) : Catalog {
    override suspend fun readLabel(jpeg: ByteArray): CatalogResult {
        require(jpeg.size <= 1_500_000) { "Photo too large" }
        val photo = stripJpegMetadata(jpeg)
        val response = http.post(baseUrl) {
            url { appendPathSegments("catalog", "read-label") }
            contentType(ContentType.Image.JPEG)
            setBody(photo)
        }
        return EasyPaperJson.decodeFromString(Resolution.serializer(), checked(response))
    }

    override suspend fun resolve(query: CatalogQuery): Resolution {
        val response = http.post(baseUrl) {
            url { appendPathSegments("catalog", "resolve") }
            contentType(ContentType.Application.Json)
            setBody(EasyPaperJson.encodeToString(CatalogQuery.serializer(), query))
        }
        return EasyPaperJson.decodeFromString(Resolution.serializer(), checked(response))
    }

    suspend fun coverage(): Coverage =
        EasyPaperJson.decodeFromString(Coverage.serializer(), checked(http.get(baseUrl) { url { appendPathSegments("catalog", "coverage") } }))

    private suspend fun checked(response: HttpResponse): String {
        val text = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) throw CatalogException(response.status.value, text.ifBlank { response.status.description })
        return text
    }
}
