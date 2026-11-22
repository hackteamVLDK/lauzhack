package org.easypaper.catalog

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.easypaper.model.EasyPaperJson
import org.easypaper.module
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 71 against verbatim rows of the official files (server/src/test/resources/catalog: BDPM lines, Swissmedic and
 * EMA rows, Refdata SAI packs, one BDPM product page), downloaded on 2026-11-22.
 */
class CatalogTest {
    private val fixtures = File(javaClass.getResource("/catalog")!!.toURI())
    private val france = France.read(File(fixtures, "fr"), "2026-11-22")
    private val swiss = Switzerland.read(File(fixtures, "ch/zugelassene_packungen_ham.xlsx"), File(fixtures, "ch/SAI-Packungen.XML")).first
    private val index = CatalogIndex(france + swiss)

    private fun resolve(codes: List<String> = emptyList(), text: String = "", country: String? = null) =
        resolve(index, CatalogQuery(codes, text, country))

    @Test
    fun franceJoinsProductsSubstancesAndPacksByCis() {
        val fervex = france.single { it.card.name == "FERVEX ADULTES, granulés pour solution buvable en sachet" }
        assertEquals("fr-bdpm:69329731", fervex.id)
        assertEquals("FERVEX ADULTES", fervex.card.brand)
        assertEquals("granulés pour solution buvable", fervex.card.form)
        assertEquals("un sachet", fervex.card.perUnit)
        assertEquals("sachet", fervex.card.doseUnit)
        assertEquals(
            listOf("acide ascorbique 200 mg", "paracétamol 500 mg", "maléate de phéniramine 25 mg"),
            fervex.card.ingredients.map { "${it.name} ${it.amount} ${it.unit}" },
        )
        assertTrue(fervex.packs.any { it.gtin == "3400930316948" && it.description.startsWith("12 sachet") })
        assertEquals("https://base-donnees-publique.medicaments.gouv.fr/medicament/69329731/extrait", fervex.card.source.url)
        assertEquals("FR", fervex.card.source.country)
    }

    @Test
    fun switzerlandBuildsTheGtinAndReadsTheCompositionPerUnit() {
        assertEquals("7680341860018", Switzerland.gtin("34186", "1"))
        val neocitran = swiss.single { it.id == "ch-swissmedic:47346-2" }
        assertEquals("pro charta", neocitran.card.perUnit)
        assertEquals("sachet", neocitran.card.doseUnit)
        assertEquals(
            listOf("paracetamolum 500 mg", "acidum ascorbicum 50 mg", "pheniramini maleas 20 mg", "phenylephrini hydrochloridum 10 mg"),
            neocitran.card.ingredients.map { "${it.name} ${it.amount} ${it.unit}" },
        )
        // the GTIN built from the Swissmedic numbers is the one Refdata lists, with the name printed on the box
        assertTrue(neocitran.packs.any { it.gtin == "7680473460537" && it.description.startsWith("NEOCITRAN Grippe") })
        assertEquals("https://www.swissmedicinfo.ch/ShowText.aspx?textType=FI&lang=DE&authNr=47346", neocitran.card.source.url)
        assertEquals("2026-10-31", neocitran.card.source.updated)
        assertNotNull(neocitran.card.indication)
    }

    @Test
    fun emaKeepsOnlyNamesSubstancesAndCountryAndLeavesFranceToItsRegistry() {
        val (products, date) = Ema.read(File(fixtures, "eu/article-57-product-data_en.xlsx"))
        assertEquals("2026-11-05", date)
        assertTrue(products.none { it.card.source.country == "FR" })
        val nurofen = products.first { it.card.name == "Nurofen" && it.card.source.country == "DE" }
        assertEquals(listOf("Ibuprofen"), nurofen.card.ingredients.map { it.name })
        assertNull(nurofen.card.ingredients.single().amount)
        assertFalse(nurofen.card.strengthVerified)
    }

    // Scenario 1: a known code gives exactly that pack, from an EAN-13 or a GS1 DataMatrix (01) with leading zero.
    @Test
    fun aKnownCodeGivesTheExactCard() {
        for (code in listOf("3400930316948", "]d2010340093031694817271231101234AB", "(01)03400930316948(17)271231")) {
            val r = resolve(codes = listOf(code))
            assertEquals(ResolutionKind.EXACT, r.kind, code)
            assertEquals("fr-bdpm:69329731", r.candidates.single().product.id)
            assertEquals("3400930316948", r.candidates.single().pack?.gtin)
            assertTrue(r.gtinKnown)
        }
        val swissPack = resolve(codes = listOf("7680341860018"), country = "CH")
        assertEquals("ch-swissmedic:34186-1", swissPack.candidates.single().product.id)
    }

