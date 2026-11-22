package org.easypaper.report

import kotlinx.coroutines.test.runTest
import org.easypaper.demo.Demo
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class HandoffTest {
    private val photo = tinyJpeg("photo content".encodeToByteArray())

    @Test
    fun reportAndPhotosRoundTripWithExportedKeys() = runTest {
        val doctor = HandoffKeys.generate()
        assertEquals(65, doctor.publicKey.size)
        assertEquals(4.toByte(), doctor.publicKey[0])
        val packet = sealForDoctor(Demo.report, mapOf("demo-photo-1" to photo), doctor.publicKey)
        val restoredKeys = HandoffKeys(doctor.publicKey.copyOf(), doctor.privateKey.copyOf())
        val (report, photos) = openFromPatient(packet, restoredKeys)
        assertEquals(Demo.report, report)
        assertEquals(setOf("demo-photo-1"), photos.keys)
        assertContentEquals(photo, photos.getValue("demo-photo-1"))
        assertFalse("Headache" in packet.decodeToString())
        assertFalse("photo content" in packet.decodeToString())
    }

    @Test
    fun foreignKeyCannotOpen() = runTest {
        val doctor = HandoffKeys.generate()
        val foreign = HandoffKeys.generate()
        val packet = sealForDoctor(Demo.report, emptyMap(), doctor.publicKey)
        assertFails { openFromPatient(packet, foreign) }
        assertEquals(Demo.report, openFromPatient(packet, doctor).first)
    }

    @Test
    fun ephemeralKeyIsFreshAndPacketIsAuthenticated() = runTest {
        val doctor = HandoffKeys.generate()
        val first = sealForDoctor(Demo.report, emptyMap(), doctor.publicKey)
        val second = sealForDoctor(Demo.report, emptyMap(), doctor.publicKey)
        assertFalse(first.copyOfRange(1, 66).contentEquals(second.copyOfRange(1, 66)))
        assertFails { openFromPatient(second.copyOf().also { first.copyInto(it, 1, 1, 66) }, doctor) }
        assertFails { openFromPatient(first.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }, doctor) }
        assertFails { openFromPatient(first.copyOf().also { it[0] = 2 }, doctor) }
        assertFails { openFromPatient(first.copyOf(65), doctor) }
    }

    @Test
    fun samePhotoPrivacyRulesAsQrTransfer() = runTest {
        val doctor = HandoffKeys.generate()
        assertFails { sealForDoctor(Demo.report, mapOf("unknown" to photo), doctor.publicKey) }
        assertFails { sealForDoctor(Demo.report, mapOf("demo-photo-1" to byteArrayOf(1, 2, 3)), doctor.publicKey) }
        assertFails { sealForDoctor(Demo.report, emptyMap(), doctor.publicKey.copyOf(64)) }
    }
}
