package org.easypaper.report

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType

class RelayException(val status: Int, message: String) : Exception(message)

/**
 * Talks to the blind relay on the server (#29). Only sealed bytes travel; the key stays in the QR the patient shows.
 * The app passes an [HttpClient] with its platform engine. Every object can be downloaded once.
 */
class RelayClient(private val baseUrl: String, private val http: HttpClient) {
    /** Uploads the sealed report and all its photos. */
    suspend fun upload(sealed: SealedReport) {
        val id = reportIdFromQr(sealed.qr)
        post(listOf("reports", id), sealed.bytes)
        for ((ref, bytes) in sealed.photos) post(listOf("reports", id, "photos", ref), bytes)
    }

    /** Encrypted report bytes for the scanned QR; open them with [openReport]. */
    suspend fun downloadReport(qr: String): ByteArray = get(listOf("reports", reportIdFromQr(qr)))

    /** Encrypted photo bytes; open them with [openPhoto]. */
    suspend fun downloadPhoto(qr: String, ref: String): ByteArray = get(listOf("reports", reportIdFromQr(qr), "photos", ref))

    private suspend fun post(path: List<String>, bytes: ByteArray) {
        val response = http.post(baseUrl) {
            url { appendPathSegments(path) } // a "/" inside a photo ref is encoded, it stays one segment
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }
        if (response.status != HttpStatusCode.Created) throw failure(response, "upload")
    }

    private suspend fun get(path: List<String>): ByteArray {
        val response = http.get(baseUrl) { url { appendPathSegments(path) } }
        if (response.status != HttpStatusCode.OK) throw failure(response, "download")
        return response.readRawBytes()
    }

    private fun failure(response: HttpResponse, what: String) = RelayException(
        response.status.value,
        when (response.status) {
            HttpStatusCode.NotFound -> "Report not found: already opened, expired or never uploaded"
            HttpStatusCode.Conflict -> "This report was already uploaded"
            HttpStatusCode.PayloadTooLarge -> "Report or photo is too large for the relay"
            else -> "Relay $what failed: ${response.status}"
        },
    )
}
