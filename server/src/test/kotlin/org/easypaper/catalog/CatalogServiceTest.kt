package org.easypaper.catalog

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.easypaper.model.EasyPaperJson
import org.easypaper.module
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Small synthetic snapshots exercise the same store, index and route as imported registries, without downloads. */
class CatalogServiceTest {
    @Test
    fun ukrainianSnapshotsMatchBothScriptsWithoutACountry() = withCatalog(listOf(snapshot("ua"))) { service ->
        val labels = mapOf(
            "НУРОФЄН 200 мг таблетки ібупрофен" to "nurofen",
            "Нурофен 200 мг ибупрофен" to "nurofen",
            "Nurofen 200 mg" to "nurofen",
            "ibuprofen 200 mg" to "nurofen",
            "ибупрофен 200 мг" to "nurofen",
            "Цитрамон-Дарниця таблетки" to "citramon",
            "ПАРАЦЕТАМОЛ-ДАРНИЦЯ 500 мг" to "paracetamol",
            "paracetamol 500 mg" to "paracetamol",
            "парацетамол 0,5 г" to "paracetamol",
            "Анальгін 500 мг" to "analgin",
        )
        for ((text, id) in labels) {
            val result = service.resolve(CatalogQuery(text = text))
            assertEquals(ResolutionKind.EXACT, result.kind, text)
            assertEquals("ua-drlz:$id", result.candidates.single().product.id, text)
            assertEquals("UA", result.candidates.single().product.card.source.country)
        }
    }

    @Test
    fun latinRegistryBrandsAreAlsoFoundFromCyrillic() {
        val ua = snapshot("ua")
        val latin = ua.products.first().let { p -> p.copy(card = p.card.copy(name = "NUROFEN 200 mg", brand = "NUROFEN")) }
        withCatalog(listOf(ua.copy(products = listOf(latin)))) { service ->
            assertEquals(latin.id, service.resolve(CatalogQuery(text = "НУРОФЄН 200 мг")).candidates.single().product.id)
        }
    }

    private fun snapshot(country: String): SourceSnapshot = EasyPaperJson.decodeFromString(
        SourceSnapshot.serializer(), javaClass.getResource("/catalog/recognition/$country.json")!!.readText(),
    )

    private val snapshots = listOf(snapshot("tr"), snapshot("ch"))
    private val products = snapshots.flatMap { it.products }

