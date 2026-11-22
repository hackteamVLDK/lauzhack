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
    put("ЁЄ", 'Е'); put("ЙІЇ", 'И'); put("Ґ", 'Г')
}

/** Search spelling only: Latin accents, Ukrainian/Russian letter variants and apostrophes are folded. */
fun foldForSearch(text: String): String = buildString(text.length) {
    for (c in text.uppercase()) when (c) {
        'ß', 'ẞ' -> append("SS")
        'Œ' -> append("OE")
        'Æ' -> append("AE")
        '\'', '’', 'ʼ', '`', '‘' -> Unit
        else -> if (c.category != CharCategory.NON_SPACING_MARK && c.category != CharCategory.COMBINING_SPACING_MARK) append(FOLD[c] ?: c)
    }
}

private val WORD = Regex("""\p{N}+(?:[.,]\p{N}+)?|\p{L}+""")

/** Search tokens: folded words and numbers; a decimal comma becomes a dot, so "2,5" and "2.5" are the same token. */
fun searchTokens(text: String): List<String> =
    WORD.findAll(foldForSearch(text)).map { it.value.replace(',', '.') }.toList()

/** A shared Latin search key lets either script find the other without changing the displayed spelling. */
internal fun comparisonWord(word: String): String = buildString {
    for (c in foldForSearch(word)) append(when (c) {
        'А' -> "A"; 'Б' -> "B"; 'В' -> "V"; 'Г' -> "G"; 'Д' -> "D"; 'Е', 'Э' -> "E"
        'Ж' -> "ZH"; 'З' -> "Z"; 'И' -> "I"; 'К' -> "K"; 'Л' -> "L"; 'М' -> "M"
        'Н' -> "N"; 'О' -> "O"; 'П' -> "P"; 'Р' -> "R"; 'С' -> "S"; 'Т' -> "T"
        'У', 'Ў' -> "U"; 'Ф' -> "F"; 'Х' -> "KH"; 'Ц' -> "TS"; 'Ч' -> "CH"; 'Ш' -> "SH"
        'Щ' -> "SHCH"; 'Ы' -> "Y"; 'Ю' -> "YU"; 'Я' -> "YA"; 'Ь', 'Ъ' -> ""
        else -> c.toString()
    })
}

internal fun comparisonTokens(text: String): List<String> = searchTokens(text).map(::comparisonWord)

/** Search evidence only: substance words are checked against registry ingredients, never added to a card. */
data class PackageLabel(
    val brand: String?,
    val brandTokens: Set<String>,
    val strengths: List<Strength>,
    val substanceTokens: Set<String>,
)

/** Explicit supplement labels are left for the patient's own words, not matched to a medicine by an ingredient. */
fun isSupplementLabel(text: String): Boolean = searchTokens(text).any { it in SUPPLEMENT_WORDS }

private val SUPPLEMENT_WORDS = setOf("NAHRUNGSERGANZUNGSMITTEL")

/** Preserves the printed name separately from normalised, capitalised brand words and potential INN words. */
fun parseLabel(text: String): PackageLabel? {
    if (isSupplementLabel(text)) return null
    val words = WORD.findAll(text).filter { it.value.any(Char::isLetter) }.toList()
    val brands = words.filter { it.value.first().isUpperCase() }
        .flatMap { searchTokens(it.value) }.filter { it.length >= 2 && it !in FILLER_WORDS }.toSet()
    val heading = text.substring(0, STRENGTH.find(text)?.range?.first ?: text.length).lineSequence().firstOrNull().orEmpty()
    val brand = searchTokens(heading).firstOrNull { it.length >= 2 && it !in FILLER_WORDS && it !in VARIANT_WORDS }
    return PackageLabel(brand, brands, strengthsIn(text), searchTokens(text)
        .filter { it.length >= 4 && it !in FILLER_WORDS && it !in VARIANT_WORDS && it != brand }
        .map { canonicalInn(it).lowercase() }.toSet())
}

/** Local spellings are search aliases only; registry composition and spelling remain intact. */
private fun canonicalInn(word: String): String = when (val folded = comparisonWord(word)) {
    "DEKSKETOPROFEN" -> "DEXKETOPROFEN"
    "BILASTIN" -> "BILASTINE"
    "IBUPROFENU" -> "IBUPROFEN"
    "PARATSETAMOL", "PARATSETAMOLU", "PARACETAMOLU" -> "PARACETAMOL"
    else -> folded
}

