package org.easypaper.catalog

import org.easypaper.model.Ingredient

/**
 * Helpers for the Ukrainian, Russian and Belarusian registries, which describe a version by a release form text
 * ("таблетки, покрытые оболочкой, 400 мг по 6 шт …", "капсулы, 25 мг+200 мг+2.5 мг+150 мг, 10 шт. - …") and,
 * in Ukraine, by a per-unit composition sentence ("1 таблетка містить парацетамолу 500 мг, кофеїну 25 мг").
 * Nothing is guessed: an amount is kept only where the registry text states it for that substance.
 */
internal object EastForms {
    private val AMOUNT = """(\d+(?:[.,]\d+)?)\s*(мг|г|мкг|МЕ|ЕД|ОД|МО|мл|%)(?:\s*/\s*(\d+(?:[.,]\d+)?\s*)?(мл|г|доза|дозу))?"""
    private val DOSES = Regex("""^\s*$AMOUNT(?:\s*\+\s*$AMOUNT)*\s*$""")
    private val ONE = Regex(AMOUNT)
    private val DOSE_SEQUENCE = Regex("""(?<![\p{L}\d.,])$AMOUNT(?:\s*\+\s*$AMOUNT)*""")

    /** One release form split into the form words and the per-unit amounts ("400 мг", "25 мг+200 мг"). */
    data class Form(val form: String, val doses: List<Pair<String, String>>, val perVolume: String?, val text: String)

    /**
     * "капсулы, 25 мг+200 мг+2.5 мг+150 мг, 10 шт. - …" -> form "капсулы", doses 25/200/2.5/150 mg.
     * "таблетки, покрытые оболочкой, 400 мг по 6 шт …" -> form "таблетки, покрытые оболочкой", dose 400 mg.
     */
    fun parse(text: String): Form {
        val parts = text.split(", ").map { it.trim() }
        val doseAt = parts.indexOfFirst { p -> DOSES.matches(p.substringBefore(" по ").substringBefore(" - ").trim()) }
        if (doseAt <= 0) {
            // Belarus writes the doses inside the form text: "порошок … со вкусом лимона 325 мг + 10 мг по 22.1 г в …"
            val head = text.substringBefore(" по ").substringBefore(" - ")
            val seq = DOSE_SEQUENCE.find(head) ?: return Form(parts.first().substringBefore(" по ").trim(), emptyList(), null, text)
            val doses = ONE.findAll(seq.value).map { m -> m.groupValues[1].replace(',', '.') to m.groupValues[2] }.toList()
            val per = ONE.find(seq.value)?.let { m -> m.groupValues[4].takeIf { it.isNotEmpty() }?.let { unit -> (m.groupValues[3].trim().ifEmpty { "1" }) + " " + unit } }
            return Form(head.substring(0, seq.range.first).trim().trimEnd(','), doses, per, text)
        }
        val doseText = parts[doseAt].substringBefore(" по ").substringBefore(" - ").trim()
        val doses = ONE.findAll(doseText).map { m -> m.groupValues[1].replace(',', '.') to m.groupValues[2] }.toList()
        val per = ONE.find(doseText)?.let { m -> m.groupValues[4].takeIf { it.isNotEmpty() }?.let { unit -> (m.groupValues[3].trim().ifEmpty { "1" }) + " " + unit } }
        return Form(parts.take(doseAt).joinToString(", "), doses, per, text)
    }

    /**
     * Substances named by the international name list ("Кофеин+Парацетамол+[Аскорбиновая кислота]") paired with
     * the doses in the same order; when the counts differ, the names are kept without amounts.
     */
    fun ingredients(inn: String, doses: List<Pair<String, String>>): List<Ingredient> {
        val names = inn.split('+').map { it.trim().removePrefix("[").removeSuffix("]").trim() }.filter { it.isNotEmpty() }
        if (names.size != doses.size) return names.map { Ingredient(it) }
        return names.zip(doses).map { (name, dose) -> Ingredient(name, dose.first, dose.second) }
    }

    /** The unit a patient counts for this form: tablet, capsule, sachet, suppository, ml (per volume), or null. */
    fun countedUnit(form: Form): String? {
        val f = form.form.lowercase()
        val all = form.text.lowercase()
        return when {
            form.perVolume?.endsWith("мл") == true -> "ml"
            f.startsWith("таблет") || f.startsWith("драже") -> "tablet"
            f.startsWith("капсул") -> "capsule"
            f.startsWith("суппозитор") || f.startsWith("супозитор") -> "suppository"
            f.startsWith("пастил") || f.startsWith("льодяник") -> "lozenge"
            (f.startsWith("порош") || f.startsWith("гранул")) && ("пакет" in all || "саше" in all || "стик" in all || "стік" in all) -> "sachet"
            else -> null
        }
    }

    /** "1 таблетка містить парацетамолу 500 мг, кофеїну 25 мг, …" -> (per unit "1 таблетка", ingredients). */
    fun ukrainianComposition(text: String): Pair<String?, List<Ingredient>> {
        val m = Regex("""^\s*(.{1,60}?)\s+містить:?\s*(.*)$""", RegexOption.DOT_MATCHES_ALL).find(text) ?: return null to emptyList()
        val per = m.groupValues[1].trim()
        val ingredients = splitTopLevel(m.groupValues[2]).mapNotNull { part ->
            val p = part.trim().trimEnd('.', ';')
            if (p.isEmpty()) return@mapNotNull null
            val amount = Regex("""^(.*?)\s+(\d+(?:[.,]\d+)?)\s*(мг|г|мкг|МО|ОД|мл|%)(?![\p{L}])""").find(p) // \b knows no Cyrillic letters
            if (amount != null) Ingredient(amount.groupValues[1].trim(), amount.groupValues[2], amount.groupValues[3],
                p.substring(amount.range.last + 1).trim().trimStart(',', ' ').ifEmpty { null })
            else Ingredient(p)
        }
        return per to ingredients
    }

    /** "1 таблетка" -> tablet, "1 стік-пакетик" / "1 пакет" -> sachet, "5 мл" -> ml. */
    fun ukrainianUnit(per: String?): String? {
        val p = per?.lowercase() ?: return null
        return when {
            Regex("""\d+(?:[.,]\d+)?\s*мл""").containsMatchIn(p) -> "ml"
            "таблет" in p || "драже" in p -> "tablet"
            "капсул" in p -> "capsule"
            "пакет" in p || "саше" in p -> "sachet"
            "супозитор" in p -> "suppository"
            "льодяник" in p || "пастил" in p -> "lozenge"
            else -> null
        }
    }

    /** Splits "a 1 мг, b (x, y) 2 мг" at the commas outside parentheses. */
    private fun splitTopLevel(text: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (c in text) {
            when (c) {
                '(' -> depth++
                ')' -> depth = maxOf(0, depth - 1)
            }
            if (c == ',' && depth == 0) { out += current.toString(); current.setLength(0) } else current.append(c)
        }
        out += current.toString()
        return out
    }
}
