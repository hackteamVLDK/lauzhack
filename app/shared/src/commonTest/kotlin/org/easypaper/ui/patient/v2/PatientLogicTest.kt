package org.easypaper.ui.patient.v2

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.model.*
import org.easypaper.ui.components.category
import org.easypaper.ui.shell.inMemoryPatientRepository
import org.easypaper.ui.theme.Category
import kotlin.test.*
import kotlin.time.Instant

class PatientLogicTest {
    private val today = LocalDate.parse("2026-11-21")
    private val utc = TimeZone.UTC
    private val titles = Category.entries.associateWith { it.name }
    private fun request() = personalRequest(today, "uk", 123L, titles)
    private fun entry(id: String, category: Category, at: String, payload: EntryPayload) =
        Entry(id, request().widgets.first { it.category == category }.id, Instant.parse(at).toEpochMilliseconds(), payload)

    @Test
    fun personalRequestHasSevenOptionalWidgetsAndNoDoctor() {
        val r = request()
        assertEquals("personal", r.id)
        assertEquals("", r.doctorName)
        assertEquals("", r.complaint)
        assertEquals(today.toString(), r.startDate)
        assertEquals(365, r.days)
        assertEquals("uk", r.locale)
        assertEquals(123L, r.createdAt)
        assertEquals(Category.entries.toSet(), r.widgets.map { it.category }.toSet())
        assertTrue(r.widgets.all { !it.required })
        assertEquals(7, r.widgets.map { it.id }.distinct().size)
    }

    @Test
    fun creatingPersonalTwicePreservesEntriesAndDoctorRequests() = runTest {
        val repository = inMemoryPatientRepository()
        repository.ensurePersonal(request())
        val water = entry("w", Category.Water, "2026-11-21T10:00:00Z", Water(250))
        assertTrue(repository.add(PERSONAL_REQUEST_ID, water).isEmpty())
        val doctor = request().copy(id = "doctor", doctorName = "Dr. Test", days = 7)
        repository.start(doctor)
        repository.ensurePersonal(request().copy(locale = "de", createdAt = 456))
        val records = repository.records.value
        assertEquals(2, records.size)
        val personal = records.single { it.request.id == PERSONAL_REQUEST_ID }
        assertEquals(listOf(water), personal.entries)
        assertEquals("uk", personal.request.locale)
        assertEquals("doctor", records.activeDoctor(today)?.request?.id)
    }

    @Test
    fun shareFiltersCategoriesAndInclusiveLocalPeriodAndCountsDistinctPhotos() {
        val entries = listOf(
            entry("before", Category.Food, "2026-11-19T22:59:59Z", Food("old", "photo/old")),
            entry("start", Category.Food, "2026-11-19T23:00:00Z", Food("meal", "photo/meal")),
            entry("end", Category.Diary, "2026-11-21T22:59:59.999Z", DiaryNote("note", "photo/note")),
            entry("after", Category.Diary, "2026-11-21T23:00:00Z", DiaryNote("future", "photo/future")),
            entry("wrong-category", Category.Water, "2026-11-21T12:00:00Z", Water(250)),
        )
        val record = PatientRecord(request(), entries)
        val selection = selectToShare(record, setOf(Category.Food, Category.Diary),
            Period(LocalDate.parse("2026-11-20"), today), true, TimeZone.of("Europe/Berlin"))
        assertEquals(listOf("start", "end"), selection.record.entries.map { it.id })
        assertEquals(2, selection.entryCount)
        assertEquals(setOf("photo/meal", "photo/note"), selection.photoRefs)
        assertEquals(2, selection.photoCount)
        assertEquals("2026-11-20", selection.record.request.startDate)
        assertEquals(2, selection.record.request.days)
        assertEquals(setOf(Category.Food, Category.Diary), selection.record.request.widgets.map { it.category }.toSet())
        assertEquals(entries, record.entries) // filtering cannot mutate the local diary
    }

    @Test
    fun disablingPhotosRemovesReferencesButKeepsText() {
        val food = entry("f", Category.Food, "2026-11-21T12:00:00Z", Food("Soup", "photo/f"))
        val diary = entry("d", Category.Diary, "2026-11-21T13:00:00Z", DiaryNote("Tired", "photo/d"))
        val record = PatientRecord(request(), listOf(food, diary))
        val selection = selectToShare(record, Category.entries.toSet(), recentPeriod(today, 7), false, utc)
        assertEquals(2, selection.entryCount)
        assertEquals(0, selection.photoCount)
        assertEquals(Food("Soup"), selection.record.entries[0].payload)
        assertEquals(DiaryNote("Tired"), selection.record.entries[1].payload)
        assertEquals("photo/f", record.entries[0].photoReference)
        assertEquals(0, selectToShare(record, emptySet(), recentPeriod(today, 7), true, utc).entryCount)
    }

    @Test
    fun highlightsUseSevenDaysAcrossAllRecordsAndIncludeUnloggedDaysInAverage() {
        val personal = PatientRecord(request(), listOf(
            entry("old", Category.BloodPressure, "2026-11-14T23:59:59Z", BloodPressure(190, 100)),
            entry("first", Category.BloodPressure, "2026-11-15T00:00:00Z", BloodPressure(128, 82)),
            entry("w1", Category.Water, "2026-11-15T12:00:00Z", Water(700)),
        ))
        val doctor = PatientRecord(request().copy(id = "doctor"), listOf(
            entry("last", Category.BloodPressure, "2026-11-21T23:59:59Z", BloodPressure(142, 90)),
            entry("future", Category.BloodPressure, "2026-11-22T00:00:00Z", BloodPressure(180, 95)),
            entry("w2", Category.Water, "2026-11-21T12:00:00Z", Water(700)),
        ))
        val result = highlights(listOf(personal, doctor), today, utc)
        assertEquals(PressureRange(128, 142, 82, 90), result.pressure)
        assertEquals(200, result.averageWaterMl)
        assertEquals(2, result.waterDays)
    }

    @Test
    fun highlightsRespectMidnightInPatientTimeZoneAndEmptyData() {
        assertEquals(Highlights(null, null, 0), highlights(emptyList(), today, utc))
        val record = PatientRecord(request(), listOf(
            entry("included", Category.Water, "2026-11-14T23:00:00Z", Water(700)),
            entry("excluded", Category.Water, "2026-11-21T23:00:00Z", Water(700)),
        ))
        assertEquals(100, highlights(listOf(record), today, TimeZone.of("Europe/Berlin")).averageWaterMl)
    }

    @Test
    fun completedAndFutureDoctorRequestsAreNotActiveAndPersonalNeverBecomesAPlan() {
        val records = listOf(PatientRecord(request()),
            PatientRecord(request().copy(id = "old", doctorName = "Doctor", startDate = "2026-10-01", days = 7)),
            PatientRecord(request().copy(id = "future", doctorName = "Doctor", startDate = "2026-12-01")))
        assertNull(records.activeDoctor(today))
    }
}
