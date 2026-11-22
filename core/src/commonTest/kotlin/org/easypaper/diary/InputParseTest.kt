package org.easypaper.diary

import org.easypaper.model.BloodPressure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputParseTest {
    @Test
    fun typedBloodPressure() {
        assertEquals(BloodPressure(120, 80), parseBloodPressure("120/80"))
        assertEquals(BloodPressure(120, 80, 72), parseBloodPressure("120/80 72"))
        assertEquals(BloodPressure(135, 85, 64), parseBloodPressure("135 / 85, pulse 64"))
        assertEquals(BloodPressure(120, 80), parseBloodPressure("120 на 80"))
        assertEquals(BloodPressure(140, 90, 70), parseBloodPressure("140 zu 90 Puls 70"))
        assertEquals(BloodPressure(118, 76), parseBloodPressure("118 over 76"))
    }

    @Test
    fun monitorDisplayOcr() {
        assertEquals(BloodPressure(128, 84, 72), parseBloodPressure("SYS 128 mmHg\nDIA 84 mmHg\nPULSE 72 /min"))
        assertEquals(BloodPressure(128, 84, 72), parseBloodPressure("SYS\nmmHg\n128\nDIA\nmmHg\n84\nPUL /min 72"))
        assertEquals(BloodPressure(131, 79, 66), parseBloodPressure("21.11. 08:15\nSYS 131\nDIA 79\n♥ 66"))
        assertEquals(BloodPressure(142, 91, 80), parseBloodPressure("Systolisch 142 Diastolisch 91 Puls 80"))
        assertEquals(BloodPressure(125, 82, 68), parseBloodPressure("СИС 125 ДИА 82 ПУЛЬС 68"))
        assertEquals(BloodPressure(127, 81, 69), parseBloodPressure("127 SYS 81 DIA 69 P/min")) // values before labels
        assertEquals(BloodPressure(129, 83), parseBloodPressure("M2 SYS 129 DIA 83"))
        assertEquals(BloodPressure(126, 80, 70), parseBloodPressure("126\n80\n70")) // bare display, no date/time
    }

    @Test
    fun ambiguousOrImpossibleBloodPressureIsNull() {
        for (bad in listOf(
            "", "hello", "120", "80/120", "21/11", "1200/80", "120/80 400",
            "08:15 126 80 70", // time on screen: three bare numbers are not trusted
            "126 80", "SYS DIA", "SYS 128 DIA", "12 8 7",
        )) assertNull(parseBloodPressure(bad), "parsed '$bad'")
    }

    @Test
    fun waterWithUnits() {
        assertEquals(1500, parseWaterMl("1,5 l"))
        assertEquals(1500, parseWaterMl("1.5 Liter"))
        assertEquals(500, parseWaterMl("500ml"))
        assertEquals(500, parseWaterMl("2x250 мл"))
        assertEquals(750, parseWaterMl("3 × 250 ml Wasser"))
        assertEquals(300, parseWaterMl("3 dl"))
        assertEquals(330, parseWaterMl("33 cl"))
        assertEquals(1000, parseWaterMl("1 литр"))
        assertEquals(500, parseWaterMl("2 стакана воды"))
        assertEquals(250, parseWaterMl("a glass of water"))
        assertEquals(750, parseWaterMl("3 Gläser"))
        assertEquals(200, parseWaterMl("200 мл"))
    }

    @Test
    fun ambiguousWaterIsNull() {
        for (bad in listOf("", "500", "1,5", "eine Flasche", "a bottle", "500 ml and 2 glasses", "0 ml", "10 l", "много воды")) {
            assertNull(parseWaterMl(bad), "parsed '$bad'")
        }
    }

    @Test
    fun reviewCasesAreNullNotGuessed() {
        // 1) values before the labels, a clock at the end: the clock must not become the pulse
        assertEquals(BloodPressure(128, 84, 72), parseBloodPressure("128 SYS 84 DIA 72 PUL 21:15"))
        // 2) a missing value is not borrowed from the next label
        assertNull(parseBloodPressure("SYS 120\nDIA --\nPULSE 72"))
        // 3) two readings
        assertNull(parseBloodPressure("120/80 or 150/90"))
        assertNull(parseBloodPressure("SYS 128 DIA 84 SYS 131 DIA 86"))
        // 4) a leftover number that cannot be placed
        assertNull(parseBloodPressure("120/80 4000"))
        assertNull(parseBloodPressure("120/80 72 65"))
        // 5) signs, fractions, ranges
        assertNull(parseBloodPressure("120 80 -72"))
        assertNull(parseBloodPressure("120/-80"))
        assertNull(parseWaterMl("-500 ml"))
        assertNull(parseWaterMl("1/2 l"))
        assertNull(parseWaterMl("1-2 l"))
        // 6) a fractional number of glasses
        assertNull(parseWaterMl("1.5 glasses"))
        assertNull(parseWaterMl("1,5 стакана"))
        // 7) absurd counts: no crash, no overflow
        assertNull(parseWaterMl("2147483648x250 ml"))
        assertNull(parseWaterMl("4294968x1000 ml"))
        assertNull(parseWaterMl("99999999999999999999 ml"))
        assertNull(parseWaterMl("30 стаканов"))
    }

    @Test
    fun conflictingLayoutsAreNull() {
        // reading values after the labels gives 84/72/66, before gives 128/84/72: both complete and different
        assertNull(parseBloodPressure("128 SYS 84 DIA 72 PUL 66"))
    }

    @Test
    fun ukrainianLabelsAndUnits() {
        assertEquals(BloodPressure(133, 86, 71), parseBloodPressure("СИСТ 133 ДІА 86 ПУЛЬС 71"))
        assertEquals(BloodPressure(140, 90), parseBloodPressure("Систолічний 140 Діастолічний 90"))
        assertEquals(500, parseWaterMl("2 склянки води"))
        assertEquals(1500, parseWaterMl("1,5 літра"))
        assertEquals(250, parseWaterMl("250 мл"))
    }

    @Test
    fun frenchLabelsAndUnits() {
        assertEquals(BloodPressure(128, 84, 72), parseBloodPressure("Tension artérielle : 128/84, pouls 72"))
        assertEquals(BloodPressure(120, 80, 64), parseBloodPressure("Tension 120 sur 80 pouls 64"))
        assertEquals(BloodPressure(133, 86, 71), parseBloodPressure("Systolique 133 Diastolique 86 Pouls 71"))
        assertEquals(BloodPressure(127, 81, 69), parseBloodPressure("127 systolique 81 diastolique 69 pouls"))
        assertEquals(250, parseWaterMl("un verre d'eau"))
        assertEquals(500, parseWaterMl("2 verres d'eau"))
        assertEquals(1000, parseWaterMl("1 litre d'eau"))
        assertEquals(1500, parseWaterMl("1,5 litres"))
        assertEquals(1500, parseWaterMl("1,5 l"))
        assertEquals(250, parseWaterMl("250 millilitres"))
        assertEquals(500, parseWaterMl("2x250 ml"))
        assertNull(parseBloodPressure("tension 120/80 ou 140/90"))
        assertNull(parseWaterMl("1,5 verres"))
        assertNull(parseWaterMl("1/2 litre"))
        assertNull(parseWaterMl("1-2 litres"))
        assertNull(parseWaterMl("500 ml et 2 verres"))
        assertNull(parseWaterMl("30 verres"))
        assertNull(parseWaterMl("un verrement"))
    }

    @Test
    fun russianLabelsAndUnits() {
        assertEquals(BloodPressure(128, 84, 72), parseBloodPressure("Давление 128/84, пульс 72"))
        assertEquals(BloodPressure(120, 80, 64), parseBloodPressure("Артериальное давление 120 на 80 пульс 64"))
        assertEquals(BloodPressure(133, 86, 71), parseBloodPressure("Систолическое 133 Диастолическое 86 Пульс 71"))
        assertEquals(250, parseWaterMl("стакан воды"))
        assertEquals(500, parseWaterMl("2 стакана"))
        assertEquals(1250, parseWaterMl("5 стаканов воды"))
        assertEquals(1000, parseWaterMl("1 литр"))
        assertEquals(1500, parseWaterMl("1,5 литра"))
        assertEquals(2000, parseWaterMl("2 литров"))
        assertEquals(250, parseWaterMl("250 мл"))
        assertNull(parseBloodPressure("давление 120/80 или 140/90"))
        assertNull(parseWaterMl("500 мл и 2 стакана"))
        assertNull(parseWaterMl("1/2 литра"))
        assertNull(parseWaterMl("-500 мл"))
    }
}
