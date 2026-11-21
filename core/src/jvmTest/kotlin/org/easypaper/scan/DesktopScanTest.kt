package org.easypaper.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.test.runTest
import org.easypaper.report.shrinkPhoto
import java.awt.Color
import java.awt.Font
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Desktop: package photo files with real codes (drawn by ZXing) -> shrinkPhoto -> readPhoto -> the local list. */
class DesktopScanTest {
    private val db = MedicationDb()

    private fun packPhoto(format: BarcodeFormat, contents: String, name: String, w: Int, h: Int, turned: Boolean = false): ByteArray {
        val img = BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE; g.fillRect(0, 0, img.width, img.height)
        g.color = Color.BLACK; g.font = Font(Font.SANS_SERIF, Font.BOLD, 140); g.drawString(name, 150, 300)
        if (turned) g.transform = AffineTransform.getRotateInstance(Math.PI / 2, 1000.0 + w / 2, 700.0 + h / 2) // around the symbol, stays inside
        val m = MultiFormatWriter().encode(contents, format, w, h, mapOf(EncodeHintType.MARGIN to 4))
        val sx = w / m.width; val sy = h / m.height
        for (y in 0 until m.height) for (x in 0 until m.width) if (m[x, y]) g.fillRect(1000 + x * sx, 700 + y * sy, sx, sy)
        g.dispose()
        return shrinkPhoto(ByteArrayOutputStream().also { ImageIO.write(img, "jpeg", it) }.toByteArray())
    }

    @Test
    fun threePackagesFromTheListByTheirCodes() = runTest {
        val dm = db.recognize(readPhoto(packPhoto(BarcodeFormat.DATA_MATRIX, "\u001D0107680567200025" + "17271231" + "10AB12", "DOLO SPEDIFEN", 900, 900)))
        assertTrue(dm.byCode, "$dm")
        assertEquals("7680567200025", dm.candidates.single().gtin)
        assertEquals("271231", dm.code?.expiry)

        val ean = db.recognize(readPhoto(packPhoto(BarcodeFormat.EAN_13, "7680623550019", "ACETALGIN", 1400, 500)))
        assertEquals("7680623550019", ean.candidates.single().gtin)

        val turned = db.recognize(readPhoto(packPhoto(BarcodeFormat.EAN_13, "7680556770041", "SPEDIFEN", 1400, 500, turned = true)))
        assertEquals("7680556770041", turned.candidates.single().gtin, "EAN turned by 90°")
    }

    @Test
    fun noCodeMeansTypingTheName() = runTest {
        val img = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB).apply { createGraphics().apply { color = Color.WHITE; fillRect(0, 0, 800, 600) } }
        assertEquals(PhotoReading(emptyList(), ""), readPhoto(ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()))
        assertFailsWith<IllegalArgumentException> { readPhoto("not an image".encodeToByteArray()) }
    }
}
