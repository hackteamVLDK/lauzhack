package org.easypaper.catalog

import org.easypaper.scan.parseScanCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Task 71: what is read on a package, normalised for search only. */
class LabelTest {
    @Test
    fun ukrainianBrandsInnAndRussianSpellingsShareSearchKeys() {
        val label = assertNotNull(parseLabel("НУРОФЄН 200 мг таблетки ібупрофен"))
        assertEquals("НУРОФЕН", label.brand)
        assertTrue("НУРОФЕН" in label.brandTokens)
        assertEquals(listOf(Strength(200.0, "mg")), label.strengths)
        assertTrue("ibuprofen" in label.substanceTokens)
        assertEquals(comparisonTokens("NUROFEN"), comparisonTokens("НУРОФЄН"))
        assertEquals(comparisonTokens("Цитрамон-Дарниця"), comparisonTokens("TSITRAMON DARNITSYA"))
        assertEquals(searchTokens("є і ї ґ ё"), searchTokens("е и и г е"))
        assertEquals(searchTokens("М'ЯТА"), searchTokens("МʼЯТА"))
        assertEquals(searchTokens("МЯТА"), searchTokens("М’ЯТА"))
        assertEquals(innRoot("ibuprofen"), innRoot("ібупрофен"))
        assertEquals(innRoot("ibuprofenum"), innRoot("ибупрофену"))
        assertEquals(innRoot("paracetamol"), innRoot("парацетамол"))
        assertEquals(innRoot("paracetamolum"), innRoot("парацетамолу"))
        assertTrue(ingredientRoots("1 таблетка містить парацетамолу 500 мг").contains(innRoot("paracetamol")))
    }

    @Test
    fun cyrillicStrengthUnitsAndDecimalGramsAreComparable() {
        assertEquals(listOf(Strength(500.0, "mg")), strengthsIn("Анальгін 0,5 г"))
        assertEquals(listOf(Strength(2.0, "ml"), Strength(0.1, "mg")), strengthsIn("2 мл 100 мкг"))
        for (unit in listOf("МО", "ME", "МЕ", "IU", "UI", "IE")) {
            assertEquals(listOf(Strength(200.0, "IU")), strengthsIn("200 $unit"), unit)
        }
        assertEquals(variantsIn("Нурофєн форте"), variantsIn("Nurofen forte"))
        assertTrue(parseLabel("ТАБЛЕТКИ 200 МГ")!!.brandTokens.isEmpty())
    }

    @Test
    fun arvelesPackagingKeepsBrandStrengthAndTurkishInnSearchAlias() {
        val label = assertNotNull(parseLabel("ARVELES® 25 mg Film Kaplı Tablet Deksketoprofen trometamol 20 Film Kaplı Tablet MENARINI"))
        assertEquals("ARVELES", label.brand)
        assertTrue("ARVELES" in label.brandTokens)
        assertEquals(listOf(Strength(25.0, "mg")), label.strengths)
        assertTrue("dexketoprofen" in label.substanceTokens)
        assertEquals(innRoot("dexketoprofen"), innRoot("deksketoprofen"))
    }

    @Test
    fun bilaxtenPackagingKeepsBrandStrengthAndTheCommonInnRoot() {
        val label = assertNotNull(parseLabel("Bilaxten® 20 mg Tablet Bilastin Menarini İlaç San. ve Tic. A.Ş."))
        assertEquals("BILAXTEN", label.brand)
        assertTrue("BILAXTEN" in label.brandTokens)
        assertEquals(listOf(Strength(20.0, "mg")), label.strengths)
        assertTrue("bilastine" in label.substanceTokens)
        assertEquals(innRoot("bilastine"), innRoot("bilastin"))
    }

    @Test
    fun anExplicitSupplementLabelIsNotMedicineSearchEvidence() {
        assertNull(parseLabel("Burgerstein ZinkVital Nahrungsergänzungsmittel 150 Minitabletten"))
    }

    @Test
    fun turkishFrenchAndDecomposedAccentsFoldForSearchOnly() {
        assertEquals(searchTokens("i s c o u g e"), searchTokens("ı ş ç ö ü ğ é"))
        assertEquals(searchTokens("BILAXTEN ILAC"), searchTokens("Bilaxten İlaç"))
        assertEquals(searchTokens("paracétamol"), searchTokens("parace\u0301tamol"))
        assertEquals(listOf(Strength(20.0, "IU"), Strength(1.0, "mg"), Strength(2.0, "ml")), strengthsIn("20 IE 1000 µg 2 ml"))
    }

    @Test
    fun strengthsWithDecimalCommaCyrillicAndThousandsAreUnderstood() {
        assertEquals(listOf(Strength(2.5, "mg")), strengthsIn("RAMIPRIL 2,5 mg"))
        assertEquals(listOf(Strength(1000.0, "mg")), strengthsIn("ACETALGIN Filmtabl 1 g 16 Stk"))
        assertEquals(listOf(Strength(500.0, "mg")), strengthsIn("Парацетамол 500мг"))
        assertEquals(listOf(Strength(200000.0, "IU")), strengthsIn("vitamine D3 200 000 UI"))
        assertEquals(listOf(Strength(0.1, "mg")), strengthsIn("EUTHYROX 100 µg"))
        assertEquals(strengthOf("2,5", "mg"), strengthsIn("2.5 mg").single())
    }

    @Test
    fun variantWordsArePhrasesAndSurviveAccentsAndCase() {
        assertEquals(setOf("ADULTES", "SANS SUCRE"), variantsIn("Fervex adultes sans sucre"))
        assertEquals(setOf("FORTE"), variantsIn("Dolo Spedifen forte"))
        assertEquals(setOf("LIBERATION PROLONGEE", "LP"), variantsIn("comprimé à libération prolongée LP"))
        assertTrue(variantsIn("Nurofen").isEmpty())
    }

    @Test
    fun searchKeepsTheOriginalButFoldsAccentsAndCyrillic() {
        assertEquals(listOf("PARACETAMOL", "500", "MG"), searchTokens("paracétamol 500 mg"))
        assertEquals(listOf("ЖЕЛТЫИ"), searchTokens("жёлтый"))
        assertEquals(listOf("2.5"), searchTokens("2,5"))
    }

    @Test
    fun gtinsKeepTheirLeadingZerosAndAreComparedInOneForm() {
        assertEquals("3400930316948", parseScanCode("(01)03400930316948(17)271231")?.gtin)
        assertEquals("3400930316948", parseScanCode("03400930316948")?.gtin)
        assertEquals("0012345678905", parseScanCode("012345678905")?.gtin) // UPC-A keeps its zero, as a string
        assertNull(parseScanCode("3400930316949")) // wrong check digit
    }

    @Test
    fun theEuMarketCoversCentrallyAuthorisedProducts() {
        assertTrue(marketFits("EU", "DE"))
        assertTrue(marketFits("CH", "CH"))
        assertTrue(!marketFits("EU", "CH"))
    }
}
