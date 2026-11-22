package org.easypaper.ui.patient.v2

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.model.*
import org.easypaper.ui.components.category
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.theme.Category
import kotlin.test.*
import kotlin.time.Instant

class SummaryDataTest {
    private val today = LocalDate(2026, 11, 22)
    private val zone = TimeZone.UTC
    private val request = personalRequest(today, "en", 0, Category.entries.associateWith { it.name })
    private fun entry(id: String, category: Category, at: String, payload: EntryPayload) = Entry(id,
        request.widgets.first { it.category == category }.id, Instant.parse(at).toEpochMilliseconds(), payload)

    @Test
    fun emptyCategoriesHaveNoValueAndSevenEmptyCalendarSlots() {
        for (category in Category.entries) {
            val data = summaryData(emptyList(), category, today, zone)
            assertNull(data.latest)
            assertNull(data.value)
            assertEquals(7, data.days.size)
            assertEquals(LocalDate(2026, 11, 16), data.days.first().date)
            assertEquals(today, data.days.last().date)
            assertTrue(data.days.all { it.samples == 0 })
        }
    }

    @Test
    fun waterUsesLocalMidnightAcrossRecordsAndExcludesFutureDays() {
        val entries = listOf(
            entry("old", Category.Water, "2026-11-21T21:59:59Z", Water(900)),
            entry("start", Category.Water, "2026-11-21T22:00:00Z", Water(250)),
            entry("today", Category.Water, "2026-11-22T08:00:00Z", Water(300)),
            entry("future", Category.Water, "2026-11-22T22:00:00Z", Water(1000)),
            entry("pressure", Category.BloodPressure, "2026-11-22T09:00:00Z", BloodPressure(128, 84)),
        )
        val records = listOf(PatientRecord(request, entries.take(2)),
            PatientRecord(request.copy(id = "doctor"), entries.drop(2)))
        val result = summaryData(records, Category.Water, today, TimeZone.of("UTC+02:00"))
        assertEquals(SummaryValue.WaterTotal(550), result.value)
        assertEquals("today", result.latest?.entry?.id)
        assertEquals(900.0, result.days[5].value)
        assertEquals(550.0, result.days[6].value)
        assertEquals(2, result.days[6].samples)
        assertEquals(0.0, result.days[0].value)
    }

    @Test
    fun pressureShowsLatestPairAndDailySystolicMeansWithGaps() {
        val entries = listOf(
            entry("latest", Category.BloodPressure, "2026-11-22T12:00:00Z", BloodPressure(128, 84)),
            entry("first", Category.BloodPressure, "2026-11-22T08:00:00Z", BloodPressure(132, 86)),
            entry("old", Category.BloodPressure, "2026-11-14T08:00:00Z", BloodPressure(140, 90)),
        )
        val data = summaryData(listOf(PatientRecord(request, entries)), Category.BloodPressure, today, zone)
        assertEquals(SummaryValue.Pressure(128, 84), data.value)
        assertEquals("latest", data.latest?.entry?.id)
        assertEquals(130.0, data.days.last().value)
        assertTrue(data.days.dropLast(1).all { it.value == null })
    }

    @Test
    fun weightNeverMixesKilogramsWithOtherUnitsOrMeasurements() {
        val entries = listOf(
            entry("waist", Category.Measurements, "2026-11-20T08:00:00Z", Measurement("Waist", 90.0, "cm")),
            entry("lb", Category.Measurements, "2026-11-21T08:00:00Z", Measurement("Weight", 160.0, "lb", Measurement.WEIGHT_LOINC)),
            entry("kg", Category.Measurements, "2026-11-22T08:00:00Z", Measurement.weight(72.5)),
        )
        val data = summaryData(listOf(PatientRecord(request, entries)), Category.Measurements, today, zone)
        assertEquals(SummaryValue.Weight(72.5), data.value)
        assertEquals(72.5, data.days.last().value)
        assertTrue(data.days.dropLast(1).all { it.value == null })
        val other = summaryData(listOf(PatientRecord(request, entries.take(1))), Category.Measurements, today, zone)
        assertEquals(SummaryMetric.Entries, other.metric)
        assertEquals(SummaryValue.Entries(1), other.value)
    }

