package org.easypaper.report

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** stripJpegMetadata on files from a real encoder (resources/photos/make_test_jpegs.py), decoded by javax.imageio. */
class RealJpegTest {
    private fun fixture(name: String) = assertNotNull(javaClass.getResourceAsStream("/photos/$name.jpg"), name).readBytes()
    private fun decode(bytes: ByteArray): BufferedImage = assertNotNull(ImageIO.read(ByteArrayInputStream(bytes)))
    private fun pixels(img: BufferedImage) = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)

    private fun orientation(jpeg: ByteArray): Int? {
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

    /** IFD0 tags of every EXIF APP1 before the image data (GPS coordinates are binary rationals, not text). */
    private fun exifIfd0Tags(jpeg: ByteArray): List<Set<Int>> {
        fun u8(i: Int) = jpeg[i].toInt() and 0xFF
        val result = mutableListOf<Set<Int>>()
        var i = 2
        while (i + 4 <= jpeg.size && u8(i) == 0xFF && u8(i + 1) != 0xDA) {
            val length = (u8(i + 2) shl 8) or u8(i + 3)
            if (u8(i + 1) == 0xE1 && String(jpeg, i + 4, 6, Charsets.ISO_8859_1) == "Exif\u0000\u0000") {
                val tiff = i + 10
                val big = u8(tiff) == 0x4D
                fun u16(at: Int) = if (big) (u8(at) shl 8) or u8(at + 1) else (u8(at + 1) shl 8) or u8(at)
                fun u32(at: Int) = if (big) (u16(at) shl 16) or u16(at + 2) else (u16(at + 2) shl 16) or u16(at)
                val ifd = tiff + u32(tiff + 4)
                result += (0 until u16(ifd)).map { u16(ifd + 2 + it * 12) }.toSet()
            }
            i += 2 + length
        }
        return result
    }

    @Test
    fun realFilesLoseMetadataButNotPixels() {
        for (name in listOf("baseline", "progressive", "trailer")) {
            val original = fixture(name)
            assertTrue("PhoneModelX" in original.decodeToString(), "fixture $name has no metadata")
            val originalTags = exifIfd0Tags(original).first()
            assertTrue(0x8825 in originalTags && 0x0110 in originalTags, "fixture $name: no GPS IFD / Model to remove")
            val clean = stripJpegMetadata(original)
            assertEquals(listOf(setOf(0x0112)), exifIfd0Tags(clean), "$name: EXIF must hold only Orientation, no GPS IFD")
            val text = clean.decodeToString()
            for (secret in listOf("PhoneModelX", "PhoneMakerX", "2026:11:21", "between-secret", "ftypmp4")) {
                assertFalse(secret in text, "$name: '$secret' left")
            }
            assertEquals(6, orientation(clean), name)
            assertContentEquals(byteArrayOf(0xFF.toByte(), 0xD9.toByte()), clean.copyOfRange(clean.size - 2, clean.size), name)
            val before = decode(original)
            val after = decode(clean)
            assertEquals(before.width to before.height, after.width to after.height, name)
            assertContentEquals(pixels(before), pixels(after), "$name: pixels changed")
        }
    }

    @Test
    fun trailerAfterTheMainImageIsCutOff() {
        assertContentEquals(stripJpegMetadata(fixture("baseline")), stripJpegMetadata(fixture("trailer")))
    }
}
