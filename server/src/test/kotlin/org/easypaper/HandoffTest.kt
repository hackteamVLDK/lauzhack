package org.easypaper

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.easypaper.demo.Demo
import org.easypaper.model.EasyPaperJson
import org.easypaper.report.HANDOFF_CLAIM_HEADER
import org.easypaper.report.HANDOFF_CLINIC_HEADER
import org.easypaper.report.HANDOFF_CREATOR_HEADER
import org.easypaper.report.HANDOFF_TTL_MILLIS
import org.easypaper.report.HandoffClaim
import org.easypaper.report.HandoffClient
import org.easypaper.report.HandoffException
import org.easypaper.report.HandoffKeys
import org.easypaper.report.HandoffPublicKey
import org.easypaper.report.HandoffSlot
import org.easypaper.report.openFromPatient
import org.easypaper.report.sealForDoctor
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HandoffTest {
    private val token = "clinic-test-token"

    private fun handoffApp(
        handoff: Handoff = Handoff(),
        clinicToken: String? = token,
        test: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application { module(aiToken = clinicToken, handoff = handoff) }
        test()
    }

    private suspend fun HttpClient.createSlot(): HandoffSlot {
        val response = post("/handoff")
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        return EasyPaperJson.decodeFromString(HandoffSlot.serializer(), response.bodyAsText())
    }

    private suspend fun HttpClient.claimCode(code: String, key: ByteArray, suppliedToken: String? = token) = post("/handoff/$code/claim") {
        suppliedToken?.let { header(HANDOFF_CLINIC_HEADER, it) }
        contentType(ContentType.Application.Json)
        setBody(EasyPaperJson.encodeToString(HandoffPublicKey.serializer(), HandoffPublicKey(Base64.getEncoder().encodeToString(key))))
    }

    @Test
    fun fullFlowPendingStatesAndOneTimeDownload() = handoffApp {
        val slot = client.createSlot()
        assertTrue(Regex("[0-9]{6}").matches(slot.code))
        assertTrue(Regex("[A-Za-z0-9_-]{43}").matches(slot.secret))
        assertEquals(HttpStatusCode.NoContent, client.get("/handoff/${slot.code}/key") { header(HANDOFF_CREATOR_HEADER, slot.secret) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/handoff/${slot.code}/key") { header(HANDOFF_CREATOR_HEADER, "wrong") }.status)

        val keys = HandoffKeys.generate()
        val claimed = client.claimCode(slot.code, keys.publicKey)
        assertEquals(HttpStatusCode.OK, claimed.status)
        val claim = EasyPaperJson.decodeFromString(HandoffClaim.serializer(), claimed.bodyAsText())
        assertNotEquals(slot.secret, claim.claimSecret)
        assertEquals(HttpStatusCode.NoContent, client.get("/handoff/${slot.code}/report") {
            header(HANDOFF_CLINIC_HEADER, token)
            header(HANDOFF_CLAIM_HEADER, claim.claimSecret)
        }.status)
        val api = HandoffClient("http://localhost", client)
        assertContentEquals(keys.publicKey, api.awaitDoctorKey(slot))
        val packet = sealForDoctor(Demo.report, emptyMap(), keys.publicKey)
        assertEquals(HttpStatusCode.NotFound, client.post("/handoff/${slot.code}/report") {
            header(HANDOFF_CREATOR_HEADER, "wrong")
            setBody(packet)
        }.status)
        api.upload(slot, packet)
        assertEquals(HandoffException.Kind.Conflict, assertFailsWith<HandoffException> { api.upload(slot, packet) }.kind)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/handoff/${slot.code}/report") { header(HANDOFF_CLAIM_HEADER, claim.claimSecret) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/handoff/${slot.code}/report") {
            header(HANDOFF_CLINIC_HEADER, token)
            header(HANDOFF_CLAIM_HEADER, "wrong")
        }.status)
        val downloaded = api.awaitReport(slot.code, token, claim.claimSecret)
        assertContentEquals(packet, downloaded)
        assertEquals(Demo.report, openFromPatient(downloaded, keys).first)
        assertEquals(HttpStatusCode.NotFound, client.get("/handoff/${slot.code}/report") {
            header(HANDOFF_CLINIC_HEADER, token)
            header(HANDOFF_CLAIM_HEADER, claim.claimSecret)
        }.status)
        assertEquals(HandoffException.Kind.NotFound, assertFailsWith<HandoffException> { api.awaitDoctorKey(slot) }.kind)
    }

    @Test
    fun claimNeedsConfiguredAndMatchingClinicToken() {
        for (configured in listOf(token, null, "")) handoffApp(clinicToken = configured) {
            val slot = client.createSlot()
            val keys = HandoffKeys.generate()
            assertEquals(HttpStatusCode.Unauthorized, client.claimCode(slot.code, keys.publicKey, null).status)
            assertEquals(HttpStatusCode.Unauthorized, client.claimCode(slot.code, keys.publicKey, "wrong").status)
            if (configured.isNullOrEmpty()) assertEquals(HttpStatusCode.Unauthorized, client.claimCode(slot.code, keys.publicKey).status)
        }
    }

    @Test
    fun repeatClaimIsConflictAndKeepsOriginalKey() = handoffApp {
        val slot = client.createSlot()
        val keys = HandoffKeys.generate()
        val api = HandoffClient("http://localhost", client)
        api.claim(slot.code, token, keys.publicKey)
        assertEquals(HandoffException.Kind.Conflict, assertFailsWith<HandoffException> {
            api.claim(slot.code, token, HandoffKeys.generate().publicKey)
        }.kind)
        assertContentEquals(keys.publicKey, api.awaitDoctorKey(slot))
    }

    @Test
    fun slotsExpireAtCreationDeadlineEvenAfterClaimAndUpload() {
        var clock = 10_000L
        handoffApp(Handoff(now = { clock })) {
            val slot = client.createSlot()
            val unclaimed = client.createSlot()
            assertEquals(clock + HANDOFF_TTL_MILLIS, slot.expiresAt)
            val keys = HandoffKeys.generate()
            val api = HandoffClient("http://localhost", client)
            val claim = api.claim(slot.code, token, keys.publicKey)
            clock = slot.expiresAt - 1
            api.upload(slot, byteArrayOf(1, 2, 3))
            assertEquals(HttpStatusCode.OK, client.get("/handoff/${slot.code}/key") { header(HANDOFF_CREATOR_HEADER, slot.secret) }.status)
            clock++
            assertEquals(HttpStatusCode.NotFound, client.claimCode(unclaimed.code, keys.publicKey).status)
            assertEquals(HttpStatusCode.NotFound, client.get("/handoff/${slot.code}/key") { header(HANDOFF_CREATOR_HEADER, slot.secret) }.status)
            assertEquals(HttpStatusCode.NotFound, client.post("/handoff/${slot.code}/report") {
                header(HANDOFF_CREATOR_HEADER, slot.secret)
                setBody(byteArrayOf(1))
            }.status)
            assertEquals(HttpStatusCode.NotFound, client.get("/handoff/${slot.code}/report") {
                header(HANDOFF_CLINIC_HEADER, token)
                header(HANDOFF_CLAIM_HEADER, claim)
            }.status)
            val expiredApi = HandoffClient("http://localhost", client, now = { clock })
            assertEquals(HandoffException.Kind.Expired, assertFailsWith<HandoffException> { expiredApi.awaitDoctorKey(slot) }.kind)
        }
    }

    @Test
    fun sixthIncorrectCodeIsThrottledAndWindowResets() {
        var clock = 0L
        handoffApp(Handoff(now = { clock })) {
            val key = HandoffKeys.generate().publicKey
            repeat(5) { assertEquals(HttpStatusCode.NotFound, client.claimCode("000000", key).status) }
            assertEquals(HttpStatusCode.TooManyRequests, client.claimCode("000001", key).status)
            clock = 59_999
            assertEquals(HttpStatusCode.TooManyRequests, client.claimCode("000002", key).status)
            clock++
            assertEquals(HttpStatusCode.NotFound, client.claimCode("000003", key).status)
        }
    }

    @Test
    fun uploadSizeBoundedAndRejectionDoesNotConsumeSlot() = handoffApp(Handoff(maxBytes = 10)) {
        val api = HandoffClient("http://localhost", client)
        val slot = api.create()
        val claim = api.claim(slot.code, token, HandoffKeys.generate().publicKey)
        assertEquals(HttpStatusCode.PayloadTooLarge, client.post("/handoff/${slot.code}/report") {
            header(HANDOFF_CREATOR_HEADER, slot.secret)
            setBody(ByteArray(11))
        }.status)
        api.upload(slot, ByteArray(10) { 7 })
        val response = client.get("/handoff/${slot.code}/report") {
            header(HANDOFF_CLINIC_HEADER, token)
            header(HANDOFF_CLAIM_HEADER, claim)
        }
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertContentEquals(ByteArray(10) { 7 }, response.readRawBytes())
    }

    @Test
    fun onlyLoopbackProxyCanSupplyFirstForwardedAddress() {
        assertEquals("203.0.113.2", handoffClientIp("127.0.0.1", "203.0.113.1, 203.0.113.2")) // the last address is the one Caddy wrote
        assertEquals("203.0.113.2", handoffClientIp("::1", " 203.0.113.1 , 203.0.113.2"))
        assertEquals("198.51.100.1", handoffClientIp("198.51.100.1", "203.0.113.1"))
        assertEquals("::1", handoffClientIp("::1", null))
    }

    @Test
    fun liveCodesAreUniqueAndPurgeRemovesSlotsAndFailureWindows() {
        var clock = 0L
        val handoff = Handoff(now = { clock }, maxSlots = 500)
        val codes = List(500) { handoff.create("ip-$it")!!.code }
        assertEquals(500, codes.toSet().size)
        assertEquals(null, handoff.create("ip-new"))
        repeat(5) { handoff.claim("unknown", "public", "ip-a") }
        assertEquals(HttpStatusCode.TooManyRequests, handoff.claim("unknown", "public", "ip-a").status)
        assertEquals(HttpStatusCode.NotFound, handoff.claim("unknown", "public", "ip-b").status)
        clock = HANDOFF_TTL_MILLIS
        handoff.purge()
        assertEquals(HttpStatusCode.NotFound, handoff.claim(codes.first(), "public", "ip-a").status)
        assertEquals(clock + HANDOFF_TTL_MILLIS, handoff.create()!!.expiresAt)
    }

    @Test
    fun oneAddressOpensAtMostTwentyWindowsAMinute() {
        var clock = 0L
        val handoff = Handoff(now = { clock })
        repeat(20) { assertNotNull(handoff.create("ip-a")) }
        assertNull(handoff.create("ip-a"), "slot flooding from one address is refused")
        assertNotNull(handoff.create("ip-b"))
        clock = 60_000
        assertNotNull(handoff.create("ip-a"), "the window slides")
    }
}
