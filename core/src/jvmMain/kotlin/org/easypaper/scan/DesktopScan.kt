package org.easypaper.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HINTS = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(
        BarcodeFormat.DATA_MATRIX, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.CODE_128,
    ),
    DecodeHintType.TRY_HARDER to true,
)

/** Desktop: the package code from a photo file with ZXing (no OCR here: the patient types the name if there is no code). */
actual suspend fun readPhoto(photo: ByteArray): PhotoReading = withContext(Dispatchers.Default) { // decoding and ZXing take a while: never on the UI thread
    val image = ImageIO.read(ByteArrayInputStream(photo)) ?: throw IllegalArgumentException("Not an image")
    val w = image.width
    val h = image.height
    val pixels = image.getRGB(0, 0, w, h, null, 0, w)
    // RGBLuminanceSource cannot rotate, so ZXing would never try a box photographed sideways: turn it 90° ourselves
    val turned = IntArray(pixels.size) { i -> pixels[(h - 1 - i % h) * w + i / h] }
    val code = listOf(RGBLuminanceSource(w, h, pixels), RGBLuminanceSource(h, w, turned)).firstNotNullOfOrNull { source ->
        try {
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), HINTS).text
        } catch (_: NotFoundException) {
            null
        }
    }
    PhotoReading(listOfNotNull(code), "")
}
