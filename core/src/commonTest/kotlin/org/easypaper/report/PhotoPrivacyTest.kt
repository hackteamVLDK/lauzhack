package org.easypaper.report

import kotlinx.coroutines.test.runTest
import org.easypaper.model.CareRequest
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.FoodWidget
import org.easypaper.model.PatientRecord
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoPrivacyTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun seg(marker: Int, payload: ByteArray) =
        bytes(0xFF, marker, (payload.size + 2) shr 8, (payload.size + 2) and 0xFF) + payload

    /** EXIF with IFD0 {Model -> "PhoneModelX", Orientation, GPS IFD -> one ASCII entry with coordinates}. */
    private fun exif(big: Boolean, orientation: Int): ByteArray {
        fun u16(v: Int) = if (big) bytes(v shr 8, v and 0xFF) else bytes(v and 0xFF, v shr 8)
        fun u32(v: Int) = if (big) u16(v ushr 16) + u16(v and 0xFFFF) else u16(v and 0xFFFF) + u16(v ushr 16)
        fun entry(tag: Int, type: Int, count: Int, value: ByteArray) = u16(tag) + u16(type) + u32(count) + value
        val model = "PhoneModelX\u0000".encodeToByteArray() // at 50
        val gpsText = "46.5197N 6.6323E\u0000".encodeToByteArray() // at 80
        val tiff = (if (big) "MM" else "II").encodeToByteArray() + u16(42) + u32(8) +
            u16(3) +
            entry(0x0110, 2, model.size, u32(50)) +
            entry(0x0112, 3, 1, u16(orientation) + u16(0)) +
            entry(0x8825, 4, 1, u32(62)) +
            u32(0) +
            model +
            u16(1) + entry(0x0002, 2, gpsText.size, u32(80)) + u32(0) +
            gpsText
        return "Exif\u0000\u0000".encodeToByteArray() + tiff
    }

    private val app0 = seg(0xE0, "JFIF\u0000".encodeToByteArray() + bytes(1, 2, 0, 0, 1, 0, 1, 0, 0))
    private val dqt = seg(0xDB, ByteArray(65) { it.toByte() })
    private val scan = bytes(0xFF, 0xDA, 0x00, 0x08, 1, 1, 0, 0, 0x3F, 0) + bytes(0x12, 0xFF, 0x00, 0x34, 0xFF, 0xD0, 0x56, 0xFF, 0xD9)

    private fun phoneJpeg(big: Boolean, orientation: Int = 6) = bytes(0xFF, 0xD8) + app0 +
        seg(0xE1, exif(big, orientation)) +
        seg(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta>secret-xmp</x:xmpmeta>".encodeToByteArray()) +
        seg(0xED, "Photoshop 3.0\u0000iptc-secret".encodeToByteArray()) +
        seg(0xFE, "comment-secret".encodeToByteArray()) +
        dqt + scan

    private fun ByteArray.has(text: String) = text in decodeToString()

    /** Orientation from the first APP1 of [jpeg], or null if there is none. */
    private fun orientationOf(jpeg: ByteArray): Int? {
        var i = 2
        while (i + 4 <= jpeg.size && jpeg[i] == 0xFF.toByte()) {
            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker == 0xDA) return null
            val length = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            if (marker == 0xE1) return exifOrientation(jpeg, i + 4, length - 2)
            i += 2 + length
        }
        return null
    }

    @Test
    fun metadataIsRemovedOrientationKept() {
        for (big in listOf(true, false)) {
            val original = phoneJpeg(big)
            assertTrue(original.has("PhoneModelX") && original.has("46.5197N"))
            assertEquals(6, orientationOf(original))
            val clean = stripJpegMetadata(original)
            for (secret in listOf("PhoneModelX", "46.5197N", "secret-xmp", "iptc-secret", "comment-secret")) {
                assertFalse(clean.has(secret), "'$secret' left (big-endian=$big)")
            }
            assertEquals(6, orientationOf(clean))
            assertContentEquals(original.copyOfRange(original.size - scan.size, original.size), clean.copyOfRange(clean.size - scan.size, clean.size))
            assertContentEquals(bytes(0xFF, 0xD8) + app0, clean.copyOfRange(0, 2 + app0.size)) // JFIF stays first
            assertTrue(clean.size < original.size)
        }
    }

    @Test
    fun uprightPhotoGetsNoExifAtAll() {
        val clean = stripJpegMetadata(phoneJpeg(big = true, orientation = 1))
        assertNull(orientationOf(clean))
        assertContentEquals(bytes(0xFF, 0xD8) + app0 + dqt + scan, clean)
    }

    @Test
    fun cleanJpegIsUnchanged() {
        val plain = bytes(0xFF, 0xD8) + app0 + dqt + scan
        assertContentEquals(plain, stripJpegMetadata(plain))
    }

    @Test
    fun notAJpegOrBrokenIsRejected() {
        assertFailsWith<IllegalArgumentException> { stripJpegMetadata("PNG...".encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { stripJpegMetadata(bytes(0xFF, 0xD8)) }
        val truncated = phoneJpeg(true).copyOfRange(0, 30)
        assertFailsWith<IllegalArgumentException> { stripJpegMetadata(truncated) }
    }

    @Test
    fun photosInTheReportArriveClean() = runTest {
        val report = Report(
            id = "rep", createdAt = 0,
            record = PatientRecord(
                CareRequest(id = "r", createdAt = 0, doctorName = "Dr", complaint = "c", startDate = "2026-11-17", days = 1, locale = "en", widgets = listOf(FoodWidget("f", "Food"))),
                listOf(Entry("e1", "f", 0, Food("soup", "p1"))),
            ),
        )
        val sealed = sealReport(report, mapOf("p1" to phoneJpeg(big = false)))
        val received = openPhoto(sealed.photos.getValue("p1"), sealed.qr, "p1")
        assertFalse(received.has("PhoneModelX") || received.has("46.5197N"))
        assertEquals(6, orientationOf(received))
    }
}
