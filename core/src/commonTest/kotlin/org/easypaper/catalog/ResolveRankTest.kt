package org.easypaper.catalog

import org.easypaper.model.Ingredient
import org.easypaper.model.MedicineProduct
import org.easypaper.model.RegistrySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Searching every registry at once (task 86) must keep the ranking: a whole brand wins, a brand is never swapped. */
class ResolveRankTest {
    private fun source(registry: String, country: String, id: String) =
        RegistrySource(registry, registry, country, id, "https://example.org/$id", "2026-11-22")

    private fun product(id: String, name: String, brand: String, country: String, vararg substances: Pair<String, String>) =
        CatalogProduct("$country:$id", MedicineProduct(name, brand, "tablet",
            substances.map { (substance, mg) -> Ingredient(substance, mg, "mg") }, source = source(country.lowercase(), country, id)))

    private val fervexAdultes = product("1", "FERVEX ADULTES, granulés pour solution buvable en sachet", "FERVEX ADULTES", "FR",
        "acide ascorbique" to "200", "paracétamol" to "500", "maléate de phéniramine" to "25")
    private val fervexSansSucre = product("2", "FERVEX ADULTES SANS SUCRE, granulés pour solution buvable en sachet",
        "FERVEX ADULTES SANS SUCRE", "FR", "acide ascorbique" to "200", "paracétamol" to "500", "maléate de phéniramine" to "25")
    private val fervexEnfants = product("3", "FERVEX ENFANTS SANS SUCRE, granulés", "FERVEX ENFANTS SANS SUCRE", "FR",
        "paracétamol" to "280", "maléate de phéniramine" to "10")
    private val dafalgan500 = product("4", "Dafalgan 500 mg, Tabletten", "Dafalgan 500 mg", "CH", "paracetamolum" to "500")
    private val dafalgan1g = product("5", "Dafalgan 1 g, Filmtabletten", "Dafalgan 1 g", "CH", "paracetamolum" to "1000")
    private val generic = product("6", "Paracetamol-Mepha 500 mg, Filmtabletten", "Paracetamol-Mepha", "CH", "paracetamolum" to "500")

    private val index = CatalogIndex(listOf(fervexAdultes, fervexSansSucre, fervexEnfants, dafalgan500, dafalgan1g, generic))

    @Test
    fun wholeBrandStaysExactWithoutACountry() {
        val result = resolve(index, CatalogQuery(text = "FERVEX ADULTES 500 mg"))
        assertEquals(ResolutionKind.EXACT, result.kind)
        assertEquals(fervexAdultes.id, result.candidates.single().product.id)
    }

    @Test
    fun brandBeatsAGenericWithTheSameSubstance() {
        val result = resolve(index, CatalogQuery(text = "Dafalgan 500 mg\nFilmtabletten\nParacetamol"))
        assertEquals(ResolutionKind.EXACT, result.kind)
        assertEquals(dafalgan500.id, result.candidates.single().product.id)
    }

    @Test
    fun aBrandWithAnUnknownStrengthIsNeverSwappedForAnotherMedicine() {
        val result = resolve(index, CatalogQuery(text = "DAFALGAN 280 mg paracetamol"))
        assertEquals(ResolutionKind.CANDIDATES, result.kind)
        assertEquals(ResolutionQuestion.WHICH_STRENGTH, result.question)
        assertTrue(result.candidates.none { it.product.id == fervexEnfants.id }, result.candidates.map { it.product.id }.toString())
        assertTrue(result.candidates.any { it.product.id == dafalgan500.id })
    }
}