/** Conservative common root: optional Latin -um or final -e, at least six letters, no arbitrary prefix matching. */
internal fun innRoot(word: String): String {
    val canonical = canonicalInn(word)
    return when {
        canonical.length > 8 && canonical.endsWith("UM") -> canonical.dropLast(2)
        canonical.length > 6 && canonical.endsWith("E") -> canonical.dropLast(1)
        else -> canonical
    }
}

/** Salts and formulation words alone do not identify an active substance. */
internal fun ingredientRoots(text: String): Set<String> = searchTokens(text).map(::innRoot)
    .filter { it.length >= 6 && it !in INN_FILLERS }.toSet()

private val INN_FILLERS = setOf(
    "TROMETAMOL", "HYDROCHLORID", "CHLORHYDRAT", "HIDROKLORUR",
    "MALEAT", "MALEAS", "SODIUM", "NATRIUM", "POTASSIUM", "CALCIUM", "MONOHYDRAT", "DIHYDRAT",
)

/** An amount with its unit, normalised for comparison: masses in mg, volumes in ml, other units as written. */
data class Strength(val value: Double, val unit: String) {
    override fun toString(): String = (if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()) + " " + unit
}

private val STRENGTH = Regex(
    // a number starts after a space or punctuation, never inside a word ("D3 200 000 UI" is 200 000, not 3 200 000)
    """(?<![\p{L}\d.,])(\d+(?:[ \u00A0\u202F]\d{3})*(?:[.,]\d+)?)\s*(MG|MCG|ΜG|µG|UG|G|ML|UI|IU|IE|ME|МГ|МКГ|Г|МЛ|МО|МЕ|%)(?![\p{L}])""",
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
        "UI", "IU", "IE", "ME", "МО", "МЕ" -> Strength(number, "IU")
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
    "EXTRA", "RAPID", "RAPIDE", "LIBERATION PROLONGEE", "PROLONGED RELEASE", "FORT", "COCUK", "PEDIATRIK", "BEBEK", "ДЕТСКИИ", "ДИТЯЧИИ", "ФОРТЕ", "МАКС", "РЕТАРД", "ПЛЮС",
)

private val PHRASE_VARIANTS = VARIANT_WORDS.filter { ' ' in it }
internal val COMPARISON_VARIANTS = VARIANT_WORDS.map(::comparisonWord).toSet()

/** The variant words present in [text] (phrases first, so SANS SUCRE is one variant, not two words). */
fun variantsIn(text: String): Set<String> {
    val folded = " " + comparisonTokens(text).joinToString(" ") + " "
    val found = PHRASE_VARIANTS.map(::comparisonWord).filter { " $it " in folded }.toMutableSet()
    val single = comparisonTokens(text).toSet()
    COMPARISON_VARIANTS.filterTo(found) { ' ' !in it && it in single }
    return found
}

/** Words of a name that say nothing about which product it is (forms, units, packaging, "et", "de"). */
internal val FILLER_WORDS: Set<String> = setOf(
    "MG", "G", "ML", "MCG", "UG", "UI", "IU", "IE", "DE", "DU", "LA", "LE", "LES", "ET", "EN", "POUR", "PAR", "A", "AU", "UND", "MIT", "ZUR", "ZUM",
    "STK", "PCE", "PCES", "X", "BTE", "FL", "TUBE", "SACHET", "SACHETS", "COMPRIME", "COMPRIMES", "GELULE", "GELULES",
    "TABL", "FILMTABL", "KAPS", "CPR", "PELL", "CAPS", "SOLUTION", "BUVABLE", "GRANULES", "POUDRE", "SIROP",
    "FILM", "KAPLI", "TABLET", "TABLETS", "FILMTABLETTEN", "TABLETTEN", "MINITABLETTEN", "ILAC", "SAN", "VE", "TIC",
    "МГ", "МКГ", "Г", "МЛ", "МО", "МЕ", "ME", "ТАБЛЕТКИ", "ТАБЛЕТКА", "ТАБЛЕТОК", "КАПСУЛИ", "КАПСУЛЫ",
    "РОЗЧИН", "РАСТВОР", "ВКРИТІ", "ВКРИТИ", "ОБОЛОНКОЮ", "ПОКРЫТЫЕ", "ОБОЛОЧКОЙ", "ШТ", "ПО",
)

internal val COMPARISON_FILLERS = FILLER_WORDS.map(::comparisonWord).toSet()
