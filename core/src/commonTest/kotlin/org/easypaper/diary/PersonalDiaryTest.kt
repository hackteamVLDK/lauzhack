package org.easypaper.diary

import kotlinx.coroutines.test.runTest
import org.easypaper.demo.Demo
import org.easypaper.model.Answers
import org.easypaper.model.BloodPressure
import org.easypaper.model.DiaryNote
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.QuestionnaireWidget
import org.easypaper.model.Water
import org.easypaper.report.tinyJpeg
import org.easypaper.storage.BlobStore
import org.easypaper.storage.DiaryRepository
import org.easypaper.storage.PatientRepository
import org.easypaper.storage.PhotoStore
import org.easypaper.storage.RecordStore
import org.easypaper.storage.generateRecordKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Patient app v2 core (#47): records belong to the person, doctors' requests are plans on top. */
class PersonalDiaryTest {
    private class Blobs : BlobStore {
        val map = HashMap<String, ByteArray>()
        override suspend fun put(key: String, bytes: ByteArray) { map[key] = bytes }
        override suspend fun get(key: String) = map[key]
        override suspend fun keys() = map.keys.toList()
        override suspend fun delete(key: String) { map.remove(key) }
    }

    private val day0 = Demo.record.entries.minOf { it.at } // first demo day, 17 Nov
    private val bpPlan = Demo.request.widgets.first { it.category() == EntryCategory.BLOOD_PRESSURE }.id

    @Test
    fun ownRecordsNeedNoRequestButStillFollowTheValueRules() {
        val diary = Diary()
        assertEquals(emptyList(), diary.check(Entry("a", ownWidgetId(EntryCategory.BLOOD_PRESSURE), 1, BloodPressure(120, 80))))
        assertEquals(listOf(EntryProblem.DIASTOLIC_NOT_BELOW_SYSTOLIC), diary.check(Entry("b", ownWidgetId(EntryCategory.BLOOD_PRESSURE), 1, BloodPressure(80, 120))))
        assertEquals(listOf(EntryProblem.WRONG_WIDGET_TYPE), diary.check(Entry("c", ownWidgetId(EntryCategory.WATER), 1, BloodPressure(120, 80))))
        assertEquals(listOf(EntryProblem.UNKNOWN_QUESTION), diary.check(Entry("d", ownWidgetId(EntryCategory.SYMPTOMS), 1, Answers(mapOf("q" to "yes")))))
        assertEquals(listOf(EntryProblem.UNKNOWN_WIDGET), diary.check(Entry("e", planWidgetId("nope", "q"), 1, Answers(mapOf("q" to "yes")))))
        assertEquals(listOf(EntryProblem.UNKNOWN_WIDGET), diary.check(Entry("f", "bp", 1, BloodPressure(120, 80))))
    }

    @Test
    fun migrationKeepsEveryRecordAndThePlanSeesExactlyThem() {
        val diary = migrateToDiary(listOf(Demo.record))
        assertEquals(listOf(Demo.request), diary.plans)
        assertEquals(Demo.record.entries.size, diary.entries.size)
        assertTrue(diary.entries.filter { it.payload is Answers }.all { it.widgetId.startsWith("plan:") })
        assertTrue(diary.entries.filter { it.payload !is Answers }.all { it.widgetId.startsWith("own:") })
        // round trip: the plan over the diary is the record the current screens, digest and handover already use
        assertEquals(Demo.record, diary.recordFor(Demo.request))
        assertEquals(Demo.record.progress(), diary.recordFor(Demo.request).progress())
    }

    @Test
    fun aPlanCountsThePersonsOwnRecordsOfItsCategoriesAndPeriodOnly() {
        val diary = Diary(
            entries = listOf(
                Entry("in", ownWidgetId(EntryCategory.BLOOD_PRESSURE), day0, BloodPressure(130, 85)),
                Entry("before", ownWidgetId(EntryCategory.BLOOD_PRESSURE), day0 - 2 * 86_400_000L, BloodPressure(120, 80)),
                Entry("other", ownWidgetId(EntryCategory.WATER), day0, Water(250)),
            ),
            plans = listOf(Demo.request.copy(widgets = Demo.request.widgets.filter { it.category() == EntryCategory.BLOOD_PRESSURE })),
        )
        val record = diary.recordFor(diary.plans.single())
        assertEquals(listOf("in"), record.entries.map { it.id })
        assertEquals(bpPlan, record.entries.single().widgetId)
        assertEquals(1, record.progress().first().widgets.single().done)
    }

    @Test
    fun aSelectionIsTheChosenCategoriesAndDaysWithOrWithoutPhotos() {
        val diary = migrateToDiary(listOf(Demo.record))
        val days = Demo.record.entries.map { it.at }.sorted()
        val (first, second) = listOf(0, 1).map { kotlin.time.Instant.fromEpochMilliseconds(day0 + it * 86_400_000L).toString().take(10) }
        val shared = diary.selection(setOf(EntryCategory.BLOOD_PRESSURE, EntryCategory.FOOD), first, second, now = days.last())
        assertTrue(shared.entries.isNotEmpty())
        assertTrue(shared.entries.all { it.payload is BloodPressure || it.payload is Food })
        assertEquals(setOf("bp", "food"), shared.request.widgets.map { it.id }.toSet())
        assertEquals(2, shared.request.days)
        assertTrue(shared.entries.any { (it.payload as? Food)?.photoRef != null }, "the demo's food photo is shared")
        val noPhotos = diary.selection(setOf(EntryCategory.FOOD), first, second, photos = false)
        assertTrue(noPhotos.entries.none { (it.payload as? Food)?.photoRef != null })
        // questionnaire answers travel with the doctor's questions
        val symptoms = diary.selection(setOf(EntryCategory.SYMPTOMS), first, second)
        val questionnaire = symptoms.request.widgets.single() as QuestionnaireWidget
        assertTrue(symptoms.entries.isNotEmpty() && symptoms.entries.all { it.widgetId == questionnaire.id })
        // the plan frames a selection made from it
        assertEquals(Demo.request, diary.selection(setOf(EntryCategory.BLOOD_PRESSURE), first, second, plan = Demo.request).request)
        assertFailsWith<IllegalArgumentException> { diary.selection(setOf(EntryCategory.FOOD), second, first) }
    }

    @Test
    fun theRepositoryMigratesOnceKeepsOldRecordsAndStoresPhotos() = runTest {
        val blobs = Blobs()
        val key = generateRecordKey()
        // what a v1 phone has: per-request records
        PatientRepository(RecordStore(blobs, key), PhotoStore(blobs, key)).apply { start(Demo.request) }
        RecordStore(blobs, key).save(Demo.record)

        val repo = DiaryRepository(blobs, key).also { it.refresh() }
        assertEquals(Demo.record.entries.size, repo.diary.value.entries.size)
        assertNotNull(RecordStore(blobs, key).load(Demo.request.id), "the current screens keep their records until the switch")

        val photo = tinyJpeg(ByteArray(20) { 3 })
        assertEquals(emptyList(), repo.add(Entry("own-food", ownWidgetId(EntryCategory.FOOD), day0, Food()), photo))
        assertEquals(listOf(EntryProblem.EMPTY_TEXT), repo.add(Entry("own-note", ownWidgetId(EntryCategory.DIARY), day0, DiaryNote(" "))))

        val reopened = DiaryRepository(blobs, key).also { it.refresh() }
        assertEquals(Demo.record.entries.size + 1, reopened.diary.value.entries.size, "saved encrypted, migrated only once")
        assertEquals(tinyJpeg(ByteArray(20) { 3 }).toList(), reopened.photo("photo/own-food")?.toList())
        assertTrue("Headache" !in blobs.map.getValue("diary").decodeToString(), "the diary is encrypted")

        reopened.remove("own-food")
        assertNull(blobs.map["photo/own-food"])
        reopened.addPlan(Demo.request)
        assertEquals(1, reopened.diary.value.plans.size)
        reopened.removePlan(Demo.request.id)
        assertEquals(Demo.record.entries.size, reopened.diary.value.entries.size, "ending a plan keeps the person's records")
        reopened.deleteAll()
        assertEquals(Diary(), DiaryRepository(blobs, key).also { it.refresh() }.diary.value)
    }
}
