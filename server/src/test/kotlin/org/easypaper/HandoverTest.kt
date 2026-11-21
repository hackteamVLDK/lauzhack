package org.easypaper

import io.ktor.server.testing.testApplication
import org.easypaper.demo.Demo
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.report.RelayClient
import org.easypaper.report.RelayException
import org.easypaper.report.handover
import org.easypaper.report.receive
import org.easypaper.storage.BlobStore
import org.easypaper.storage.PatientRepository
import org.easypaper.storage.PhotoStore
import org.easypaper.storage.RecordStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Task 30 on the real relay: the patient's phone -> relay -> the doctor's app, once, and only with the QR. */
class HandoverTest {
    private class Blobs : BlobStore {
        private val map = HashMap<String, ByteArray>()
        override suspend fun put(key: String, bytes: ByteArray) { map[key] = bytes }
        override suspend fun get(key: String) = map[key]
        override suspend fun keys() = map.keys.toList()
        override suspend fun delete(key: String) { map.remove(key) }
    }

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDA.toByte(), 0, 8, 1, 1, 0, 0, 0x3F, 0, 9, 8, 7,
        0xFF.toByte(), 0xD9.toByte())

    @Test
    fun demoRecordWithAPhotoGoesFromPhoneToDoctorOnce() = testApplication {
        application { module(Relay()) }
        val relay = RelayClient("http://localhost", client)
        val phone = Blobs().let { b -> Random.nextBytes(32).let { key -> PatientRepository(RecordStore(b, key), PhotoStore(b, key)) } }
        phone.start(Demo.request)
        for (entry in Demo.record.entries) phone.add(Demo.request.id, entry.copy(payload = (entry.payload as? Food)?.copy(photoRef = null) ?: entry.payload))
        val food = Demo.request.widgets.first { it is org.easypaper.model.FoodWidget }.id
        assertEquals(emptyList(), phone.add(Demo.request.id, Entry("e99", food, Demo.record.entries.maxOf { it.at }, Food("soup")), photo = jpeg))
        val record = phone.records.value.single()

        val qr = phone.handover(record, relay, now = 1_000)
        assertTrue(qr.startsWith("ep1:"))

        // a different key (someone who saw only the report id) cannot open it
        val forged = qr.substringBeforeLast(':') + ":" + "A".repeat(43)
        assertFails { relay.receive(forged) }

        // the forged attempt burned nothing it could read, but the relay hands each object out once: upload again
        val qr2 = phone.handover(record, relay, now = 2_000)
        val received = relay.receive(qr2)
        assertEquals(record, received.report.record)
        assertEquals(2_000, received.report.createdAt)
        assertContentEquals(jpeg, received.photos.getValue("photo/e99"))

        assertEquals(404, assertFailsWith<RelayException> { relay.receive(qr2) }.status, "second time the link does not work")
        assertFailsWith<IllegalArgumentException> { relay.receive("https://example.com/not-ours") }
    }
}
