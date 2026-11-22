package org.easypaper.catalog

import org.easypaper.model.Ingredient
import org.easypaper.model.MedicineProduct
import org.easypaper.model.RegistrySource
import java.io.File
import javax.xml.stream.XMLStreamConstants

/**
 * Switzerland. Composition, indication field and packs come from Swissmedic's official list of authorised human
 * medicine packs ("Zugelassene Packungen", xlsx, public); the pack names printed on the boxes and the GTINs in
 * trade come from the Refdata SAI monthly export (SAI-Packungen.XML). The card links to the official product
 * information on swissmedicinfo (AIPS) by authorisation number.
 */
object Switzerland {
    const val REGISTRY = "ch-swissmedic"
    const val NAME = "Swissmedic – zugelassene Packungen; Refdata SAI"
    const val PACKS_URL = "https://www.swissmedic.ch/dam/swissmedic/de/dokumente/internetlisten/zugelassene_packungen_human.xlsx.download.xlsx/zugelassene_packungen_ham.xlsx"
    const val SAI_DOWNLOAD_PAGE = "https://sai.refdata.ch/en/download"

    /** Official professional information (Fachinformation) for one authorisation, on AIPS / swissmedicinfo. */
    fun infoUrl(authorisation: String) = "https://www.swissmedicinfo.ch/ShowText.aspx?textType=FI&lang=DE&authNr=$authorisation"

    /** One Refdata article: GTIN and the German / French names printed on the box. */
    data class SaiPack(val gtin: String, val nameDe: String, val nameFr: String)

    /**
     * Reads the Swissmedic list [packsXlsx] and, when present, the SAI export [saiPacks] (SAI-Packungen.XML).
     * Returns the products and the "Stand" date of the Swissmedic list.
     */
    fun read(packsXlsx: File, saiPacks: File?): Pair<List<CatalogProduct>, String> {
        val sai = saiPacks?.takeIf { it.isFile }?.let(::readSai).orEmpty()
        var stand = ""
        var header: Map<String, String>? = null
        val rows = mutableListOf<Map<String, String>>()
        readXlsxRows(packsXlsx) { _, cells ->
            val first = cells.values.firstOrNull().orEmpty()
            Regex("""Stand\s*/\s*Etat au:\s*(\d{2})\.(\d{2})\.(\d{4})""").find(cells.values.joinToString(" "))?.let { m ->
                stand = "${m.groupValues[3]}-${m.groupValues[2]}-${m.groupValues[1]}"
            }
            when {
                header == null && cells["A"].orEmpty().startsWith("Zulassungs") -> header = cells
                header != null && cells["A"]?.all(Char::isDigit) == true && first.isNotEmpty() -> rows += cells
            }
        }
        val products = rows.groupBy { it.getValue("A") to it["B"].orEmpty() }.map { (key, packRows) ->
            val (authorisation, dosage) = key
            val row = packRows.first()
            val officialName = row["C"].orEmpty().trim()
            val composition = row["R"].orEmpty()
            val perUnit = perUnit(composition)
            val packs = packRows.mapNotNull { p ->
                val gtin = gtin(authorisation, p["K"] ?: return@mapNotNull null) ?: return@mapNotNull null
                CatalogPack(gtin, sai[gtin]?.nameDe ?: listOfNotNull(p["L"], p["M"]).joinToString(" "))
            }
            CatalogProduct(
                id = "$REGISTRY:$authorisation-$dosage",
                card = MedicineProduct(
                    name = officialName,
                    brand = officialName.substringBefore(',').trim(),
                    form = officialName.substringAfter(',', "").trim().ifEmpty { null },
                    ingredients = ingredients(row["Q"].orEmpty(), composition),
                    perUnit = perUnit,
                    doseUnit = perUnit?.let(::countedUnit),
                    indication = (row["U"]?.takeIf { it.isNotBlank() } ?: row["T"])?.trim()?.ifEmpty { null },
                    indicationLanguage = null, // the registry field is written in German, French or Italian
                    source = RegistrySource(REGISTRY, NAME, "CH", "$authorisation-$dosage", infoUrl(authorisation), stand),
                ),
                packs = packs.distinctBy { it.gtin },
                otherNames = packs.flatMap { p -> listOfNotNull(sai[p.gtin]?.nameDe, sai[p.gtin]?.nameFr) }.distinct(),
            )
        }
        return products to stand
    }

