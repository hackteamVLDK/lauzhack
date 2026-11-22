package org.easypaper.catalog

import org.easypaper.model.Ingredient
import org.easypaper.model.MedicineProduct
import org.easypaper.model.RegistrySource
import java.io.File
import java.security.MessageDigest

/**
 * EU/EEA: EMA's public Article 57 product list (xlsx). It names products, their active substances and the country
 * of authorisation, but not strengths per unit, so its cards are marked strength-not-verified and never complete
 * a composition. Countries with a national integration (France) are left to that registry.
 */
object Ema {
    const val REGISTRY = "eu-ema-art57"
    const val NAME = "EMA Article 57 product data"
    const val URL = "https://www.ema.europa.eu/en/documents/other/article-57-product-data_en.xlsx"
    const val PAGE = "https://www.ema.europa.eu/en/human-regulatory-overview/post-authorisation/data-medicines-iso-idmp-standards-post-authorisation/public-data-article-57-database"
    private val NATIONAL = setOf("FR")

    fun read(xlsx: File): Pair<List<CatalogProduct>, String> {
        var dataDate = ""
        var started = false
        val products = LinkedHashMap<String, CatalogProduct>()
        readXlsxRows(xlsx) { _, cells ->
            val a = cells["A"].orEmpty()
            Regex("""on (\d{2})/(\d{2})/(\d{4})""").find(a)?.let { m -> dataDate = "${m.groupValues[3]}-${m.groupValues[2]}-${m.groupValues[1]}" }
            if (!started) { started = a.startsWith("Product name"); return@readXlsxRows }
            val name = a.trim()
            val substances = cells["B"].orEmpty().trim()
            val country = COUNTRIES[cells["D"].orEmpty().trim()] ?: return@readXlsxRows
            if (name.isEmpty() || country in NATIONAL) return@readXlsxRows
            val route = cells["C"].orEmpty().trim()
            // no record id in the file: the row's identifying fields, hashed, keep re-imports free of duplicates
            val key = sha("$name|$substances|$route|$country|${cells["E"].orEmpty().trim()}")
            products.getOrPut(key) {
                CatalogProduct(
                    id = "$REGISTRY:$key",
                    card = MedicineProduct(
                        name = name, brand = name, form = route.ifEmpty { null },
                        ingredients = substances.split('|', ',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().map { Ingredient(it) },
                        source = RegistrySource(REGISTRY, NAME, country, key, PAGE, dataDate),
                        strengthVerified = false,
                    ),
                )
            }
        }
        return products.values.toList() to dataDate
    }

    private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(10)
        .joinToString("") { "%02x".format(it) }

    /** Country names as EMA writes them -> ISO 3166-1 alpha-2. */
    private val COUNTRIES = mapOf(
        "Austria" to "AT", "Belgium" to "BE", "Bulgaria" to "BG", "Croatia" to "HR", "Cyprus" to "CY", "Czech Republic" to "CZ",
        "Czechia" to "CZ", "Denmark" to "DK", "Estonia" to "EE", "Finland" to "FI", "France" to "FR", "Germany" to "DE",
        "Greece" to "GR", "Hungary" to "HU", "Iceland" to "IS", "Ireland" to "IE", "Italy" to "IT", "Latvia" to "LV",
        "Liechtenstein" to "LI", "Lithuania" to "LT", "Luxembourg" to "LU", "Malta" to "MT", "Netherlands" to "NL",
        "Norway" to "NO", "Poland" to "PL", "Portugal" to "PT", "Romania" to "RO", "Slovakia" to "SK", "Slovenia" to "SI",
        "Spain" to "ES", "Sweden" to "SE", "United Kingdom (Northern Ireland)" to "GB-NIR", "European Union" to "EU",
    )
}
