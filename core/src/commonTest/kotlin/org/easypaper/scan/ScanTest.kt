package org.easypaper.scan

import org.easypaper.model.MedicationSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanTest {
    private val db = MedicationDb()

    @Test
    fun databaseHasMoreThanTenProducts() {
        assertTrue(DEMO_MEDICATIONS.size >= 10)
        assertTrue(DEMO_MEDICATIONS.map { it.atc }.toSet().size >= 10)
        DEMO_MEDICATIONS.forEach { assertEquals(it.gtin, parseScanCode(it.gtin)?.gtin, "bad GTIN in data: ${it.gtin}") }
    }

    @Test
    fun everyProductRecognizedByEan13() {
        DEMO_MEDICATIONS.forEach { assertEquals(it, db.find(parseScanCode(it.gtin)!!)) }
    }

    @Test
    fun everyProductRecognizedByDataMatrix() {
        DEMO_MEDICATIONS.forEach { item ->
            val code = parseScanCode("]d2010${item.gtin}17281231" + "10AB-12\u001D" + "21XYZ987")!!
            assertEquals(ScanCode(item.gtin, "281231", "AB-12", "XYZ987"), code)
            assertEquals(item, db.find(code))
        }
    }

    @Test
    fun printedHumanReadableForm() {
        assertEquals(
            ScanCode("7680567200025", "270630", "B123", "S1"),
            parseScanCode("(01)07680567200025(17)270630(10)B123(21)S1"),
        )
    }

    @Test
    fun unknownVariableFieldIsSkipped() {
        assertEquals(ScanCode("7680567200025", "270630", "B1"), parseScanCode("0107680567200025" + "17270630" + "714ABC\u001D" + "10B1"))
    }

    @Test
    fun badCodesRejected() {
        for (raw in listOf("", "hello", "7680567200026", "]d2", "(17)270630", "01076805672001", "76805672000", "123")) {
            assertNull(parseScanCode(raw), "accepted '$raw'")
        }
        assertNull(db.find(ScanCode("4006381333931"))) // valid GTIN, not in the list
    }

    @Test
    fun everyProductFoundByOcrTextOfItsBox() {
        DEMO_MEDICATIONS.forEach { item ->
            val ocr = "Rx\n" + item.nameDe.lowercase().replace(" ", "\n") + "\nSwissmedic 12345"
            assertEquals(item, db.search(ocr).first(), "OCR: $ocr")
            assertEquals(item, db.search(item.nameFr).first(), "FR: ${item.nameFr}")
        }
        assertTrue(db.search("Kaffee und Kuchen").isEmpty())
    }

    @Test
    fun draftMedicationNeedsConfirmation() {
        val item = db.find(parseScanCode("7680567200025")!!)!!
        val med = item.toMedication()
        assertEquals("DOLO SPEDIFEN forte Filmtabl 400 mg 10 Stk", med.name)
        assertEquals("7680567200025", med.code)
        assertEquals("400 mg", med.dose)
        assertEquals(MedicationSource.SCAN, med.source)
        assertFalse(med.confirmed)
        assertEquals(item.nameFr, item.toMedication("fr-CH").name)
        assertEquals("1 g/2ml", DEMO_MEDICATIONS.first { "Inj" in it.nameDe }.toMedication().dose)
    }
}