    @Test
    fun textCategoriesCountOnlyTheSevenInclusiveDays() {
        val entries = listOf(
            entry("before", Category.Diary, "2026-11-15T23:59:59Z", DiaryNote("old")),
            entry("start", Category.Diary, "2026-11-16T00:00:00Z", DiaryNote("one")),
            entry("end", Category.Diary, "2026-11-22T10:00:00Z", DiaryNote("two")),
        )
        val data = summaryData(listOf(PatientRecord(request, entries)), Category.Diary, today, zone)
        assertEquals(SummaryValue.Entries(2), data.value)
        assertEquals("end", data.latest?.entry?.id)
        assertEquals(listOf(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0), data.days.map { it.value })
    }

    @Test
    fun timestampsUseThePatientsTimeZoneAndCalendarYesterday() {
        val zone = TimeZone.of("UTC+02:00")
        val at: (String) -> Long = { Instant.parse(it).toEpochMilliseconds() }
        assertEquals(SummaryTime.Today("00:05"), summaryTime(at("2026-11-21T22:05:00Z"), today, zone))
        assertEquals(SummaryTime.Yesterday, summaryTime(at("2026-11-21T21:59:59Z"), today, zone))
        assertEquals(SummaryTime.Earlier(LocalDate(2026, 11, 20)), summaryTime(at("2026-11-20T12:00:00Z"), today, zone))
    }

    @Test
    fun trendsRequireTwoWeeksAndSamplesInBothWindows() {
        val old = entry("old", Category.Water, "2026-11-09T08:00:00Z", Water(700))
        val recent = entry("new", Category.Water, "2026-11-22T08:00:00Z", Water(1400))
        val records = listOf(PatientRecord(request, listOf(old, recent)))
        val trend = assertNotNull(summaryTrend(records, Category.Water, today, zone))
        assertEquals(14, trend.days.size)
        assertEquals(100.0, trend.previousAverage)
        assertEquals(200.0, trend.currentAverage)
        assertEquals(SummaryDirection.Higher, trend.direction)
        assertNull(summaryTrend(listOf(PatientRecord(request, listOf(recent))), Category.Water, today, zone))
        assertNull(summaryTrend(listOf(PatientRecord(request, listOf(old))), Category.Water, today, zone))
        val tooRecent = old.copy(at = Instant.parse("2026-11-10T08:00:00Z").toEpochMilliseconds())
        assertNull(summaryTrend(listOf(PatientRecord(request, listOf(tooRecent, recent))), Category.Water, today, zone))
        assertNull(summaryTrend(records, Category.Diary, today, zone))
    }

    @Test
    fun pressureTrendWeightsSamplesAndComparesOnlySystolicReadings() {
        val entries = listOf(
            entry("old", Category.BloodPressure, "2026-11-09T08:00:00Z", BloodPressure(140, 70)),
            entry("new1", Category.BloodPressure, "2026-11-21T08:00:00Z", BloodPressure(120, 90)),
            entry("new2", Category.BloodPressure, "2026-11-21T09:00:00Z", BloodPressure(130, 95)),
            entry("new3", Category.BloodPressure, "2026-11-22T08:00:00Z", BloodPressure(140, 100)),
        )
        val trend = assertNotNull(summaryTrend(listOf(PatientRecord(request, entries)), Category.BloodPressure, today, zone))
        assertEquals(140.0, trend.previousAverage)
        assertEquals(130.0, trend.currentAverage)
        assertEquals(SummaryDirection.Lower, trend.direction)
        val same = trend.copy(previousAverage = 130.0)
        assertEquals(SummaryDirection.Same, same.direction)
    }

    @Test
    fun tipDismissalsPersistAndResetAndPinnedWidgetsHideTheirTips() {
        val empty = { SavedSetting(null) {} }
        var saved: String? = null
        val settings: (SavedSetting) -> PatientSettings = { dismissed -> PatientSettings(empty(), empty(), empty(),
            empty(), empty(), empty(), empty(), empty(), empty(), empty(), empty(), dismissed) }
        val patient = settings(SavedSetting(null) { saved = it })
        assertEquals(SummaryTip.entries, summaryTips(patient, false))
        patient.dismissTip(SummaryTip.Pressure)
        assertEquals("Pressure", saved)
        val restored = settings(SavedSetting(saved) {})
        restored.addWidget(Category.Medicines)
        assertEquals(listOf(SummaryTip.Invite), summaryTips(restored, false))
        assertTrue(summaryTips(restored, true).isEmpty())
        patient.reset()
        assertNull(saved)
        assertEquals(SummaryTip.entries, summaryTips(patient, false))
    }
}
