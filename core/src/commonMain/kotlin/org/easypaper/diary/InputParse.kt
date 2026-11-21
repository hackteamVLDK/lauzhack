package org.easypaper.diary

import org.easypaper.model.BloodPressure

// Patient input without AI. Anything ambiguous gives null instead of a guess; the patient confirms the result anyway.

private const val NOT_LETTER_BEFORE = """(?<![\p{L}])"""
private const val NOT_LETTER_AFTER = """(?![\p{L}])"""
private val SYS_LABEL = Regex("""${NOT_LETTER_BEFORE}(?:SYSTOLISCH|SYSTOLIC|SYST|SYS|СИСТОЛ\p{L}*|СИСТ|СИС)$NOT_LETTER_AFTER""")
private val DIA_LABEL = Regex("""${NOT_LETTER_BEFORE}(?:DIASTOLISCH|DIASTOLIC|DIAST|DIA|ДИАСТОЛ\p{L}*|ДІАСТОЛ\p{L}*|ДИАСТ|ДІАСТ|ДИА|ДІА)$NOT_LETTER_AFTER""")
private const val PULSE_WORDS = """(?:${NOT_LETTER_BEFORE}(?:PULSE|PULS|PUL|ПУЛЬС|HR|PR)$NOT_LETTER_AFTER|P\s*/\s*MIN|♥)"""
private val PULSE_LABEL = Regex(PULSE_WORDS)
private val LABELS = listOf(SYS_LABEL, DIA_LABEL, PULSE_LABEL)

/** A run of digits with what is glued to it: "128", "21:15", "21.11.", "-72", "1,5". */
private val NUMBER_TOKEN = Regex("""[-+]?\d+(?:[.,:/]\d+)*[.:]?""") // no class may start with "[:" — Android ICU reads it as a POSIX property and fails to compile

/**
 * Blood pressure from typed text ("120/80 72", "120 на 80", "120 zu 80") or from OCR of a monitor display
 * ("SYS 128 mmHg DIA 84 PULSE 72 /min", labels in EN/DE/RU/UK, number after or before the label).
 * Null when the text is ambiguous (two readings, conflicting layouts, leftover numbers, signs) or the values are not
 * physically possible with diastolic < systolic.
 */
fun parseBloodPressure(text: String): BloodPressure? {
    val t = text.uppercase()
    val hasLabels = SYS_LABEL.containsMatchIn(t) && DIA_LABEL.containsMatchIn(t)
    val bp = (if (hasLabels) labeled(t) else typed(t) ?: unlabeled(t)) ?: return null
    val ok = bp.systolic in SYSTOLIC && bp.diastolic in DIASTOLIC && bp.diastolic < bp.systolic && (bp.pulse == null || bp.pulse in PULSE)
    return bp.takeIf { ok }
}

/** Monitor display with labels. Every label must get exactly one plain 2–3 digit number. */
private fun labeled(t: String): BloodPressure? {
    val labelHits = LABELS.map { it.findAll(t).toList() }
    if (labelHits.any { it.size > 1 }) return null // a label twice: two readings on one screen
    val labelRanges = labelHits.flatten().map { it.range }
    val tokens = NUMBER_TOKEN.findAll(t).toList()
    fun plain(token: MatchResult) = token.value.toIntOrNull()?.takeIf { token.value.length in 2..3 && token.value.all(Char::isDigit) }
    fun noLabelBetween(from: Int, to: Int) = labelRanges.none { it.first >= from && it.last < to }

    // nearest number after (or before) the label, with no other label in between; a time or date gives null
    fun after(label: MatchResult) = tokens.firstOrNull { it.range.first > label.range.last }
        ?.takeIf { noLabelBetween(label.range.last + 1, it.range.first) }?.let(::plain)
    fun before(label: MatchResult) = tokens.lastOrNull { it.range.last < label.range.first }
        ?.takeIf { noLabelBetween(it.range.last + 1, label.range.first) }?.let(::plain)

    fun reading(read: (MatchResult) -> Int?): BloodPressure? {
        val sys = read(labelHits[0].single()) ?: return null
        val dia = read(labelHits[1].single()) ?: return null
        val pulseLabel = labelHits[2].singleOrNull()
        val pulse = if (pulseLabel != null) read(pulseLabel) ?: return null else null
        return BloodPressure(sys, dia, pulse)
    }
    val a = reading(::after)
    val b = reading(::before)
    return if (a != null && b != null && a != b) null else a ?: b // two different full readings: ambiguous
}

