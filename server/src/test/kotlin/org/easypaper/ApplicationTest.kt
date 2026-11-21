package org.easypaper

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.easypaper.demo.Demo
import org.easypaper.model.*
import org.easypaper.report.Report
import org.easypaper.report.newReportId
import org.easypaper.report.RelayClient
import org.easypaper.report.RelayException
import org.easypaper.report.openPhoto
import org.easypaper.report.openReport
import org.easypaper.report.sealReport
import java.nio.file.Files
import kotlin.time.Duration.Companion.milliseconds
import kotlin.test.*

class ApplicationTest {
    private val id = "rep-12345678"
    private fun bytes(n: Int) = ByteArray(n) { (it % 200).toByte() }

    private fun relayApp(relay: Relay = Relay(), test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(relay) }
        test()
    }

    @Test
    fun uploadThenReadOnceThen404() = relayApp {
        assertEquals(HttpStatusCode.Created, client.post("/reports/$id") { setBody(bytes(500)) }.status)
        val first = client.get("/reports/$id")
        assertEquals(HttpStatusCode.OK, first.status)
        assertContentEquals(bytes(500), first.readRawBytes())
        assertEquals("no-store", first.headers[HttpHeaders.CacheControl])
        assertEquals(HttpStatusCode.NotFound, client.get("/reports/$id").status)
    }

    @Test
    fun photosAreReadOnceToo() = relayApp {
        assertEquals(HttpStatusCode.Created, client.post("/reports/$id/photos/photo%2Fe1") { setBody(bytes(300)) }.status)
        assertContentEquals(bytes(300), client.get("/reports/$id/photos/photo%2Fe1").readRawBytes())
        assertEquals(HttpStatusCode.NotFound, client.get("/reports/$id/photos/photo%2Fe1").status)
    }

    @Test
    fun duplicateEmptyAndBadIdsRejected() = relayApp {
        client.post("/reports/$id") { setBody(bytes(10)) }
        assertEquals(HttpStatusCode.Conflict, client.post("/reports/$id") { setBody(bytes(10)) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/reports/rep-0000002") { setBody(ByteArray(0)) }.status)
        for (bad in listOf("short", "rep%2F..%2F..%2Fetc", "rep%20with%20spaces")) {
            assertEquals(HttpStatusCode.BadRequest, client.post("/reports/$bad") { setBody(bytes(10)) }.status, bad)
        }
        for (ref in listOf("..", "photo%2F..%2Fx", "a%2F%2Fb", "x".repeat(129))) {
            assertEquals(HttpStatusCode.BadRequest, client.post("/reports/$id/photos/$ref") { setBody(bytes(10)) }.status, ref)
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/reports/never-uploaded").status)
    }

    @Test
    fun sizeLimits() = relayApp(Relay(maxBytes = 100, maxTotalBytes = 150)) {
        assertEquals(HttpStatusCode.PayloadTooLarge, client.post("/reports/rep-big-0001") { setBody(bytes(101)) }.status)
        assertEquals(HttpStatusCode.Created, client.post("/reports/rep-ok-00001") { setBody(bytes(100)) }.status)
        // a second 100-byte object would exceed the 150-byte total
        assertEquals(HttpStatusCode.InsufficientStorage, client.post("/reports/rep-ok-00002") { setBody(bytes(100)) }.status)
        client.get("/reports/rep-ok-00001") // reading frees the space
        assertEquals(HttpStatusCode.Created, client.post("/reports/rep-ok-00002") { setBody(bytes(100)) }.status)
    }

    @Test
    fun expiresAfterTtl() {
        var clock = 1_000_000L
        val relay = Relay(ttlMillis = 72L * 3600 * 1000, now = { clock })
        relayApp(relay) {
            client.post("/reports/$id") { setBody(bytes(10)) }
            client.post("/reports/rep-87654321") { setBody(bytes(10)) }
            clock += 72L * 3600 * 1000 - 1
            assertEquals(HttpStatusCode.OK, client.get("/reports/rep-87654321").status) // a moment before expiry
            clock += 1
            assertEquals(HttpStatusCode.NotFound, client.get("/reports/$id").status)
        }
    }

    @Test
    fun sealedDemoReportTravelsThroughTheRelay() {
        val relay = Relay()
        relayApp(relay) {
            val photo = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDA.toByte(), 0, 8, 1, 1, 0, 0, 0x3F, 0, 1, 2, 3,
                0xFF.toByte(), 0xD9.toByte())
            val sealed = sealReport(Demo.report, mapOf("demo-photo-1" to photo))
            val relayClient = RelayClient("http://localhost", client)
            relayClient.upload(sealed)
            assertEquals(409, assertFailsWith<RelayException> { relayClient.upload(sealed) }.status)

            // the server holds exactly the ciphertext it was given, nothing readable
            val stored = assertNotNull(relay.peek("report/${Demo.report.id}"))
            assertContentEquals(sealed.bytes, stored)
            assertFalse("Headache" in stored.decodeToString() || "Dr. Demo" in stored.decodeToString())

            assertEquals(Demo.report, openReport(relayClient.downloadReport(sealed.qr), sealed.qr))
            assertContentEquals(photo, openPhoto(relayClient.downloadPhoto(sealed.qr, "demo-photo-1"), sealed.qr, "demo-photo-1"))
            assertEquals(404, assertFailsWith<RelayException> { relayClient.downloadReport(sealed.qr) }.status)
        }
    }

    @Test
    fun photoRefWithSlashTravelsThroughTheClient() {
        val relay = Relay()
        relayApp(relay) {
            val report = Report(
                id = newReportId(), createdAt = 0,
                record = PatientRecord(
                    CareRequest(id = "req", createdAt = 0, doctorName = "Dr", complaint = "c", startDate = "2026-11-21", days = 1,
                        locale = "en", widgets = listOf(FoodWidget("f", "Food"))),
                    listOf(Entry("e1", "f", 0, Food("soup", "photo/e1"))), // photoRef format of PhotoStore (#27)
                ),
            )
            val photo = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDA.toByte(), 0, 8, 1, 1, 0, 0, 0x3F, 0, 9,
                0xFF.toByte(), 0xD9.toByte())
            val sealed = sealReport(report, mapOf("photo/e1" to photo))
            val relayClient = RelayClient("http://localhost", client)
            relayClient.upload(sealed)
            assertNotNull(relay.peek("photo/${report.id}/photo/e1")) // one object, not an extra path segment
            assertContentEquals(photo, openPhoto(relayClient.downloadPhoto(sealed.qr, "photo/e1"), sealed.qr, "photo/e1"))
        }
    }

    @Test
    fun expiredObjectsArePurgedWithoutAnyRequest() {
        var clock = 0L
        val relay = Relay(ttlMillis = 1000, now = { clock })
        testApplication {
            application { module(relay, purgeEvery = 50.milliseconds) }
            startApplication()
            assertEquals(Relay.PutResult.CREATED, relay.put("report/rep-purge-01", bytes(10)))
            clock = 1000
            var waited = 0
            while (relay.peek("report/rep-purge-01") != null && waited < 3000) { Thread.sleep(50); waited += 50 }
            assertNull(relay.peek("report/rep-purge-01"), "not purged after ${waited} ms")
        }
    }

    @Test
    fun servesTheDoctorsWebApp() {
        val dir = Files.createTempDirectory("web").toFile().apply { resolve("index.html").writeText("<title>Easy Paper</title>") }
        try {
            testApplication {
                application { module(Relay(), dir) }
                assertTrue("Easy Paper" in client.get("/").bodyAsText())
            }
            testApplication {
                application { module(Relay()) }
                assertEquals(HttpStatusCode.NotFound, client.get("/").status)
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
