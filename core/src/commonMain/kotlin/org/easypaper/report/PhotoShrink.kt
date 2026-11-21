package org.easypaper.report

/** Longest side and size of a photo as it is stored and sent (task 28); the AI step skips anything bigger. */
const val PHOTO_MAX_SIDE = 1024
const val PHOTO_MAX_BYTES = 200_000

/**
 * Shrinks a camera or gallery photo before it is saved: [encode] (the platform: ImageIO, Android Bitmap) re-encodes
 * the pixels as JPEG with the longest side ≤ maxSide at the given quality. Starts at [PHOTO_MAX_SIDE] px, q80 and
 * steps down until the result fits [PHOTO_MAX_BYTES]. Metadata is stripped; the original's EXIF orientation is
 * kept, so the pixels need no rotating.
 */
fun shrinkPhoto(photo: ByteArray, encode: (maxSide: Int, quality: Int) -> ByteArray): ByteArray {
    val orientation = jpegOrientation(photo)
    for (side in intArrayOf(PHOTO_MAX_SIDE, 768, 512)) for (quality in intArrayOf(80, 65, 50)) {
        val out = stripJpegMetadata(encode(side, quality), orientation)
        if (out.size <= PHOTO_MAX_BYTES) return out
    }
    throw IllegalArgumentException("Photo stays above $PHOTO_MAX_BYTES bytes even at 512 px")
}

/**
 * Clockwise turn that shows a stored photo upright (0, 90, 180, 270): shrinkPhoto keeps the EXIF orientation
 * tag instead of rotating pixels, so viewers and recognisers turn the picture themselves. Mirrored orientations
 * (2, 4, 5, 7) do not come from cameras and are shown unturned.
 */
fun jpegRotationDegrees(jpeg: ByteArray): Int = when (jpegOrientation(jpeg)) { 3 -> 180; 6 -> 90; 8 -> 270; else -> 0 }

/** EXIF orientation (1..8) of a JPEG, read from the segments before the image data; null for none or not a JPEG. */
internal fun jpegOrientation(jpeg: ByteArray): Int? {
    fun u8(i: Int) = jpeg[i].toInt() and 0xFF
    if (jpeg.size < 4 || u8(0) != 0xFF || u8(1) != 0xD8) return null
    var i = 2
    while (i + 3 < jpeg.size && u8(i) == 0xFF) {
        val marker = u8(i + 1)
        if (marker == 0xDA || marker == 0xD9) return null
        val length = (u8(i + 2) shl 8) or u8(i + 3)
        if (marker == 0xE1) exifOrientation(jpeg, i + 4, length - 2)?.let { return it }
        i += 2 + length
    }
    return null
}
