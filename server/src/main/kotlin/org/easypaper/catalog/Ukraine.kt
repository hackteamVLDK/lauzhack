package org.easypaper.catalog

import org.easypaper.model.MedicineProduct
import org.easypaper.model.RegistrySource
import java.io.File

/**
 * Ukraine: the State Register of Medicinal Products as the State Service of Ukraine on Medicines and Drugs Control
 * publishes it ("reestr.csv", Windows-1251, ';'-separated, one row per registration record; listed as the source of
 * the MoH open-data set on data.gov.ua). The composition per unit is the register's own sentence
 * ("1 таблетка містить …"); the card links to the record's official instruction file.
 */
object Ukraine {
    const val REGISTRY = "ua-drlz"
    const val NAME = "Державний реєстр лікарських засобів України"
    const val CSV_URL = "http://www.drlz.com.ua/ibp/zvity.nsf/all/zvit/\$file/reestr.csv"
    const val PAGE = "https://www.drlz.gov.ua/"

    fun read(csv: File, dataDate: String): List<CatalogProduct> {
        val rows = parseCsv(String(csv.readBytes(), charset("windows-1251")), ';')
        val header = rows.first()
        fun col(name: String) = header.indexOfFirst { it.trim() == name }.also { require(it >= 0) { "No column '$name' in ${csv.name}" } }
        val id = col("ID")
        val name = col("Торгівельне найменування")
        val form = col("Форма випуску")
        val composition = col("Склад (діючі)")
        val group = col("Фармакотерапевтична група")
        val number = col("Номер Реєстраційного посвідчення")
        val ended = col("Дострокове припинення")
        val leaflet = col("URL інструкції")
        val products = LinkedHashMap<String, CatalogProduct>()
        for (r in rows.drop(1)) {
            if (r.size <= maxOf(id, leaflet)) continue
            val recordId = r[id].trim().ifEmpty { continue }
            val formText = r[form].trim()
            // substances (bulk) are registered too; a patient never takes them
            if (formText.contains("субстанці", ignoreCase = true)) continue
            val (perUnit, ingredients) = EastForms.ukrainianComposition(r[composition])
            val trade = r[name].trim()
            products[recordId] = CatalogProduct(
                id = "$REGISTRY:$recordId",
                card = MedicineProduct(
                    name = listOf(trade, formText.substringBefore(';').substringBefore(", по").trim()).filter { it.isNotEmpty() }.joinToString(", "),
                    brand = trade,
                    form = formText.substringBefore(';').substringBefore(", по").trim().ifEmpty { null },
                    ingredients = ingredients,
                    perUnit = perUnit,
                    doseUnit = EastForms.ukrainianUnit(perUnit),
                    indication = r[group].trim().ifEmpty { null },
                    indicationLanguage = "uk",
                    source = RegistrySource(REGISTRY, NAME, "UA", r[number].trim().ifEmpty { recordId },
                        r[leaflet].trim().ifEmpty { PAGE }, dataDate),
                ),
                active = !r[ended].trim().equals("Так", ignoreCase = true),
            )
        }
        return products.values.toList()
    }
}

/** RFC 4180 CSV with quoted fields that may contain the separator, quotes ("") and line breaks. */
internal fun parseCsv(text: String, separator: Char): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
            c == '"' -> quoted = !quoted
            !quoted && c == separator -> { row += field.toString(); field.setLength(0) }
            !quoted && (c == '\n' || c == '\r') -> {
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                row += field.toString(); field.setLength(0)
                if (row.any { it.isNotEmpty() }) rows += row
                row = mutableListOf()
            }
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
    return rows
}
