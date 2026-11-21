package org.easypaper.report

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** shrinkPhoto with the real Android encoder on a 12 MP camera-like photo with EXIF orientation and GPS. */
class AndroidPhotoDeviceTest {
    /** APP1 "Exif": Orientation 6, Model "PhoneModelX", GPS IFD pointer — like a phone writes. */
    private val phoneExif = byteArrayOf(
        0xFF.toByte(), 0xE1.toByte(), 0x00, 0x3A, 0x45, 0x78, 0x69, 0x66, 0, 0, 0x4D, 0x4D, 0, 0x2A, 0, 0, 0, 8,
        0, 3, 0x01, 0x10, 0, 2, 0, 0, 0, 12, 0, 0, 0, 0x2C, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0,
        0x88.toByte(), 0x25, 0, 4, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0,
    ) + "PhoneModelX\u0000".encodeToByteArray()

    private fun cameraJpeg(w: Int, h: Int): ByteArray {
        val r = Random(7)
        val pixels = IntArray(w * h) { i ->
            val x = i % w; val y = i / w; val n = r.nextInt(24)
            (0xFF shl 24) or ((x * 255 / w + n).coerceAtMost(255) shl 16) or ((y * 255 / h + n).coerceAtMost(255) shl 8) or (128 + n)
        }
        val bmp = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        val jpeg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
        bmp.recycle()
        return jpeg.copyOfRange(0, 2) + fixLength(phoneExif) + jpeg.copyOfRange(2, jpeg.size)
    }

    private fun fixLength(app1: ByteArray) = app1.copyOf().also { it[2] = ((app1.size - 2) shr 8).toByte(); it[3] = (app1.size - 2).toByte() }

    @Test
    fun cameraPhotoBecomesSmallCleanAndKeepsOrientation() {
        val original = cameraJpeg(4000, 3000)
        assertEquals(6, jpegOrientation(original))
        assertTrue("PhoneModelX" in original.decodeToString() && original.size > PHOTO_MAX_BYTES)
        val out = shrinkPhoto(original)
        assertTrue(out.size <= PHOTO_MAX_BYTES, "${out.size} bytes")
        val img = assertNotNull(BitmapFactory.decodeByteArray(out, 0, out.size))
        assertEquals(1024 to 768, img.width to img.height)
        assertEquals(6, jpegOrientation(out))
        assertTrue("PhoneModelX" !in out.decodeToString(), "phone metadata left")
        assertEquals(out.toList(), stripJpegMetadata(out).toList(), "nothing left to strip")
    }

    @Test
    fun notAnImageIsRejected() {
        assertFailsWith<IllegalArgumentException> { shrinkPhoto("hello".encodeToByteArray()) }
    }
}