    private fun withCatalog(snapshots: List<SourceSnapshot> = this.snapshots, check: (CatalogService) -> Unit) {
        val dir = Files.createTempDirectory("recognition-catalog").toFile()
        try {
            val store = CatalogStore(dir)
            snapshots.forEach(store::write)
            check(CatalogService(store, indications = null))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun packagingTextFindsTurkishAndSwissProductsWithoutACountry() = withCatalog { service ->
        val labels = mapOf(
            "ARVELES® 25 mg Film Kaplı Tablet Deksketoprofen trometamol 20 Film Kaplı Tablet MENARINI" to "ARVELES",
            "Bilaxten® 20 mg Tablet Bilastin Menarini İlaç San. ve Tic. A.Ş." to "BILAXTEN",
            "Dafalgan® 500 mg paracétamol" to "DAFALGAN",
            "deksketoprofen trometamol 25 mg" to "ARVELES",
            "bilastin 20 mg" to "BILAXTEN",
            "parace\u0301tamol 500 mg" to "DAFALGAN",
        )
        for ((text, brand) in labels) {
            val result = service.resolve(CatalogQuery(text = text))
            assertEquals(ResolutionKind.EXACT, result.kind, text)
            assertEquals(brand, result.candidates.single().product.card.brand, text)
            assertEquals(if (brand == "DAFALGAN") "CH" else "TR", result.candidates.single().product.card.source.country)
        }
    }

    @Test
    fun gtinAndDataMatrixFindExactPacksInEveryRegistryWithoutACountry() = withCatalog { service ->
        for (product in products) {
            val gtin = product.packs.single().gtin
            for (code in listOf(gtin, "0$gtin", "(01)0$gtin(17)281231", "]d2010${gtin}17281231")) {
                val result = service.resolve(CatalogQuery(codes = listOf(code)))
                assertEquals(ResolutionKind.EXACT, result.kind, code)
                assertTrue(result.gtinKnown)
                assertEquals(product.id, result.candidates.single().product.id)
                assertEquals(gtin, result.candidates.single().pack?.gtin)
            }
        }
    }

    @Test
    fun aSupplementAndWrongInnStrengthHaveNoRegistryCandidates() = withCatalog { service ->
        for (text in listOf("Burgerstein ZinkVital Nahrungsergänzungsmittel 150 Minitabletten", "bilastin 25 mg", "Menarini Tablet 20 mg")) {
            val result = service.resolve(CatalogQuery(text = text))
            assertEquals(ResolutionKind.TOO_LITTLE, result.kind, text)
            assertTrue(result.candidates.isEmpty(), text)
        }
    }

    @Test
    fun wholeBrandOutranksPartialBrandAndInnWithStrength() {
        val original = products.first()
        val partial = original.copy(id = "partial", card = original.card.copy(brand = "ARVELES SPECIAL", name = "ARVELES SPECIAL 25 mg"))
        val generic = original.copy(id = "generic", card = original.card.copy(brand = "OTHER", name = "OTHER 25 mg"))
        val index = CatalogIndex(listOf(generic, partial, original))
        assertEquals(listOf(original.id, partial.id, generic.id), index.search("ARVELES 25 mg deksketoprofen", null).map { it.product.id })
    }

    @Test
    fun ambiguousTextAndSharedGtinsOfferAtMostFiveCandidatesWithRegistryCountries() {
        val original = products.first()
        val copies = (1..8).map { n -> original.copy(id = "variant:$n", card = original.card.copy(
            source = original.card.source.copy(country = if (n % 2 == 0) "CH" else "TR"))) }
        withCatalog(listOf(snapshots.first().copy(products = copies))) { service ->
            for (query in listOf(CatalogQuery(text = "ARVELES 25 mg"), CatalogQuery(codes = listOf(original.packs.single().gtin)))) {
                val result = service.resolve(query)
                assertEquals(ResolutionKind.CANDIDATES, result.kind)
                assertEquals(5, result.candidates.size)
                assertTrue(result.candidates.all { it.product.card.source.country in it.differences })
                if (query.codes.isNotEmpty()) {
                    assertTrue(result.gtinKnown)
                    assertTrue(result.candidates.all { it.pack?.gtin == original.packs.single().gtin })
                }
            }
        }
    }

    @Test
    fun absentAndEmptySnapshotsAreReportedAsNotLoaded() {
        for (empty in listOf(emptyList(), snapshots.map { it.copy(products = emptyList()) })) {
            withCatalog(empty) { service ->
                val result = service.resolve(CatalogQuery(text = "ARVELES 25 mg"))
                assertEquals(ResolutionKind.NOT_LOADED, result.kind)
                assertTrue(result.candidates.isEmpty())
            }
        }
    }

    @Test
    fun aBrokenSnapshotWithoutOtherLoadedProductsIsReportedAsNotLoaded() {
        val dir = Files.createTempDirectory("broken-recognition-catalog").toFile()
        try {
            java.io.File(dir, "broken.json.gz").writeText("not a gzip snapshot")
            val result = CatalogService(CatalogStore(dir), indications = null).resolve(CatalogQuery(text = "ARVELES 25 mg"))
            assertEquals(ResolutionKind.NOT_LOADED, result.kind)
            assertTrue(result.sources.any { !it.ok })
            assertEquals("ARVELES 25 mg", result.labelName)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun emptyCatalogRouteReturnsAUsableResolutionInsteadOfATransportError() = testApplication {
        val dir = Files.createTempDirectory("empty-recognition-catalog").toFile()
        try {
            application { module(catalog = CatalogService(CatalogStore(dir), indications = null)) }
            val response = client.post("/catalog/resolve") {
                setBody(EasyPaperJson.encodeToString(CatalogQuery.serializer(), CatalogQuery(text = "ARVELES 25 mg")))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ResolutionKind.NOT_LOADED, EasyPaperJson.decodeFromString(Resolution.serializer(), response.bodyAsText()).kind)
        } finally {
            dir.deleteRecursively()
        }
    }
}
