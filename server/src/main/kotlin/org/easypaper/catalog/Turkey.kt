package org.easypaper.catalog

import org.easypaper.model.Ingredient
import org.easypaper.model.MedicineProduct
import org.easypaper.model.RegistrySource
import java.io.File
import java.net.URLEncoder

/**
 * Turkey: TİTCK's weekly "Ruhsatlı Beşeri Tıbbi Ürünler Listesi" (licensed human medicinal products, xlsx, first
 * sheet): one row per pack with its barcode (GTIN 869…), the product name with strength, form and pack
 * ("MAJEZIK 100 MG FILM TABLET, 15 TABLET"), active substances ("spironolakton+hidroklorotiyazit"), ATC, license
 * number and suspension status. A version is the license number with the name without its pack; the strength per
 * unit is read from that official name and paired with the substances in order. The card links to the agency's
 * KÜB/KT (SmPC / leaflet) search for the product.
 */
object Turkey {
    const val REGISTRY = "tr-titck"
    const val NAME = "TİTCK Ruhsatlı Beşeri Tıbbi Ürünler Listesi"
    const val PAGE = "https://www.titck.gov.tr/dinamikmodul/85"

    fun leafletUrl(name: String) = "https://www.titck.gov.tr/kubkt?search=" + URLEncoder.encode(name, Charsets.UTF_8)

    private val AMOUNT = Regex("""(\d+(?:[.,]\d+)?)\s*(MG|MCG|µG|G|IU|IU|ML|%)(?![\p{L}])""", RegexOption.IGNORE_CASE)

    /** Products and the list date from the file name ("…Listesi13.11.2026_….xlsx") or [fallbackDate]. */
    fun read(xlsx: File, fallbackDate: String): Pair<List<CatalogProduct>, String> {
        val date = Regex("""(\d{2})\.(\d{2})\.(\d{4})""").find(xlsx.name)?.let { "${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}" } ?: fallbackDate
        var header: Map<String, String>? = null
        val versions = LinkedHashMap<String, CatalogProduct>()
        readXlsxRows(xlsx) { _, cells ->
            val h = header
            if (h == null) {
                if (cells.values.any { it.trim() == "BARKOD" }) header = cells
                return@readXlsxRows
            }
            fun col(name: String) = h.entries.firstOrNull { it.value.trim().startsWith(name) }?.key?.let { cells[it]?.trim() }
            val barcode = col("BARKOD") ?: return@readXlsxRows
            val full = col("ÜRÜN ADI") ?: return@readXlsxRows
            if (barcode.length != 13 || !barcode.all(Char::isDigit)) return@readXlsxRows
            val license = col("RUHSAT NUMARASI").orEmpty()
            val (name, _) = splitPack(full)
            val suspended = h.entries.firstOrNull { it.value.startsWith("RUHSATI ASKIDA") }?.key?.let { cells[it]?.trim() }.let { it != null && it != "0" }
            val key = "$REGISTRY:" + (license.ifEmpty { barcode }) + "|" + name
            val existing = versions[key]
            val pack = CatalogPack(barcode, full)
            if (existing != null) {
                versions[key] = existing.copy(packs = (existing.packs + pack).distinctBy { it.gtin })
                return@readXlsxRows
            }
            val doses = AMOUNT.findAll(percentFirst(name)).map { it.groupValues[1].replace(',', '.') to it.groupValues[2].uppercase() }.toList()
            val substances = col("ETKİN MADDE").orEmpty().split('+').map { it.trim() }.filter { it.isNotEmpty() }
            val ingredients = if (substances.size == doses.size) substances.zip(doses).map { (s, d) -> Ingredient(s, d.first, d.second.lowercase()) }
                else substances.map { Ingredient(it) }
            val form = formOf(name)
            versions[key] = CatalogProduct(
                id = key,
                card = MedicineProduct(
                    name = name,
                    brand = name.split(' ').takeWhile { w -> w.none(Char::isDigit) && w != "%" }.joinToString(" ").ifEmpty { name },
                    form = form,
                    ingredients = ingredients,
                    perUnit = form,
                    doseUnit = countedUnit(name),
                    source = RegistrySource(REGISTRY, NAME, "TR", license.ifEmpty { barcode }, leafletUrl(name), date),
                ),
                packs = listOf(pack),
                active = !suspended,
            )
        }
        return versions.values.toList() to date
    }

    /** "MAJEZIK % 0.25 ORAL SPREY, ÇÖZELTİ, 30 ML" -> ("MAJEZIK % 0.25 ORAL SPREY, ÇÖZELTİ", "30 ML"). */
    internal fun splitPack(full: String): Pair<String, String?> {
        val i = full.lastIndexOf(", ")
        if (i < 0) return full to null
        val tail = full.substring(i + 2)
        return if (tail.firstOrNull()?.isDigit() == true) full.substring(0, i).trim() to tail else full to null
    }

    /** The form words after the last strength: "FILM TABLET", "ORAL SPREY, ÇÖZELTİ". */
    internal fun formOf(name: String): String? {
        val text = percentFirst(name)
        val last = AMOUNT.findAll(text).lastOrNull() ?: return null
        return text.substring(last.range.last + 1).trim().trimStart('/', ',').trim().ifEmpty { null }
    }

    /** Turkish names put a percentage first ("MAJEZIK % 0.25 GARGARA"): read it as "0.25 %". */
    internal fun percentFirst(name: String): String = name.replace(Regex("""%\s*(\d+(?:[.,]\d+)?)""")) { "${it.groupValues[1]} %" }

    /** The unit a patient counts: tablet, capsule, sachet, suppository, ml (liquid per volume); null otherwise. */
    internal fun countedUnit(name: String): String? {
        val n = foldForSearch(name)
        return when {
            Regex("""MG\s*/\s*\d*[.,]?\d*\s*ML""").containsMatchIn(n) -> "ml"
            "TABLET" in n || "DRAJE" in n -> "tablet"
            "KAPSUL" in n -> "capsule"
            "SASE" in n -> "sachet"
            "SUPOZITUAR" in n -> "suppository"
            "PASTIL" in n -> "lozenge"
            else -> null
        }
    }
}
