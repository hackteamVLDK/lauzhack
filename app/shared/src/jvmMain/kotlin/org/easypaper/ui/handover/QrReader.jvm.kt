package org.easypaper.ui.handover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.awt.FileDialog
import java.awt.Frame
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** The text of the QR code in an image (a photo or screenshot of the patient's phone); null when there is none. */
fun readQrImage(image: ByteArray): String? {
    val img = ImageIO.read(ByteArrayInputStream(image)) ?: return null
    val pixels = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
    val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(img.width, img.height, pixels)))
    val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)
    return try { MultiFormatReader().decode(bitmap, hints).text } catch (_: NotFoundException) { null }
}

/** Desktop: no camera access here; the QR is read from an image file. */
@Composable
actual fun rememberQrReader(onCode: (String) -> Unit, onFailure: () -> Unit): (() -> Unit)? {
    val code by rememberUpdatedState(onCode)
    val failure by rememberUpdatedState(onFailure)
    return remember {
        {
            val dialog = FileDialog(null as Frame?, "QR", FileDialog.LOAD).apply { isVisible = true }
            dialog.files.firstOrNull()?.let { file -> readQrImage(file.readBytes())?.let(code) ?: failure() }
        }
    }
}
