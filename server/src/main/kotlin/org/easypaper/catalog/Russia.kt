package org.easypaper.catalog

import org.easypaper.model.MedicineProduct
import org.easypaper.model.RegistrySource
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Russia: the State Register of Medicines (ГРЛС, Ministry of Health), the one-file export linked on
 * grls.rosminzdrav.ru ("Загрузите ГРЛС одним файлом", a zip of xlsx lists). The current lists are read: valid
 * registrations, EAEU registrations, foreign packs, pending confirmation. One version per release form with its
 * doses ("капсулы, 25 мг+200 мг+2.5 мг+150 мг"); doses are paired with the international names in their order.
 * The export carries no barcodes and no per-record link: the card names the registration number and links to the
 * register's search page.
 */
object Russia {
    const val REGISTRY = "ru-grls"
    const val NAME = "Государственный реестр лекарственных средств (ГРЛС)"
    const val PAGE = "https://grls.rosminzdrav.ru/grls.aspx"
    private val CURRENT = listOf("Действующий", "ЕАЭС", "иностранных_упаковках", "на_подтверждении")

    /** Reads the zip [export]; returns the products and the "по состоянию на" date. */
    fun read(export: File, work: File): Pair<List<CatalogProduct>, String> {
        work.mkdirs()
        var date = ""
        val products = LinkedHashMap<String, CatalogProduct>()
        ZipFile(export).use { zip ->
            for (entry in zip.entries().toList()) {
                val name = entry.name
                if (!name.endsWith(".xlsx") || CURRENT.none { it in name }) continue
                val file = File(work, "grls-${CURRENT.first { it in name }}.xlsx")
                zip.getInputStream(entry).use { input -> file.outputStream().use { input.copyTo(it) } }
                var header = false
                readXlsxRows(file) { _, cells ->
                    Regex("""по состоянию на (\d{2})\.(\d{2})\.(\d{4})""").find(cells.values.joinToString(" "))
                        ?.let { m -> date = "${m.groupValues[3]}-${m.groupValues[2]}-${m.groupValues[1]}" }
                    if (!header) { header = cells["C"]?.startsWith("Номер регистрационного") == true; return@readXlsxRows }
                    val number = cells["C"]?.trim().orEmpty()
                    val trade = cells["I"]?.trim().orEmpty()
                    if (number.isEmpty() || trade.isEmpty()) return@readXlsxRows
                    for (release in cells["K"].orEmpty().split(";").map { it.trim() }.filter { it.isNotEmpty() }) {
                        val text = release.substringBefore(" - Без рецепта").substringBefore(" - По рецепту").trim()
                        if (text.isEmpty() || text.startsWith("субстанция", ignoreCase = true)) continue
                        val form = EastForms.parse(text)
                        val key = sha("$number|${form.form}|${form.doses}")
                        products.getOrPut("$REGISTRY:$key") {
                            val dose = form.doses.joinToString("+") { "${it.first} ${it.second}" }
                            CatalogProduct(
                                id = "$REGISTRY:$key",
                                card = MedicineProduct(
                                    name = listOf(trade, form.form, dose).filter { it.isNotEmpty() }.joinToString(", "),
                                    brand = trade,
                                    form = form.form,
                                    ingredients = EastForms.ingredients(cells["J"].orEmpty(), form.doses),
                                    perUnit = form.perVolume ?: form.form,
                                    doseUnit = EastForms.countedUnit(form),
                                    indication = cells["N"]?.trim()?.ifEmpty { null },
                                    indicationLanguage = "ru",
                                    source = RegistrySource(REGISTRY, NAME, "RU", number, PAGE, date),
                                ),
                            )
                        }
                    }
                }
            }
        }
        return products.values.map { p -> p.copy(card = p.card.copy(source = p.card.source.copy(updated = date))) } to date
    }

    private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(10).joinToString("") { "%02x".format(it) }
}
