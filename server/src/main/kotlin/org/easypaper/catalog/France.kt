package org.easypaper.catalog

import org.easypaper.model.Ingredient
import org.easypaper.model.MedicineProduct
import org.easypaper.model.RegistrySource
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * France: Base de données publique des médicaments (ANSM/HAS/CNAM), free reuse with the source named
 * (https://base-donnees-publique.medicaments.gouv.fr/telechargement). One product per CIS code, joined to its
 * substances (CIS_COMPO_bdpm.txt) and packs (CIS_CIP_bdpm.txt) by CIS. Files are tab-separated without header.
 */
object France {
    const val REGISTRY = "fr-bdpm"
    const val NAME = "Base de données publique des médicaments (France)"
    const val BASE = "https://base-donnees-publique.medicaments.gouv.fr"
    val FILES = listOf("CIS_bdpm.txt", "CIS_COMPO_bdpm.txt", "CIS_CIP_bdpm.txt")

    fun productUrl(cis: String) = "$BASE/medicament/$cis/extrait"

    /** Builds the products from the three files in [dir]; [dataDate] is the date of the downloaded files. */
    fun read(dir: File, dataDate: String): List<CatalogProduct> {
        val compo = lines(File(dir, "CIS_COMPO_bdpm.txt")).map { it.split('\t') }.filter { it.size >= 8 }.groupBy { it[0].trim() }
        val packs = lines(File(dir, "CIS_CIP_bdpm.txt")).map { it.split('\t') }.filter { it.size >= 7 }.groupBy { it[0].trim() }
        val products = LinkedHashMap<String, CatalogProduct>() // keyed by CIS: a repeated line never makes a second product
        for (line in lines(File(dir, "CIS_bdpm.txt"))) {
            val f = line.split('\t')
            if (f.size < 7) continue
            val cis = f[0].trim()
            val name = f[1].trim()
            val rows = compo[cis].orEmpty()
            val ingredients = ingredients(rows)
            val reference = rows.firstOrNull { it[6].trim() == "SA" }?.get(5)?.trim()?.takeIf { it.isNotEmpty() }
            products[cis] = CatalogProduct(
                id = "$REGISTRY:$cis",
                card = MedicineProduct(
                    name = name,
                    brand = name.substringBefore(',').trim(),
                    form = f[2].trim().ifEmpty { null },
                    ingredients = ingredients,
                    perUnit = reference,
                    doseUnit = reference?.let(::countedUnit),
                    source = RegistrySource(REGISTRY, NAME, "FR", cis, productUrl(cis), dataDate),
                ),
                packs = packs[cis].orEmpty().mapNotNull { p ->
                    val cip13 = p[6].trim()
                    if (cip13.length == 13 && cip13.all(Char::isDigit)) CatalogPack(cip13, p[2].trim()) else null
                }.distinctBy { it.gtin },
                active = f[4].trim().equals("Autorisation active", ignoreCase = true),
            )
        }
        return products.values.toList()
    }

    /**
     * Active substances (SA) in the order of the file; a therapeutic fraction (FT) with the same link number is the
     * active moiety the SA corresponds to, kept as its "équivalant à" note.
     */
    internal fun ingredients(rows: List<List<String>>): List<Ingredient> {
        val byLink = rows.groupBy { it[7].trim() }
        return rows.filter { it[6].trim() == "SA" }.distinctBy { it[3].trim() + "|" + it[7].trim() + "|" + it[1].trim() }.map { sa ->
            val (amount, unit) = splitDosage(sa[4])
            val ft = byLink[sa[7].trim()].orEmpty().firstOrNull { it[6].trim() == "FT" }
            Ingredient(
                name = sa[3].trim().lowercase().replaceFirstChar { it.uppercase() }.let(::frenchName),
                amount = amount, unit = unit,
                equivalent = ft?.let { "équivalant à ${frenchName(it[3].trim().lowercase())} ${it[4].trim()}".trim() },
            )
        }
    }

    /** "500 mg" -> ("500", "mg"); "200 000 UI" -> ("200 000", "UI"); "" -> (null, null). */
    internal fun splitDosage(dosage: String): Pair<String?, String?> {
        val m = Regex("""^\s*([\d][\d\s .,]*)\s*(.*?)\s*$""").find(dosage) ?: return null to dosage.trim().ifEmpty { null }
        return m.groupValues[1].trim() to m.groupValues[2].trim().ifEmpty { null }
    }

    /** Registry names are upper case in the composition file; the card uses lower case like the leaflet. */
    private fun frenchName(name: String) = name.lowercase()

    /** "un sachet" -> "sachet", "une gélule" -> "gélule", "5 ml" -> "ml", "un comprimé" -> "comprimé". */
    internal fun countedUnit(reference: String): String? {
        val r = reference.trim().lowercase()
        Regex("""^(?:un|une)\s+(.+)$""").find(r)?.let { return it.groupValues[1] }
        Regex("""^[\d.,\s]+\s*(ml|g|mg)\b""").find(r)?.let { return it.groupValues[1] }
        return null
    }

    /** The BDPM files mix encodings (UTF-8 and Windows-1252): try UTF-8 strictly first. */
    internal fun lines(file: File): List<String> {
        val bytes = file.readBytes()
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, charset("windows-1252"))
        }
        return text.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.toList()
    }
}
