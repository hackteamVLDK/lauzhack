package org.easypaper.catalog

import org.easypaper.scan.parseScanCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Task 71: what is read on a package, normalised for search only. */
class LabelTest {
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
