package org.easypaper.scan

import org.easypaper.model.Medication
import org.easypaper.model.MedicationSource

/** One package in the local medication list. Facts only: no indications, no advice. */
data class MedicationInfo(val gtin: String, val nameDe: String, val nameFr: String, val atc: String)

/** What a package code carries: GTIN plus, for GS1 DataMatrix, expiry (YYMMDD), batch and serial. */
data class ScanCode(val gtin: String, val expiry: String? = null, val batch: String? = null, val serial: String? = null)

private const val GS = '\u001D' // FNC1 separator in GS1 DataMatrix

/**
 * Parses the text a barcode/DataMatrix reader returns: EAN-13 / UPC-A / GTIN-14 digits, a GS1 element string
 * ("]d2" prefix optional, GS-separated) or the printed form "(01)…(17)…(10)…(21)…".
 * Returns null when there is no GTIN with a valid check digit.
 */
fun parseScanCode(raw: String): ScanCode? {
    var s = raw.trim()
    if (s.length > 3 && s[0] == ']') s = s.substring(3) // symbology identifier, e.g. ]d2, ]E0
    if (s.all { it.isDigit() } && s.length in 12..14) return normalize(s)?.let { ScanCode(it) }

    val fields = mutableMapOf<String, String>()
    if (s.startsWith("(")) {
        Regex("""\((\d{2,4})\)([^(]*)""").findAll(s).forEach { fields[it.groupValues[1]] = it.groupValues[2].trim() }
    } else {
        var i = 0
        while (i + 2 <= s.length) {
            if (s[i] == GS) { i++; continue }
            val ai = s.substring(i, i + 2)
            val fixed = when (ai) { "01" -> 14; "17", "11", "15" -> 6; else -> null }
            val end = if (fixed != null) i + 2 + fixed else s.indexOf(GS, i).let { if (it < 0) s.length else it }
            if (end > s.length) break
            fields[ai] = s.substring(i + 2, end) // unknown variable AIs are read up to the next GS and ignored
            i = end
        }
    }
    val gtin = fields["01"]?.let(::normalize) ?: return null
    return ScanCode(gtin, fields["17"], fields["10"], fields["21"])
}

/** GTIN with valid check digit, as stored in the database: 13 digits when the GTIN-14/UPC-A form allows it. */
private fun normalize(digits: String): String? {
    if (digits.length !in 12..14 || !digits.all { it.isDigit() }) return null
    val sum = digits.dropLast(1).reversed().withIndex().sumOf { (i, c) -> (c - '0') * if (i % 2 == 0) 3 else 1 }
    if ((10 - sum % 10) % 10 != digits.last() - '0') return null
    return when {
        digits.length == 12 -> "0$digits"
        digits.length == 14 && digits[0] == '0' -> digits.substring(1)
        else -> digits
    }
}

/** Local medication subset. Lookup by scanned code, or by text OCR read from the box. */
class MedicationDb(items: List<MedicationInfo> = DEMO_MEDICATIONS) {
    private val byGtin = items.associateBy { it.gtin }
    private val indexed = items.map { item ->
        val words = (tokens(item.nameDe) + tokens(item.nameFr)).toSet()
        Triple(item, setOf(tokens(item.nameDe).first(), tokens(item.nameFr).first()), words)
    }

    fun find(code: ScanCode): MedicationInfo? = byGtin[code.gtin]

    /**
     * Best candidates for OCR text of a package: the brand must be present, then most matching words win,
     * ties go to the name with fewer words missing from the text.
     */
    fun search(ocrText: String, limit: Int = 3): List<MedicationInfo> {
        val seen = tokens(ocrText).toSet()
        return indexed.filter { (_, brands, _) -> brands.any { it in seen } }
            .sortedWith(compareByDescending<Triple<MedicationInfo, Set<String>, Set<String>>> { (_, _, words) -> words.count { it in seen } }
                .thenBy { (_, _, words) -> words.count { it !in seen } })
            .take(limit)
            .map { it.first }
    }

    // "2.5" stays one token, so a 2.5 mg pack does not match the text "5 mg".
    private fun tokens(text: String) = TOKEN.findAll(text.uppercase()).map { it.value.replace(',', '.') }.toList()
}

private val TOKEN = Regex("""\p{N}+(?:[.,]\p{N}+)?|\p{L}+""")

private val STRENGTH = Regex("""\d+(?:[.,]\d+)?\s?(?:mg|g|mcg|µg|ml|IE)(?:\s?/\s?\d*(?:[.,]\d+)?\s?ml)?(?![\p{L}])""")

/**
 * Draft entry for the patient to confirm (#4): source SCAN, confirmed = false until the patient checks it.
 * [dose] is the strength printed in the package name, e.g. "400 mg".
 */
fun MedicationInfo.toMedication(locale: String = "de"): Medication {
    val name = if (locale.startsWith("fr")) nameFr else nameDe
    return Medication(name = name, code = gtin, dose = STRENGTH.find(nameDe)?.value, source = MedicationSource.SCAN, confirmed = false)
}
