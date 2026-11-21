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
}
