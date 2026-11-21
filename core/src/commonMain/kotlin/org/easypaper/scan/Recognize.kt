package org.easypaper.scan

/** What the device read from one photo without any LLM: barcode contents and the printed text (ML Kit, #28). */
data class PhotoReading(val codes: List<String>, val text: String)

/**
 * A package photo matched against the local list. [byCode]: the GTIN from the barcode/DataMatrix is in the list,
 * so [candidates] holds exactly that package; otherwise up to 3 candidates by the printed name. The patient
 * always confirms (#31); [code] keeps expiry and batch from a DataMatrix even when the GTIN is unknown.
 */
data class PackageMatch(val code: ScanCode?, val candidates: List<MedicationInfo>, val byCode: Boolean)

fun MedicationDb.recognize(reading: PhotoReading): PackageMatch {
    val codes = reading.codes.mapNotNull(::parseScanCode)
    for (code in codes) find(code)?.let { return PackageMatch(code, listOf(it), byCode = true) }
    return PackageMatch(codes.firstOrNull(), search(reading.text), byCode = false)
}

/** One line of OCR text with its box in the photo (pixels, y grows downwards). */
data class TextLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * OCR text in reading order by rows: OCR engines group a monitor display into columns ("SYS DIA PUL" then
 * "128 84 72"), so lines that overlap vertically by at least half of the lower one are joined left to right.
 */
fun textByRows(lines: List<TextLine>): String {
    val rows = mutableListOf<MutableList<TextLine>>()
    for (line in lines.sortedBy { it.top }) {
        val row = rows.firstOrNull { r ->
            r.any { o ->
                val overlap = minOf(o.bottom, line.bottom) - maxOf(o.top, line.top)
                overlap * 2 >= minOf(o.bottom - o.top, line.bottom - line.top)
            }
        }
        if (row != null) row += line else rows += mutableListOf(line)
    }
    return rows.joinToString("\n") { r -> r.sortedBy { it.left }.joinToString(" ") { it.text } }
}
