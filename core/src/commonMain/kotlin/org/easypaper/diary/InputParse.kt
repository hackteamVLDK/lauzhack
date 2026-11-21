package org.easypaper.diary

import org.easypaper.model.BloodPressure

// Patient input without AI. Anything ambiguous gives null instead of a guess; the patient confirms the result anyway.

private const val NOT_LETTER_BEFORE = """(?<![\p{L}])"""
private const val NOT_LETTER_AFTER = """(?![\p{L}])"""
private val SYS_LABEL = """${NOT_LETTER_BEFORE}(?:SYSTOLISCH|SYSTOLIC|SYST|SYS|СИСТОЛ\p{L}*|СИСТ|СИС)$NOT_LETTER_AFTER"""
private val DIA_LABEL = """${NOT_LETTER_BEFORE}(?:DIASTOLISCH|DIASTOLIC|DIAST|DIA|ДИАСТОЛ\p{L}*|ДИАСТ|ДИА)$NOT_LETTER_AFTER"""
private val PULSE_LABEL = """(?:${NOT_LETTER_BEFORE}(?:PULSE|PULS|PUL|ПУЛЬС|HR|PR)$NOT_LETTER_AFTER|P\s*/\s*MIN|♥)"""
private const val NUM = """(?<!\d)(\d{2,3})(?!\d)"""
private val DATE_OR_TIME = Regex("""\d\s*[:.\-]\s*\d""")

/**
 * Blood pressure from typed text ("120/80 72", "120 на 80", "120 zu 80") or from OCR of a monitor display
 * ("SYS 128 mmHg DIA 84 PULSE 72 /min", labels in EN/DE/RU, number after or before the label).
 * The result must be physically possible and diastolic < systolic, otherwise null.
 */
fun parseBloodPressure(text: String): BloodPressure? {
    val t = text.uppercase()
    val bp = labeled(t) ?: slash(t) ?: words(t) ?: unlabeled(t) ?: return null
    val ok = bp.systolic in SYSTOLIC && bp.diastolic in DIASTOLIC && bp.diastolic < bp.systolic && (bp.pulse == null || bp.pulse in PULSE)
    return bp.takeIf { ok }
}

private fun labeled(t: String): BloodPressure? {
    if (!Regex(SYS_LABEL).containsMatchIn(t) || !Regex(DIA_LABEL).containsMatchIn(t)) return null
    val hasPulse = Regex(PULSE_LABEL).containsMatchIn(t)
    fun after(label: String) = Regex("""$label[^0-9]{0,12}?$NUM""").find(t)?.groupValues?.get(1)?.toInt()
    fun before(label: String) = Regex("""$NUM[^0-9]{0,8}?$label""").find(t)?.groupValues?.get(1)?.toInt()
    // displays print the value either after or before every label; pick the reading where all labels get a value
    for (read in listOf(::after, ::before)) {
        val sys = read(SYS_LABEL) ?: continue
        val dia = read(DIA_LABEL) ?: continue
        val pulse = if (hasPulse) read(PULSE_LABEL) ?: continue else null
        return BloodPressure(sys, dia, pulse)
    }
    return null
}

private fun slash(t: String): BloodPressure? {
    val m = Regex("""$NUM\s*/\s*$NUM(?:\s*[,;]?\s*(?:$PULSE_LABEL)?\s*[:=]?\s*$NUM)?""").find(t) ?: return null
    return BloodPressure(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull())
}

private fun words(t: String): BloodPressure? {
    val m = Regex("""$NUM\s+(?:НА|ZU|OVER|ÜBER|UEBER)\s+$NUM(?:\s*[,;]?\s*(?:$PULSE_LABEL)?\s*[:=]?\s*$NUM)?""").find(t) ?: return null
    return BloodPressure(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull())
}

/** Three bare numbers in display order (systolic, diastolic, pulse), only when no date or time is on screen. */
private fun unlabeled(t: String): BloodPressure? {
    if (DATE_OR_TIME.containsMatchIn(t)) return null
    val numbers = Regex("""\d+""").findAll(t).map { it.value }.toList()
    if (numbers.size != 3 || numbers.any { it.length !in 2..3 }) return null
    return BloodPressure(numbers[0].toInt(), numbers[1].toInt(), numbers[2].toInt())
}

private const val GLASS_ML = 250
private val UNIT = """(ML|МЛ|MILLILITER\p{L}*|МИЛЛИЛИТР\p{L}*|DL|ДЛ|CL|L|Л|LITER\p{L}*|LITRE\p{L}*|ЛИТР\p{L}*)$NOT_LETTER_AFTER"""
private val AMOUNT = """(\d+(?:[.,]\d+)?)"""
private val WATER_PATTERNS = listOf(
    Regex("""(?<![\d.,])(\d+)\s*[X×*]\s*$AMOUNT\s*$UNIT"""), // 2x250 ml
    Regex("""(?<![\d.,])$AMOUNT\s*$UNIT"""), // 1,5 l
    Regex("""(?:(?<![\d.,])(\d+)\s*)?${NOT_LETTER_BEFORE}(СТАКАН\p{L}*|GLAS\p{L}*|GLÄSER|CUP\p{L}*|ЧАШК\p{L}*|ЧАШЕК|TASSE\p{L}*)"""), // 2 стакана
)

/**
 * Water in mL from text: "1,5 l", "500ml", "2x250 мл", "3 dl", "2 стакана" / "a glass" (250 mL each).
 * Exactly one amount must be present; a bare number or a bottle is ambiguous -> null. Result 1..5000 mL.
 */
fun parseWaterMl(text: String): Int? {
    var rest = text.uppercase()
    val amounts = mutableListOf<Int>()
    for ((index, pattern) in WATER_PATTERNS.withIndex()) {
        for (m in pattern.findAll(rest)) {
            val g = m.groupValues
            amounts += when (index) {
                0 -> g[1].toInt() * ml(g[2], g[3])
                1 -> ml(g[1], g[2])
                else -> (g[1].toIntOrNull() ?: 1) * GLASS_ML
            }
        }
        rest = pattern.replace(rest, " ") // a matched amount is not read again by the next pattern
    }
    return amounts.singleOrNull()?.takeIf { it in WATER_ML }
}

private fun ml(amount: String, unit: String): Int {
    val value = amount.replace(',', '.').toDouble()
    val factor = when {
        unit.startsWith("ML") || unit.startsWith("МЛ") || unit.startsWith("MILLI") || unit.startsWith("МИЛЛИ") -> 1
        unit == "DL" || unit == "ДЛ" -> 100
        unit == "CL" -> 10
        else -> 1000 // L, Л, LITER, ЛИТР
    }
    return kotlin.math.round(value * factor).toInt()
}