/** "120/80 [pulse 72]" or "120 на|zu|over 80 [72]": exactly one reading and no other numbers in the text. */
private fun typed(t: String): BloodPressure? {
    val pattern = Regex("""(?<![\d.,:/+-])(\d{2,3})(?:\s*/\s*|\s+(?:НА|ZU|OVER|ÜBER|UEBER)\s+)(\d{2,3})(?![\d.,:/])(?:\s*[,;]?\s*(?:$PULSE_WORDS)?\s*[=:]?\s*(?<![\d.,:/+-])(\d{2,3})(?![\d.,:/]))?""")
    val matches = pattern.findAll(t).toList()
    if (matches.size != 1) return null
    val m = matches.single()
    if (Regex("""\d""").containsMatchIn(t.removeRange(m.range))) return null // a leftover number we cannot place
    return BloodPressure(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull())
}

/** Three bare numbers in display order (systolic, diastolic, pulse); nothing else that looks like a number. */
private fun unlabeled(t: String): BloodPressure? {
    val tokens = NUMBER_TOKEN.findAll(t).map { it.value }.toList()
    if (tokens.size != 3 || tokens.any { it.length !in 2..3 || !it.all(Char::isDigit) }) return null
    return BloodPressure(tokens[0].toInt(), tokens[1].toInt(), tokens[2].toInt())
}

private const val GLASS_ML = 250
private const val MAX_COUNT = 20
private val UNIT = """(ML|МЛ|MILLILITER\p{L}*|МИЛЛИЛИТР\p{L}*|МІЛІЛІТР\p{L}*|DL|ДЛ|CL|L|Л|LITER\p{L}*|LITRE\p{L}*|ЛИТР\p{L}*|ЛІТР\p{L}*)$NOT_LETTER_AFTER"""
private const val AMOUNT = """(\d+(?:[.,]\d+)?)"""
private val WATER_PATTERNS = listOf(
    Regex("""(\d+)\s*[X×*]\s*$AMOUNT\s*$UNIT"""), // 2x250 ml
    Regex("""$AMOUNT\s*$UNIT"""), // 1,5 l
    Regex("""(?:(\d+)\s*)?${NOT_LETTER_BEFORE}(СТАКАН\p{L}*|СКЛЯНК\p{L}*|GLAS\p{L}*|GLÄSER|CUP\p{L}*|ЧАШК\p{L}*|ЧАШЕК|ЧАШОК|TASSE\p{L}*)"""), // 2 стакана
)

/**
 * Water in mL from text: "1,5 l", "500ml", "2x250 мл", "3 dl", "2 стакана" / "a glass" / "2 склянки" (250 mL each).
 * Exactly one amount; a bare number, a bottle, a sign, a fraction or range ("1/2 l", "1-2 l"), a fractional
 * count of glasses or an absurd count -> null. Result 1..5000 mL.
 */
fun parseWaterMl(text: String): Int? {
    var rest = text.uppercase()
    val amounts = mutableListOf<Long>()
    for ((index, pattern) in WATER_PATTERNS.withIndex()) {
        for (m in pattern.findAll(rest)) {
            // what stands right before the amount: "-500", "1/2", "1-2", "1.5 glasses" are not one clear amount
            val before = rest.substring(0, m.range.first).trimEnd().lastOrNull()
            if (before != null && (before.isDigit() || before in "-+/.,–")) return null
            val g = m.groupValues
            amounts += when (index) {
                0 -> (g[1].toLongOrNull()?.takeIf { it in 1..MAX_COUNT } ?: return null) * (ml(g[2], g[3]) ?: return null)
                1 -> ml(g[1], g[2]) ?: return null
                else -> (if (g[1].isEmpty()) 1L else g[1].toLongOrNull()?.takeIf { it in 1..MAX_COUNT } ?: return null) * GLASS_ML
            }
        }
        rest = pattern.replace(rest) { " ".repeat(it.value.length) } // keep positions, never read twice
    }
    return amounts.singleOrNull()?.takeIf { it in WATER_ML.first..WATER_ML.last }?.toInt()
}

private fun ml(amount: String, unit: String): Long? {
    val value = amount.replace(',', '.').toDoubleOrNull() ?: return null
    val factor = when {
        unit.startsWith("ML") || unit.startsWith("МЛ") || unit.startsWith("MILLI") || unit.startsWith("МИЛЛИ") || unit.startsWith("МІЛІ") -> 1
        unit == "DL" || unit == "ДЛ" -> 100
        unit == "CL" -> 10
        else -> 1000 // L, Л, LITER, ЛИТР, ЛІТР
    }
    val ml = value * factor
    return if (ml > 1_000_000) null else kotlin.math.round(ml).toLong()
}
