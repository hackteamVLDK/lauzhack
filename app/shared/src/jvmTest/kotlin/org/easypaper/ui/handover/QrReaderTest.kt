package org.easypaper.ui.handover

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QrReaderTest {
    private fun png(img: BufferedImage) = ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()

    @Test
    fun readsTheHandoverCodeFromAScreenshot() {
        val code = "ep1:Q2hhbmdlTWVQbGVhc2UxMg:" + "k".repeat(43)
        val matrix = QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 400, 400)
        val img = BufferedImage(600, 900, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until height) for (x in 0 until width) setRGB(x, y, 0xF2F2F7) // phone screen around the code
            for (y in 0 until 400) for (x in 0 until 400) setRGB(100 + x, 200 + y, if (matrix[x, y]) 0 else 0xFFFFFF)
        }
        assertEquals(code, readQrImage(png(img)))
    }

    @Test
    fun noCodeOrNoImageGivesNull() {
        assertNull(readQrImage(png(BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB))))
        assertNull(readQrImage("not an image".encodeToByteArray()))
    }
}
