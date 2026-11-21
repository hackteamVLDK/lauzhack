package org.easypaper.report

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.ByteArrayOutputStream

/** Android: a camera or gallery photo -> JPEG ≤ 1024 px, ≤ 200 KB, no metadata. See [shrinkPhoto]. */
fun shrinkPhoto(photo: ByteArray): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(photo, 0, photo.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image" }
    // a 12 MP photo is decoded at a power-of-two fraction first, so it never needs ~48 MB of memory
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_MAX_SIDE) sample *= 2
    val image = BitmapFactory.decodeByteArray(photo, 0, photo.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: throw IllegalArgumentException("Not an image")
    try {
        return shrinkPhoto(photo) { maxSide, quality ->
            val scale = minOf(1.0, maxSide.toDouble() / maxOf(image.width, image.height))
            val w = maxOf(1, (image.width * scale).toInt())
            val h = maxOf(1, (image.height * scale).toInt())
            val rgb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) // transparent PNG areas become white
            Canvas(rgb).apply {
                drawColor(Color.WHITE)
                drawBitmap(image, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            ByteArrayOutputStream().also { rgb.compress(Bitmap.CompressFormat.JPEG, quality, it); rgb.recycle() }.toByteArray()
        }
    } finally {
        image.recycle()
    }
}
