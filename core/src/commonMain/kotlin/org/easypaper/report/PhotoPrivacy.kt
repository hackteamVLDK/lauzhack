package org.easypaper.report

/**
 * Removes what a phone writes into a JPEG besides the picture: EXIF (GPS position, phone model, time), XMP, IPTC,
 * MPF and comments, and everything after the main image's EOI (Ultra HDR gain maps, MPF secondary images, Motion
 * Photo video, depth maps — each can carry its own metadata). Keeps the colour segments (JFIF APP0, ICC APP2,
 * Adobe APP14) and the orientation, rewritten as a minimal EXIF, so the photo is not shown sideways. Scan data is
 * copied unchanged (no re-encoding); progressive JPEGs keep all their scans.
 * Throws IllegalArgumentException for anything that is not a well-formed JPEG.
 */
fun stripJpegMetadata(jpeg: ByteArray): ByteArray {
    fun u8(i: Int) = jpeg[i].toInt() and 0xFF
    require(jpeg.size >= 4 && u8(0) == 0xFF && u8(1) == 0xD8) { "Not a JPEG" }
    val kept = mutableListOf(jpeg.copyOfRange(0, 2))
    var orientation: Int? = null
    var i = 2
    while (true) {
        require(i + 1 < jpeg.size && u8(i) == 0xFF) { "Broken JPEG at byte $i" }
        while (i + 1 < jpeg.size && u8(i + 1) == 0xFF) i++ // fill bytes
        require(i + 1 < jpeg.size) { "Broken JPEG at byte $i" }
        val marker = u8(i + 1)
        if (marker == 0xD9) { kept += jpeg.copyOfRange(i, i + 2); break } // EOI: anything after it is dropped
        if (marker in 0xD0..0xD7 || marker == 0x01) { kept += jpeg.copyOfRange(i, i + 2); i += 2; continue }
        require(i + 3 < jpeg.size) { "Broken JPEG at byte $i" }
        val length = (u8(i + 2) shl 8) or u8(i + 3)
        require(length >= 2 && i + 2 + length <= jpeg.size) { "Broken JPEG segment at byte $i" }
        if (marker == 0xDA) { // SOS header + entropy-coded data up to the next real marker
            var j = i + 2 + length
            // inside scan data FF is always followed by 00 (stuffing), D0-D7 (restart) or FF (fill)
            while (j + 1 < jpeg.size && !(u8(j) == 0xFF && u8(j + 1) != 0x00 && u8(j + 1) !in 0xD0..0xD7 && u8(j + 1) != 0xFF)) j++
            require(j + 1 < jpeg.size) { "JPEG scan without end" }
            kept += jpeg.copyOfRange(i, j)
            i = j
            continue
        }
        if (marker == 0xE1) orientation = orientation ?: exifOrientation(jpeg, i + 4, length - 2)
        val iccProfile = marker == 0xE2 && startsWith(jpeg, i + 4, "ICC_PROFILE\u0000")
        val drop = marker == 0xE1 || marker == 0xFE || (marker == 0xE2 && !iccProfile) || (marker in 0xE3..0xEF && marker != 0xEE)
        if (!drop) kept += jpeg.copyOfRange(i, i + 2 + length)
        i += 2 + length
    }
    if (orientation != null && orientation != 1) {
        val afterJfif = if (kept.size > 1 && kept[1].size > 1 && (kept[1][1].toInt() and 0xFF) == 0xE0) 2 else 1
        kept.add(afterJfif, minimalExif(orientation))
    }
    val out = ByteArray(kept.sumOf { it.size })
    var at = 0
    for (part in kept) { part.copyInto(out, at); at += part.size }
    return out
}

private fun startsWith(data: ByteArray, at: Int, text: String) =
    at + text.length <= data.size && text.indices.all { (data[at + it].toInt() and 0xFF) == text[it].code }

/** Orientation (tag 0x0112) from an APP1 payload "Exif\0\0" + TIFF; null when absent or malformed. */
internal fun exifOrientation(data: ByteArray, offset: Int, length: Int): Int? {
    val end = offset + length
    fun u8(i: Int) = if (i in offset until end) data[i].toInt() and 0xFF else throw IndexOutOfBoundsException()
    return try {
        if ((0 until 6).any { u8(offset + it) != "Exif\u0000\u0000"[it].code }) return null
        val tiff = offset + 6
        val big = when { u8(tiff) == 0x4D && u8(tiff + 1) == 0x4D -> true; u8(tiff) == 0x49 && u8(tiff + 1) == 0x49 -> false; else -> return null }
        fun u16(i: Int) = if (big) (u8(i) shl 8) or u8(i + 1) else (u8(i + 1) shl 8) or u8(i)
        fun u32(i: Int) = if (big) (u16(i) shl 16) or u16(i + 2) else (u16(i + 2) shl 16) or u16(i)
        val ifd = tiff + u32(tiff + 4)
        (0 until u16(ifd)).map { ifd + 2 + it * 12 }
            .firstOrNull { u16(it) == 0x0112 && u16(it + 2) == 3 }
            ?.let { u16(it + 8) }?.takeIf { it in 1..8 }
    } catch (_: IndexOutOfBoundsException) {
        null
    }
}

/** APP1 with "Exif\0\0", big-endian TIFF and one IFD0 entry: Orientation. */
private fun minimalExif(orientation: Int): ByteArray = byteArrayOf(
    0xFF.toByte(), 0xE1.toByte(), 0x00, 0x22, // APP1, length 34
    0x45, 0x78, 0x69, 0x66, 0x00, 0x00, // "Exif\0\0"
    0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // "MM", 42, IFD0 at 8
    0x00, 0x01, // one entry
    0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, orientation.toByte(), 0x00, 0x00, // Orientation SHORT ×1
    0x00, 0x00, 0x00, 0x00, // no next IFD
)
