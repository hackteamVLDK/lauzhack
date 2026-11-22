package org.easypaper.catalog

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Turkey (task 85) against verbatim rows of TİTCK's licensed products list of 13.11.2026. */
class TurkeyTest {
    private val fixtures = File(javaClass.getResource("/catalog/tr")!!.toURI())
    private val list = fixtures.listFiles()!!.single { it.name.endsWith(".xlsx") }
    private val read = Turkey.read(list, "2026-01-01")
    private val turkey = read.first
    private val date = read.second
    private fun ingredients(p: CatalogProduct) = p.card.ingredients.map { "${it.name} ${it.amount} ${it.unit}" }

    @Test
    fun packsOfOneLicenseAndStrengthAreOneVersionWithTheirBarcodes() {
        assertEquals("2026-11-13", date)
        val majezik = turkey.single { it.card.name == "MAJEZIK 100 MG FILM TABLET" }
        assertEquals(listOf("8699536090115", "8699536090122"), majezik.packs.map { it.gtin })
        assertEquals(listOf("flurbiprofen 100 mg"), ingredients(majezik))
        assertEquals("tablet", majezik.card.doseUnit)
        assertEquals("TR", majezik.card.source.country)
        assertEquals("175/73", majezik.card.source.recordId)
        assertTrue(majezik.card.source.url.startsWith("https://www.titck.gov.tr/kubkt?search=MAJEZIK"))
    }

    @Test
    fun combinationsPairTheStrengthsWithTheSubstancesInOrder() {
        val aktazid = turkey.single { it.card.name == "AKTAZİD 25 MG/25 MG FİLM KAPLI TABLET" }
        assertEquals(listOf("spironolakton 25 mg", "hidroklorotiyazit 25 mg"), ingredients(aktazid))
    }

    @Test
    fun aPercentageWrittenFirstIsRead() {
        val gargle = turkey.single { it.card.name == "MAJEZIK % 0.25 GARGARA" }
        assertEquals(listOf("flurbiprofen 0.25 %"), ingredients(gargle))
        assertEquals("GARGARA", gargle.card.form)
        assertNull(gargle.card.doseUnit)
    }

    @Test
    fun aSuspendedLicenseIsKeptButMarked() {
        val suspended = turkey.single { it.card.name.startsWith("ABSTRAL") }
        assertFalse(suspended.active)
    }

    @Test
    fun aTurkishBarcodeFindsTheExactPack() {
        val r = resolve(CatalogIndex(turkey), CatalogQuery(codes = listOf("]d2010869953609011517280131"), country = "TR"))
        assertEquals(ResolutionKind.EXACT, r.kind)
        assertEquals("MAJEZIK 100 MG FILM TABLET", r.candidates.single().product.card.name)
        assertEquals("8699536090115", r.candidates.single().pack?.gtin)
        // a label without the strength asks which one
        val label = resolve(CatalogIndex(turkey), CatalogQuery(text = "MAJEZIK\nflurbiprofen\nfilm tablet", country = "TR"))
        assertEquals(ResolutionKind.CANDIDATES, label.kind)
        assertEquals(ResolutionQuestion.WHICH_STRENGTH, label.question)
    }
}