    // Scenario 2: no barcode, the sachet's printed text alone finds the combination with all its substances.
    @Test
    fun aSachetWithoutBarcodeIsFoundByItsPrintedText() {
        val fr = resolve(text = "FERVEX\nADULTES\nparacétamol 500 mg\nacide ascorbique 200 mg\nmaléate de phéniramine 25 mg\n8 sachets", country = "FR")
        assertEquals(ResolutionKind.EXACT, fr.kind)
        assertEquals("fr-bdpm:69329731", fr.candidates.single().product.id)
        assertEquals(3, fr.candidates.single().product.card.ingredients.size)

        val ch = resolve(text = "NeoCitran\nGrippe / Erkältung\nfür Erwachsene\nParacetamol 500 mg\n12 Beutel", country = "CH")
        assertEquals(ResolutionKind.EXACT, ch.kind)
        assertEquals("ch-swissmedic:47346-2", ch.candidates.single().product.id) // not the FORTE version
        assertEquals(4, ch.candidates.single().product.card.ingredients.size)
    }

    @Test
    fun aVariantWordOnTheBoxIsNeverDropped() {
        val forte = resolve(text = "NEOCITRAN Grippe Erkältung FORTE", country = "CH")
        assertTrue(forte.candidates.isNotEmpty())
        assertTrue(forte.candidates.all { "FORTE" in it.product.card.name.uppercase() })
        val sansSucre = resolve(text = "FERVEX ADULTES SANS SUCRE 500 mg", country = "FR")
        assertEquals("fr-bdpm:66947007", sansSucre.candidates.single().product.id)
    }

    // Scenario 3: the name is readable, the strength is cut off: never choose a strength, ask.
    @Test
    fun aBlisterWithoutReadableStrengthAsksInsteadOfChoosing() {
        val r = resolve(text = "DOLIPRANE\nparacétamol\nLot 4512", country = "FR")
        assertEquals(ResolutionKind.CANDIDATES, r.kind)
        assertEquals(ResolutionQuestion.WHICH_STRENGTH, r.question)
        val strengths = r.candidates.flatMap { it.differences }.toSet()
        assertTrue("500 mg" in strengths && "1000 mg" in strengths, strengths.toString())
    }

    // Scenario 4: a valid code no registry knows: the search goes on with the printed text.
    @Test
    fun anUnknownCodeFallsBackToThePrintedText() {
        val r = resolve(codes = listOf("4006381333931"), text = "DAFALGAN Filmtabl 1 g", country = "CH")
        assertEquals(ResolutionKind.EXACT, r.kind)
        assertEquals("ch-swissmedic:56318-2", r.candidates.single().product.id)
        assertEquals("4006381333931", r.gtin)
        assertFalse(r.gtinKnown)
    }

    @Test
    fun aCodeThatContradictsTheLabelAsksForANewPhoto() {
        val dafalgan500 = swiss.single { it.id == "ch-swissmedic:69266-1" }.packs.first().gtin
        val r = resolve(codes = listOf(dafalgan500), text = "DAFALGAN Filmtabletten 1 g", country = "CH")
        assertEquals(ResolutionKind.CONFLICT, r.kind)
        assertEquals(ResolutionQuestion.NEW_PHOTO, r.question)
    }

    @Test
    fun tooLittleOnThePhotoAsksForTheFoilOrTheBox() {
        val r = resolve(text = "Tabl\n10", country = "FR")
        assertEquals(ResolutionKind.TOO_LITTLE, r.kind)
        assertEquals(ResolutionQuestion.PHOTO_PACKAGE, r.question)
    }

    @Test
    fun theIndicationIsQuotedFromTheOfficialPage() {
        val html = File(fixtures, "fr/medicament-69738404-extrait.html").readText()
        assertEquals(
            "Ce médicament est indiqué chez l’adulte et l’enfant à partir de 50 kg (environ 15 ans) dans le traitement au cours " +
                "des rhumes, rhinites, rhinopharyngites et des états grippaux.",
            frenchIndication(html),
        )
    }

