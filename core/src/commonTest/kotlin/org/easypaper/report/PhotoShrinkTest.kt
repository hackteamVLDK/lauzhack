package org.easypaper.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoShrinkTest {
    /** APP1 "Exif" with big-endian IFD0: Orientation [o] and a GPS IFD pointer (0x8825). */
    private fun exifWithGps(o: Int) = byteArrayOf(
        0xFF.toByte(), 0xE1.toByte(), 0x00, 0x2E, 0x45, 0x78, 0x69, 0x66, 0, 0, 0x4D, 0x4D, 0, 0x2A, 0, 0, 0, 8,
        0, 2, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, o.toByte(), 0, 0, 0x88.toByte(), 0x25, 0, 4, 0, 0, 0, 1, 0, 0, 0, 0x26, 0, 0, 0, 0,
    )

    private fun withExif(jpeg: ByteArray, o: Int) = jpeg.copyOfRange(0, 2) + exifWithGps(o) + jpeg.copyOfRange(2, jpeg.size)

    @Test
    fun stepsDownUntilItFitsAndKeepsOnlyTheOrientation() {
        val tried = mutableListOf<Pair<Int, Int>>()
        val original = withExif(tinyJpeg(ByteArray(10) { 1 }), 6)
        val out = shrinkPhoto(original) { side, q ->
            tried += side to q
            tinyJpeg(ByteArray(if (side == 768 && q == 65) 1_000 else PHOTO_MAX_BYTES + 1) { 2 })
        }
        assertEquals(listOf(1024 to 80, 1024 to 65, 1024 to 50, 768 to 80, 768 to 65), tried)
        assertTrue(out.size <= PHOTO_MAX_BYTES)
        assertEquals(6, jpegOrientation(out))
        assertEquals(stripJpegMetadata(withExif(tinyJpeg(ByteArray(1_000) { 2 }), 6)).toList(), out.toList(), "only Orientation, no GPS")
    }

    @Test
    fun uprightOrNonJpegOriginalGetsNoExif() {
        val small = tinyJpeg(ByteArray(10) { 3 })
        assertEquals(small.toList(), shrinkPhoto(withExif(small, 1)) { _, _ -> small }.toList())
        assertEquals(small.toList(), shrinkPhoto(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) { _, _ -> small }.toList()) // PNG
        assertNull(jpegOrientation(small))
        assertNull(jpegOrientation(ByteArray(3)))
    }

    @Test
    fun tooBigEvenAtTheSmallestSizeFails() {
        assertFailsWith<IllegalArgumentException> { shrinkPhoto(tinyJpeg(ByteArray(1))) { _, _ -> tinyJpeg(ByteArray(PHOTO_MAX_BYTES) { 4 }) } }
    }

    @Test
    fun rotationFollowsTheCameraOrientation() {
        val plain = tinyJpeg(ByteArray(4) { 5 })
        assertEquals(mapOf(1 to 0, 3 to 180, 6 to 90, 8 to 270, 5 to 0), listOf(1, 3, 6, 8, 5).associateWith { jpegRotationDegrees(withExif(plain, it)) })
        assertEquals(0, jpegRotationDegrees(plain))
        assertEquals(0, jpegRotationDegrees("not a jpeg".encodeToByteArray()))
    }
}
