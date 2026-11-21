package org.easypaper.scan

import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import org.easypaper.report.jpegRotationDegrees
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads barcodes (DataMatrix, EAN, UPC, Code 128) and printed text from a photo on the phone with ML Kit's
 * bundled models: no network, no Google Play services, no LLM. Feed the result to [MedicationDb.recognize]
 * (package) or parseBloodPressure (monitor display). The photo is turned upright by its EXIF orientation first:
 * shrinkPhoto keeps the tag instead of rotating pixels, and text recognition needs upright text.
 */
actual suspend fun readPhoto(photo: ByteArray): PhotoReading {
    val bitmap = BitmapFactory.decodeByteArray(photo, 0, photo.size) ?: throw IllegalArgumentException("Not an image")
    val image = InputImage.fromBitmap(bitmap, jpegRotationDegrees(photo))
    val formats = BarcodeScannerOptions.Builder().setBarcodeFormats(
        Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_CODE_128,
    ).build()
    val scanner = BarcodeScanning.getClient(formats)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        val codes = scanner.process(image).await().mapNotNull { it.rawValue }
        val lines = recognizer.process(image).await().textBlocks.flatMap { it.lines }.mapNotNull { line ->
            line.boundingBox?.let { TextLine(line.text, it.left, it.top, it.right, it.bottom) }
        }
        return PhotoReading(codes, textByRows(lines))
    } finally {
        scanner.close()
        recognizer.close()
        bitmap.recycle()
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
