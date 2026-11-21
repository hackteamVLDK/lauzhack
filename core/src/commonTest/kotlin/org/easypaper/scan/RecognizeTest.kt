package org.easypaper.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecognizeTest {
    private val db = MedicationDb()

    @Test
    fun codeInTheListWinsOverText() {
        val m = db.recognize(PhotoReading(listOf("hello", "]d2" + "0107680567200025" + "17271231" + "10AB12"), "ACETALGIN 1 g"))
        assertTrue(m.byCode)
        assertEquals(listOf("7680567200025"), m.candidates.map { it.gtin })
        assertEquals("271231", m.code?.expiry)
    }

    @Test
    fun unknownCodeFallsBackToTheNameButKeepsTheCode() {
        val m = db.recognize(PhotoReading(listOf("4006381333931"), "Dolo Spedifen forte\n400 mg\n10 Filmtabletten"))
        assertEquals(false, m.byCode)
        assertEquals("4006381333931", m.code?.gtin)
        assertEquals("7680567200025", m.candidates.first().gtin)
    }

    @Test
    fun nothingReadableGivesNoCandidates() {
        assertEquals(PackageMatch(null, emptyList(), false), db.recognize(PhotoReading(emptyList(), "")))
    }
}

class TextByRowsTest {
    @Test
    fun monitorColumnsBecomeRows() {
        // as ML Kit returns a monitor photo: the label column first, then the big digits
        val lines = listOf(
            TextLine("SYS mmHg", 120, 155, 400, 200), TextLine("DIA mmHg", 120, 515, 400, 560), TextLine("PUL /min", 120, 855, 400, 900),
            TextLine("128", 700, 150, 1100, 330), TextLine("84", 760, 510, 1000, 690), TextLine("72", 760, 850, 1000, 1030),
        )
        assertEquals("SYS mmHg 128\nDIA mmHg 84\nPUL /min 72", textByRows(lines))
        assertEquals(org.easypaper.model.BloodPressure(128, 84, 72), org.easypaper.diary.parseBloodPressure(textByRows(lines)))
    }

    @Test
    fun paragraphLinesStaySeparate() {
        val lines = listOf(TextLine("SPEDIFEN", 80, 100, 500, 190), TextLine("400 mg", 80, 220, 400, 310), TextLine("30 Stk", 80, 340, 400, 430))
        assertEquals("SPEDIFEN\n400 mg\n30 Stk", textByRows(lines))
        assertEquals("", textByRows(emptyList()))
    }
}
