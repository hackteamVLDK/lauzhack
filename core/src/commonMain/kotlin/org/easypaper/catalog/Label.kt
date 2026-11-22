package org.easypaper.catalog

/**
 * Text helpers for matching a package label against registry names. Normalisation is only for search: the
 * original label text and the registry's own spelling are always kept for the person to compare.
 */

private val FOLD = buildMap {
    fun put(chars: String, to: Char) = chars.forEach { put(it, to) }
    put("ÀÁÂÃÄÅĀĂĄ", 'A'); put("ÇĆĈĊČ", 'C'); put("ÐĎĐ", 'D'); put("ÈÉÊËĒĔĖĘĚ", 'E'); put("ĜĞĠĢ", 'G')
    put("ÌÍÎÏĨĪĬĮİ", 'I'); put("ĴJ", 'J'); put("ĶK", 'K'); put("ĹĻĽĿŁ", 'L'); put("ÑŃŅŇ", 'N'); put("ÒÓÔÕÖØŌŎŐ", 'O')
    put("ŔŖŘ", 'R'); put("ŚŜŞŠ", 'S'); put("ŢŤŦ", 'T'); put("ÙÚÛÜŨŪŬŮŰŲ", 'U'); put("ÝŸŶ", 'Y'); put("ŹŻŽ", 'Z')
    put("Ё", 'Е'); put("Й", 'И')
}

/** Upper case without accents (Latin) and with Ё→Е, Й→И (Cyrillic); "ß" becomes "SS", "Œ" "OE". */
fun foldForSearch(text: String): String = buildString(text.length) {
    for (c in text.uppercase()) when (c) {
        'ß', 'ẞ' -> append("SS")
        'Œ' -> append("OE")
        'Æ' -> append("AE")
        else -> append(FOLD[c] ?: c)
    }
}

private val WORD = Regex("""\p{N}+(?:[.,]\p{N}+)?|\p{L}+""")

/** Search tokens: folded words and numbers; a decimal comma becomes a dot, so "2,5" and "2.5" are the same token. */
fun searchTokens(text: String): List<String> =
    WORD.findAll(foldForSearch(text)).map { it.value.replace(',', '.') }.toList()

/** An amount with its unit, normalised for comparison: masses in mg, volumes in ml, other units as written. */
data class Strength(val value: Double, val unit: String) {
    override fun toString(): String = (if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()) + " " + unit
}

private val STRENGTH = Regex(
    // a number starts after a space or punctuation, never inside a word ("D3 200 000 UI" is 200 000, not 3 200 000)
    """(?<![\p{L}\d.,])(\d+(?:[ \u00A0\u202F]\d{3})*(?:[.,]\d+)?)\s*(MG|MCG|ΜG|µG|UG|G|ML|UI|IU|IE|МГ|МКГ|Г|МЛ|МЕ|%)(?![\p{L}])""",
    RegexOption.IGNORE_CASE,
)

/** Every strength printed in [text]: "1 g", "2,5 mg", "200 000 UI", "500мг", "10 mg/ml" (the per-ml part is ignored). */
fun strengthsIn(text: String): List<Strength> = STRENGTH.findAll(foldForSearch(text)).mapNotNull { m ->
    val number = m.groupValues[1].replace(Regex("""[ \u00A0\u202F]"""), "").replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
    normalizeStrength(number, m.groupValues[2])
}.toList()

/** Registry amount ("500", "2,5", "200 000") with its unit ("mg", "g", "µg", "UI") in comparable form. */
fun strengthOf(amount: String?, unit: String?): Strength? {
    val number = amount?.replace(Regex("""[ \u00A0\u202F]"""), "")?.replace(',', '.')?.toDoubleOrNull() ?: return null
    return normalizeStrength(number, unit ?: return null)
}

private fun normalizeStrength(number: Double, rawUnit: String): Strength? {
    val unit = foldForSearch(rawUnit.trim()).replace('Μ', 'U').replace('µ', 'U')
    return when (unit) {
        "MG", "МГ" -> Strength(number, "mg")
        "G", "Г" -> Strength(number * 1000, "mg")
        "MCG", "UG", "МКГ" -> Strength(number / 1000, "mg")
        "ML", "МЛ" -> Strength(number, "ml")
        "UI", "IU", "IE", "МЕ" -> Strength(number, "IU")
        "%" -> Strength(number, "%")
        else -> null
    }
}

/**
 * Words that tell versions of one brand apart and must never be dropped: a label saying FORTE never matches a
 * product without it. Multi-word variants are joined with a space and matched as a phrase.
 */
val VARIANT_WORDS: Set<String> = setOf(
    "FORTE", "MAX", "MAXI", "JUNIOR", "ADULTES", "ADULTE", "ADULTS", "ADULT", "ERWACHSENE", "ENFANTS", "ENFANT", "KINDER",
    "NOURRISSONS", "BEBE", "SANS SUCRE", "ZUCKERFREI", "RETARD", "LP", "SR", "XR", "CR", "MR", "ER", "DEPOT", "DUO", "PLUS",
    "EXTRA", "RAPID", "RAPIDE", "LIBERATION PROLONGEE", "PROLONGED RELEASE", "ДЕТСКИЙ", "ФОРТЕ", "МАКС", "РЕТАРД", "ПЛЮС",
)

private val PHRASE_VARIANTS = VARIANT_WORDS.filter { ' ' in it }

/** The variant words present in [text] (phrases first, so SANS SUCRE is one variant, not two words). */
fun variantsIn(text: String): Set<String> {
    val folded = " " + searchTokens(text).joinToString(" ") + " "
    val found = PHRASE_VARIANTS.filter { " $it " in folded }.toMutableSet()
    val single = searchTokens(text).toSet()
    VARIANT_WORDS.filterTo(found) { ' ' !in it && it in single }
    return found
}

/** Words of a name that say nothing about which product it is (forms, units, packaging, "et", "de"). */
internal val FILLER_WORDS: Set<String> = setOf(
    "MG", "G", "ML", "MCG", "UI", "DE", "DU", "LA", "LE", "LES", "ET", "EN", "POUR", "PAR", "A", "AU", "UND", "MIT", "ZUR", "ZUM",
    "STK", "PCE", "PCES", "X", "BTE", "FL", "TUBE", "SACHET", "SACHETS", "COMPRIME", "COMPRIMES", "GELULE", "GELULES",
    "TABL", "FILMTABL", "KAPS", "CPR", "PELL", "CAPS", "SOLUTION", "BUVABLE", "GRANULES", "POUDRE", "SIROP",
)
