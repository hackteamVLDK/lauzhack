package org.easypaper.report

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** shrinkPhoto with the real ImageIO encoder on a 12 MP photo-like image carrying phone metadata and GPS. */
class DesktopPhotoTest {
    private fun encode(img: BufferedImage, format: String) = ByteArrayOutputStream().also { ImageIO.write(img, format, it) }.toByteArray()

    /** Gradient plus sensor-like noise: compresses like a photo, not like a flat test card. */
    private fun bigPhoto(w: Int, h: Int, type: Int = BufferedImage.TYPE_INT_RGB): BufferedImage {
        val r = Random(7)
        return BufferedImage(w, h, type).apply {
            for (y in 0 until h) for (x in 0 until w) {
                val n = r.nextInt(24)
                setRGB(x, y, (0xFF shl 24) or ((x * 255 / w + n).coerceAtMost(255) shl 16) or ((y * 255 / h + n).coerceAtMost(255) shl 8) or (128 + n))
            }
        }
    }

    /** The real phone EXIF (Orientation 6, Model, GPS) of the RealJpegTest fixture, spliced into [jpeg]. */
    private fun withPhoneExif(jpeg: ByteArray): ByteArray {
        val fixture = assertNotNull(javaClass.getResourceAsStream("/photos/baseline.jpg")).readBytes()
        var i = 2
        while ((fixture[i + 1].toInt() and 0xFF) != 0xE1) i += 2 + (((fixture[i + 2].toInt() and 0xFF) shl 8) or (fixture[i + 3].toInt() and 0xFF))
        val app1 = fixture.copyOfRange(i, i + 2 + (((fixture[i + 2].toInt() and 0xFF) shl 8) or (fixture[i + 3].toInt() and 0xFF)))
        return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
    }

    @Test
    fun twelveMegapixelPhotoBecomesSmallCleanAndKeepsOrientation() {
        val original = withPhoneExif(encode(bigPhoto(4000, 3000), "jpeg"))
        assertTrue("PhoneModelX" in original.decodeToString() && original.size > PHOTO_MAX_BYTES)
        val out = shrinkPhoto(original)
        assertTrue(out.size <= PHOTO_MAX_BYTES, "${out.size} bytes")
        val img = assertNotNull(ImageIO.read(ByteArrayInputStream(out)))
        assertEquals(1024 to 768, img.width to img.height)
        assertEquals(6, jpegOrientation(out))
        assertFalse("PhoneModelX" in out.decodeToString(), "phone metadata left")
        assertEquals(out.toList(), stripJpegMetadata(out).toList(), "nothing left to strip")
    }

    @Test
    fun smallPngWithTransparencyIsNotUpscaled() {
        val png = encode(bigPhoto(300, 500, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, 0) }, "png")
        val img = assertNotNull(ImageIO.read(ByteArrayInputStream(shrinkPhoto(png))))
        assertEquals(300 to 500, img.width to img.height)
        val corner = img.getRGB(0, 0)
        assertTrue(listOf(16, 8, 0).all { (corner shr it and 0xFF) > 0xE0 }, "transparent pixel turns white, got ${corner.toString(16)}")
    }

    @Test
    fun notAnImageIsRejected() {
        assertFailsWith<IllegalArgumentException> { shrinkPhoto("hello".encodeToByteArray()) }
    }
}
