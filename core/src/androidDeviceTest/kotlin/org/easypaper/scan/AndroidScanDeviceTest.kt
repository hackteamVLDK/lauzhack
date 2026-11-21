package org.easypaper.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.test.runTest
import org.easypaper.diary.parseBloodPressure
import org.easypaper.model.BloodPressure
import org.easypaper.report.shrinkPhoto
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * ML Kit on the emulator reads package photos drawn here: real DataMatrix / EAN-13 symbols (ZXing) and printed
 * names, three packages of the local list; and a blood pressure monitor display. Photos go through shrinkPhoto
 * first, as in the app. No network, no LLM.
 */
class AndroidScanDeviceTest {
    private val db = MedicationDb()

    private fun photo(draw: Canvas.(Paint) -> Unit): ByteArray {
        val bmp = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(Color.WHITE)
            draw(Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD })
        }
        return shrinkPhoto(ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it); bmp.recycle() }.toByteArray())
    }

    private fun Canvas.symbol(format: BarcodeFormat, contents: String, left: Float, top: Float, w: Int, h: Int) {
        val m = MultiFormatWriter().encode(contents, format, w, h, mapOf(EncodeHintType.MARGIN to 4))
        val black = Paint().apply { color = Color.BLACK }
        val sx = w.toFloat() / m.width
        val sy = h.toFloat() / m.height
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m[x, y]) drawRect(left + x * sx, top + y * sy, left + (x + 1) * sx, top + (y + 1) * sy, black)
        }
    }

    private fun Canvas.lines(p: Paint, size: Float, vararg text: String) {
        p.textSize = size
        text.forEachIndexed { i, t -> drawText(t, 80f, 160f + i * size * 1.4f, p) }
    }

    @Test
    fun threePackagesFromTheListAreRecognized() = runTest(timeout = 3.minutes) {
        // 1. GS1 DataMatrix as on Swiss packs (GTIN, expiry, batch); FNC1 first = GS1 mode
        val dataMatrix = photo { p ->
            lines(p, 70f, "DOLO SPEDIFEN forte", "400 mg", "10 Filmtabletten")
            symbol(BarcodeFormat.DATA_MATRIX, "\u001D0107680567200025" + "17271231" + "10AB12", 900f, 500f, 500, 500)
        }
        // 2. EAN-13 only
        val ean = photo { p ->
            lines(p, 70f, "ACETALGIN", "1 g", "16 Filmtabletten")
            symbol(BarcodeFormat.EAN_13, "7680623550019", 300f, 650f, 900, 350)
        }
        // 3. no readable code: the printed name is enough
        val textOnly = photo { p -> lines(p, 90f, "SPEDIFEN", "400 mg", "30 Filmtabletten") }

        val m1 = db.recognize(readPhoto(dataMatrix))
        assertTrue(m1.byCode, "DataMatrix: $m1")
        assertEquals("7680567200025", m1.candidates.single().gtin)
        assertEquals("271231", m1.code?.expiry)
        assertEquals("AB12", m1.code?.batch)

        val m2 = db.recognize(readPhoto(ean))
        assertTrue(m2.byCode, "EAN: $m2")
        assertEquals("7680623550019", m2.candidates.single().gtin)

        val reading3 = readPhoto(textOnly)
        val m3 = db.recognize(reading3)
        assertEquals(false, m3.byCode)
        assertEquals("7680556770041", m3.candidates.firstOrNull()?.gtin, "text read: ${reading3.text}")
    }

    @Test
    fun bloodPressureFromAMonitorDisplay() = runTest(timeout = 3.minutes) {
        val display = photo { p ->
            p.textSize = 60f; drawText("SYS mmHg", 120f, 200f, p); drawText("DIA mmHg", 120f, 560f, p); drawText("PUL /min", 120f, 900f, p)
            p.textSize = 240f; drawText("128", 700f, 330f, p); drawText("84", 760f, 690f, p); drawText("72", 760f, 1030f, p)
        }
        val reading = readPhoto(display)
        assertEquals(BloodPressure(128, 84, 72), parseBloodPressure(reading.text), "text read: ${reading.text}")
        assertEquals(emptyList(), reading.codes)
    }
}
