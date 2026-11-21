package org.easypaper.ui.patient

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.demo.Demo
import org.easypaper.model.BloodPressure
import org.easypaper.model.Entry
import org.easypaper.model.MedicationsWidget
import org.easypaper.model.PatientRecord
import org.easypaper.model.Water
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodayModelTest {
    private val utc = TimeZone.UTC
    private val lastDay = LocalDate(2026, 11, 21) // day 5 of the demo (17–21 Nov)

    @Test
    fun todayShowsWhatWasRecordedTodayPerWidgetInTheDoctorsOrder() {
        val today = todayFor(Demo.record, lastDay, utc)
        assertEquals(Demo.request.widgets.map { it.id }, today.map { it.widget.id })

        val pressure = assertIs<WidgetToday.Pressure>(today.single { it.widget.id == "bp" })
        assertEquals(listOf("e23", "e24"), pressure.readings.map { it.id })
        assertEquals(2, pressure.expected)
        assertEquals(BloodPressure(134, 87, 74), pressure.latest)

        val water = assertIs<WidgetToday.Drink>(today.single { it.widget.id == "w" })
        assertEquals(1000, water.totalMl)
        assertEquals(1500, water.targetMl)
        assertEquals("e25", water.lastEntryId)

        assertEquals("e26", assertIs<WidgetToday.Questions>(today.single { it.widget.id == "q" }).answered?.id)

        val meds = assertIs<WidgetToday.Notes>(today.single { it.widget is MedicationsWidget })
        assertEquals(listOf("e30"), meds.entries.map { it.id })
        assertTrue(assertIs<WidgetToday.Notes>(today.single { it.widget.id == "f" }).entries.isEmpty()) // food on days 2 and 4 only

        // Questions for the doctor are kept from every day, not only today.
        assertEquals(listOf("e35"), assertIs<WidgetToday.Asked>(today.single { it.widget.id == "dq" }).entries.map { it.id })
    }

    @Test
    fun emptyDayHasNothingAndNothingToUndo() {
        val today = todayFor(PatientRecord(Demo.request), lastDay, utc)
        val pressure = assertIs<WidgetToday.Pressure>(today.single { it.widget.id == "bp" })
        assertNull(pressure.latest)
        val water = assertIs<WidgetToday.Drink>(today.single { it.widget.id == "w" })
        assertEquals(0, water.totalMl)
        assertNull(water.lastEntryId)
    }

    @Test
    fun theDayFollowsThePatientsTimeZone() {
        // 23:10 UTC on the 20th is already the 21st in Kyiv (UTC+2 in winter).
        val late = Entry("late", "w", 1_795_216_200_000L, Water(250)) // 2026-11-20T23:10Z
        val record = PatientRecord(Demo.request, listOf(late))
        assertEquals(250, assertIs<WidgetToday.Drink>(todayFor(record, lastDay, TimeZone.of("Europe/Kyiv")).single { it.widget.id == "w" }).totalMl)
        assertEquals(0, assertIs<WidgetToday.Drink>(todayFor(record, lastDay, utc).single { it.widget.id == "w" }).totalMl)
        assertEquals("01:10", timeOfDay(late.at, TimeZone.of("Europe/Kyiv")))
    }

    @Test
    fun bloodPressureFields() {
        assertEquals(BloodPressure(128, 84, 72), bloodPressureFromFields("128", "84", "72"))
        assertEquals(BloodPressure(128, 84, null), bloodPressureFromFields(" 128 ", "84", ""))
        assertNull(bloodPressureFromFields("", "84", "72")) // upper missing
        assertNull(bloodPressureFromFields("128", "", ""))
        assertNull(bloodPressureFromFields("128", "84", "7x")) // pulse typed but not a number
        // Out of range values come back as a BloodPressure: the request's check explains what is wrong.
        assertEquals(BloodPressure(1280, 84, null), bloodPressureFromFields("1280", "84", ""))
    }

    @Test
    fun litresWithTheLanguagesSeparator() {
        assertEquals("1.25", litres(1250, '.'))
        assertEquals("1,5", litres(1500, ','))
        assertEquals("1", litres(1000, '.'))
        assertEquals("0.99", litres(990, '.'))
        assertEquals("2", litres(1996, '.'))
        assertEquals("0", litres(0, '.'))
    }

    @Test
    fun historyListsDaysNewestFirst() {
        val days = daysWithEntries(Demo.record, utc)
        assertEquals((21 downTo 17).map { LocalDate(2026, 11, it) }, days.map { it.first })
        assertEquals(Demo.record.entries.size, days.sumOf { it.second.size })
    }

    @Test
    fun entryIdsAreUniqueAndSafeInRefs() {
        val random = Random(7)
        val ids = List(200) { newEntryId(random) }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { Regex("e[0-9a-f]{16}").matches(it) })
    }
}
