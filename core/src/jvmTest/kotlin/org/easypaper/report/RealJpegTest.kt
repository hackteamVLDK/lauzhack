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

/** stripJpegMetadata on files from a real encoder (tools/make_test_jpegs.py), decoded by javax.imageio. */
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

    @Test
    fun realFilesLoseMetadataButNotPixels() {
        for (name in listOf("baseline", "progressive", "trailer")) {
            val original = fixture(name)
            assertTrue("PhoneModelX" in original.decodeToString(), "fixture $name has no metadata")
            val clean = stripJpegMetadata(original)
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
