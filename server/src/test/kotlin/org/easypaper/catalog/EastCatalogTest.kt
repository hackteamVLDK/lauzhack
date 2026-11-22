package org.easypaper.catalog

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ukraine, Russia and Belarus against verbatim rows of their registers (downloaded 2026-11-22: the State Service's
 * reestr.csv, the ГРЛС one-file export of 2026-11-20, rows and one result page of the rceth.by search).
 */
class EastCatalogTest {
    private val fixtures = File(javaClass.getResource("/catalog")!!.toURI())
    private val ukraine = Ukraine.read(File(fixtures, "ua/reestr.csv"), "2026-11-22")
    private val russia = Russia.read(File(fixtures, "ru/grls.zip"), Files.createTempDirectory("grls").toFile()).first
    private val belarus = Belarus.read(File(fixtures, "by/rceth-rows.tsv"), "2026-11-22")

    private fun ingredients(p: CatalogProduct) = p.card.ingredients.map { "${it.name} ${it.amount} ${it.unit}" }

    @Test
    fun ukraineReadsTheCompositionSentencePerUnit() {
        val coldrex = ukraine.first { it.card.brand == "КОЛДРЕКС" }
        assertEquals("1 таблетка", coldrex.card.perUnit)
        assertEquals("tablet", coldrex.card.doseUnit)
        assertEquals(listOf("парацетамолу 500 мг", "кофеїну 25 мг", "фенілефрину гідрохлориду 5 мг", "терпінгідрату 20 мг", "аскорбінової кислоти 30 мг"),
            ingredients(coldrex))
        assertEquals("UA/2675/01/01", coldrex.card.source.recordId)
        assertTrue(coldrex.card.source.url.startsWith("http://www.drlz.com.ua/ibp/lz_www.nsf/id/")) // the record's own instruction
        val sachet = ukraine.first { it.card.brand == "КОЛДРЕКС БЛІЦ" }
        assertEquals("sachet", sachet.card.doseUnit)
        assertEquals("UA", sachet.card.source.country)
    }

    @Test
    fun russiaPairsTheDosesWithTheInternationalNamesInOrder() {
        val hotrem = russia.first { it.card.brand == "Колдрекс ХотРем" }
        assertEquals("sachet", hotrem.card.doseUnit)
        assertEquals(listOf("Парацетамол 750 мг", "Фенилэфрин 10 мг", "Аскорбиновая кислота 60 мг"), ingredients(hotrem))
        assertEquals("ЛП-№(004447)-(РГ-RU)", russia.first { it.card.name.startsWith("Колдрекс ХотРем, порошок для приготовления раствора для приема внутрь, со вкусом лимона и меда") }.card.source.recordId)
        assertEquals("2026-11-20", hotrem.card.source.updated)
        val forte = russia.first { it.card.brand.startsWith("Нурофен") }
        assertEquals("capsule", forte.card.doseUnit)
        assertEquals(listOf("Ибупрофен 400 мг"), ingredients(forte))
        // one version per release form and dose, not one per pack size
        assertEquals(1, russia.count { it.card.brand.startsWith("Нурофен") })
    }

    @Test
    fun belarusFindsTheDosesInsideTheFormTextAndKeepsOneVersionPerDose() {
        val theraflu = belarus.filter { it.card.brand == "ТЕРАФЛЮ" }
        assertEquals(1, theraflu.size, theraflu.map { it.card.name }.toString()) // packs of 1, 2, 3 … sachets are one version
        assertEquals(listOf("парацетамол 325 мг", "фенилэфрин 10 мг", "фенирамин 20 мг", "аскорбиновая кислота 50 мг"), ingredients(theraflu.single()))
        assertEquals("sachet", theraflu.single().card.doseUnit)
        assertTrue(theraflu.single().card.source.url.startsWith("https://www.rceth.by/Refbank/reestr_lekarstvennih_sredstv/details/"))
    }

    @Test
    fun belarusResultColumnsAreFoundByTheirHeaders() {
        val rows = Belarus.rows(File(fixtures, "by/results-page.html").readText())
        assertEquals(5, rows.size)
        assertTrue(rows.first().forms.startsWith("раствор для внутривенного введения"))
        assertEquals("ЛП-№014195-РГ-BY", rows.first().number)
        assertTrue(rows.first().link.startsWith("https://www.rceth.by/Refbank/reestr_lekarstvennih_sredstv/details/3496"))
    }

    @Test
    fun aCyrillicLabelFindsTheVersionInTheCountryOfPurchase() {
        val index = CatalogIndex(ukraine + russia + belarus)
        val ua = resolve(index, CatalogQuery(text = "КОЛДРЕКС\nтаблетки\nпарацетамол 500 мг", country = "UA"))
        assertEquals(ResolutionKind.EXACT, ua.kind)
        assertEquals("UA/2675/01/01", ua.candidates.single().product.card.source.recordId)
        val ru = resolve(index, CatalogQuery(text = "Колдрекс ХотРем\nсо вкусом лимона\n750 мг+10 мг+60 мг", country = "RU"))
        assertTrue(ru.candidates.isNotEmpty() && ru.candidates.all { it.product.card.source.country == "RU" })
        val by = resolve(index, CatalogQuery(text = "Терафлю\nпорошок\n325 мг", country = "BY"))
        assertEquals(ResolutionKind.EXACT, by.kind)
        assertEquals("BY", by.candidates.single().product.card.source.country)
    }
}