    @Test
    fun importingTwiceKeepsOneProductPerRecordAndABrokenSourceDoesNotStopTheOthers() {
        val dir = Files.createTempDirectory("catalog").toFile()
        val store = CatalogStore(dir)
        val info = SourceInfo(France.REGISTRY, France.NAME, listOf("FR"), France.BASE, "2026-11-22", "2026-11-22T00:00:00Z", 0, "")
        store.write(SourceSnapshot(info, france + france)) // a repeated record
        store.write(SourceSnapshot(info, france)) // and a second run
        File(dir, "broken.json.gz").writeText("not gzip")
        val service = CatalogService(store, indications = null)
        assertEquals(france.size, service.index.products.size)
        assertTrue(service.status.any { it.registry == France.REGISTRY && it.ok })
        assertTrue(service.status.any { !it.ok })
        assertEquals(ResolutionKind.EXACT, service.resolve(CatalogQuery(listOf("3400930316948"))).kind)
        dir.deleteRecursively()
    }

    @Test
    fun theRouteAnswersWithTheResolutionAndTheCoverage() = testApplication {
        val dir = Files.createTempDirectory("catalog").toFile()
        val store = CatalogStore(dir)
        store.write(SourceSnapshot(SourceInfo(France.REGISTRY, France.NAME, listOf("FR"), France.BASE, "2026-11-22", "", 0, ""), france))
        application { module(catalog = CatalogService(store, indications = null)) }
        val body = EasyPaperJson.encodeToString(CatalogQuery.serializer(), CatalogQuery(listOf("3400930316948")))
        val response = client.post("/catalog/resolve") { setBody(body) }
        assertEquals(HttpStatusCode.OK, response.status)
        val r = EasyPaperJson.decodeFromString(Resolution.serializer(), response.bodyAsText())
        assertEquals("fr-bdpm:69329731", r.candidates.single().product.id)
        val coverage = EasyPaperJson.decodeFromString(Coverage.serializer(), client.get("/catalog/coverage").bodyAsText())
        assertTrue(coverage.notCovered.isEmpty())
        assertEquals(listOf(France.REGISTRY), coverage.sources.map { it.registry })
        assertEquals(400, client.post("/catalog/resolve") { setBody("{") }.status.value)
        dir.deleteRecursively()
    }

    @Test
    fun aVariantWordIsNeverTakenForABrand() {
        val (ema, _) = Ema.read(File(fixtures, "eu/article-57-product-data_en.xlsx"))
        // a real EMA name that starts with a variant word (Germany, 2026-11-05 file)
        val other = ema.first().let { it.copy(id = "eu-ema-art57:junior", card = it.card.copy(name = "Junior Schokoladengeschmack", brand = "Junior Schokoladengeschmack",
            source = it.card.source.copy(country = "DE"))) }
        val r = resolve(CatalogIndex(ema + other), CatalogQuery(text = "Nurofen Junior", country = "DE"))
        assertTrue(r.candidates.isNotEmpty())
        assertTrue(r.candidates.all { it.product.card.name.startsWith("Nurofen Junior") }, r.candidates.map { it.product.card.name }.toString())
        assertTrue(r.candidates.none { it.product.card.strengthVerified }) // EU list: substances without amounts
    }

    // review of #71: behind Caddy each client has its own limit, and a large body is refused before it is read
    @Test
    fun theLimitIsPerForwardedClientAndLargeBodiesAreRefused() = testApplication {
        val dir = Files.createTempDirectory("catalog").toFile()
        val store = CatalogStore(dir)
        store.write(SourceSnapshot(SourceInfo(France.REGISTRY, France.NAME, listOf("FR"), France.BASE, "2026-11-22", "", 0, ""), france))
        application { module(catalog = CatalogService(store, indications = null)) }
        val body = EasyPaperJson.encodeToString(CatalogQuery.serializer(), CatalogQuery(listOf("3400930316948")))
        repeat(60) { assertEquals(HttpStatusCode.OK, client.post("/catalog/resolve") { header("X-Forwarded-For", "203.0.113.7"); setBody(body) }.status) }
        assertEquals(HttpStatusCode.TooManyRequests, client.post("/catalog/resolve") { header("X-Forwarded-For", "203.0.113.7"); setBody(body) }.status)
        assertEquals(HttpStatusCode.OK, client.post("/catalog/resolve") { header("X-Forwarded-For", "198.51.100.2"); setBody(body) }.status)
        val big = "{\"text\":\"" + "x".repeat(20_000) + "\"}"
        assertEquals(HttpStatusCode.PayloadTooLarge, client.post("/catalog/resolve") { header("X-Forwarded-For", "198.51.100.3"); setBody(big) }.status)
        dir.deleteRecursively()
    }
}