    /** GTIN of a Swiss pack: 7680 + authorisation (5 digits) + pack code (3 digits) + GS1 check digit. */
    fun gtin(authorisation: String, packCode: String): String? {
        val a = authorisation.trim().toIntOrNull() ?: return null
        val p = packCode.trim().toIntOrNull() ?: return null
        if (a > 99999 || p > 999) return null
        val body = "7680" + a.toString().padStart(5, '0') + p.toString().padStart(3, '0')
        return body + checkDigit(body)
    }

    internal fun checkDigit(body: String): Int {
        val sum = body.reversed().withIndex().sumOf { (i, c) -> (c - '0') * if (i % 2 == 0) 3 else 1 }
        return (10 - sum % 10) % 10
    }

    /**
     * The amount of each active substance (column "Wirkstoff(e)") as written in the composition text:
     * "paracetamolum 500 mg, …" and an optional "corresp. …" right after it.
     */
    internal fun ingredients(actives: String, composition: String): List<Ingredient> =
        actives.split(", ").map { it.trim() }.filter { it.isNotEmpty() }.distinct().map { name ->
            val m = Regex(Regex.escape(name) + """\s+(\d+(?:[.,]\d+)?(?:\s\d{3})*)\s*(mg|g|µg|mcg|ng|ml|U\.\s?I\.|I\.\s?E\.|Mio\.\s?U\.\s?I\.|UI|IE|kBq|MBq|%)(?:\s+corresp\.\s+([^,]+?)(?=,|\s+pro\s|$))?""",
                RegexOption.IGNORE_CASE).find(composition)
            Ingredient(name, m?.groupValues?.get(1), m?.groupValues?.get(2), m?.groupValues?.get(3)?.trim()?.takeIf { it.isNotEmpty() }?.let { "corresp. $it" })
        }

    /** "… pro compresso corresp. natrium 1.58 mg." -> "pro compresso"; "… ad solutionem pro 5 ml" -> "pro 5 ml". */
    internal fun perUnit(composition: String): String? =
        Regex("""\bpro\s+((?:\d+(?:[.,]\d+)?\s*(?:ml|g))|[\p{L}]+(?:\s+[\p{L}]+)?)""").findAll(composition)
            .map { "pro " + it.groupValues[1].trim() }
            .firstOrNull { !it.startsWith("pro usu") }
            ?.replace(Regex("""\s+(corresp|et|ad)$"""), "")

    /** The unit a patient counts: tablet, capsule, sachet, suppository, ml, dose; null when the text says otherwise. */
    internal fun countedUnit(perUnit: String): String? {
        val w = perUnit.removePrefix("pro ").lowercase()
        Regex("""^\d+(?:[.,]\d+)?\s*(ml|g)$""").find(w)?.let { return it.groupValues[1] }
        return when {
            w.startsWith("compress") -> "tablet"
            w.startsWith("capsul") -> "capsule"
            w.startsWith("charta") || w.startsWith("sacc") -> "sachet"
            w.startsWith("suppositor") -> "suppository"
            w.startsWith("dosi") -> "dose"
            w.startsWith("pastill") -> "lozenge"
            else -> null
        }
    }

    /** GTIN -> names from SAI-Packungen.XML, streamed (the file is ~35 MB). */
    fun readSai(file: File): Map<String, SaiPack> {
        val out = HashMap<String, SaiPack>()
        file.inputStream().buffered().use { input ->
            val xml = factory().createXMLStreamReader(input)
            var field = ""
            val values = HashMap<String, String>()
            while (xml.hasNext()) {
                when (xml.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        if (xml.localName == "PACKUNG") values.clear()
                        field = xml.localName
                    }
                    XMLStreamConstants.CHARACTERS -> if (field.isNotEmpty()) values.merge(field, xml.text) { a, b -> a + b }
                    XMLStreamConstants.END_ELEMENT -> {
                        if (xml.localName == "PACKUNG") {
                            val gtin = values["GTIN_INDUSTRY"]?.trim()
                            if (gtin != null && gtin.length == 13 && gtin.all(Char::isDigit)) {
                                out[gtin] = SaiPack(gtin, values["BESCHREIBUNG_DE_REFDATA"]?.trim().orEmpty(), values["BESCHREIBUNG_FR_REFDATA"]?.trim().orEmpty())
                            }
                        }
                        field = ""
                    }
                }
            }
            xml.close()
        }
        return out.filterValues { it.nameDe.isNotEmpty() || it.nameFr.isNotEmpty() }
    }
}
