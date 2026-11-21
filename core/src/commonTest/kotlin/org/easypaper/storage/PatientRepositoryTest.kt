package org.easypaper.storage

import kotlinx.coroutines.test.runTest
import org.easypaper.diary.EntryProblem
import org.easypaper.model.*
import org.easypaper.report.tinyJpeg
import kotlin.test.*

class PatientRepositoryTest {
    private class MemoryBlobs : BlobStore {
        val map = mutableMapOf<String, ByteArray>()
        override suspend fun put(key: String, bytes: ByteArray) { map[key] = bytes }
        override suspend fun get(key: String) = map[key]
        override suspend fun keys() = map.keys.toList()
        override suspend fun delete(key: String) { map.remove(key) }
    }

    private val request = CareRequest(
        id = "req-1", createdAt = 0, doctorName = "Dr", complaint = "secret complaint", startDate = "2026-11-21", days = 3,
        locale = "en", widgets = listOf(BloodPressureWidget("bp", "BP"), FoodWidget("f", "Food"), DiaryWidget("d", "Diary")),
    )

    private suspend fun repo(blobs: BlobStore = MemoryBlobs(), key: ByteArray? = null): PatientRepository {
        val k = key ?: generateRecordKey()
        return PatientRepository(RecordStore(blobs, k), PhotoStore(blobs, k))
    }

    @Test
    fun entriesAreCheckedSavedAndPublished() = runTest {
        val repo = repo()
        repo.start(request)
        assertEquals(listOf(PatientRecord(request)), repo.records.value)
        assertEquals(emptyList(), repo.add("req-1", Entry("e1", "bp", 1, BloodPressure(120, 80))))
        assertEquals(listOf(EntryProblem.SYSTOLIC_OUT_OF_RANGE), repo.add("req-1", Entry("e2", "bp", 2, BloodPressure(1200, 80))))
        assertEquals(listOf("e1"), repo.records.value.single().entries.map { it.id }) // the typo never reached the store
        repo.start(request) // starting again keeps what is there
        assertEquals(1, repo.records.value.single().entries.size)
        assertFailsWith<IllegalArgumentException> { repo.add("req-1", Entry("e1", "bp", 3, BloodPressure(121, 81))) }
        assertFailsWith<IllegalArgumentException> { repo.add("other", Entry("e9", "bp", 3, BloodPressure(121, 81))) }
    }

    @Test
    fun photoIsStrippedEncryptedAndLinkedToTheEntry() = runTest {
        val blobs = MemoryBlobs()
        val repo = repo(blobs)
        repo.start(request)
        val exif = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0, 16) + "Exif\u0000\u0000PhoneMdl".encodeToByteArray()
        val jpeg = tinyJpeg(ByteArray(100) { 7 }).let { it.copyOfRange(0, 2) + exif + it.copyOfRange(2, it.size) }
        assertEquals(emptyList(), repo.add("req-1", Entry("e1", "f", 1, Food(text = null)), photo = jpeg)) // photo alone is enough
        val food = repo.records.value.single().entries.single().payload as Food
        assertEquals("photo/e1", food.photoRef)
        assertContentEquals(tinyJpeg(ByteArray(100) { 7 }), repo.photo("photo/e1")) // EXIF gone
        assertFalse("PhoneMdl" in blobs.map.getValue("photo/e1").decodeToString())
        assertFailsWith<IllegalArgumentException> { repo.add("req-1", Entry("e2", "bp", 1, BloodPressure(120, 80)), photo = jpeg) }
        assertFailsWith<IllegalArgumentException> { repo.add("req-1", Entry("e3", "d", 1, DiaryNote("x")), photo = "PNG".encodeToByteArray()) }
    }

    @Test
    fun removingAndDeletingAlsoErasesPhotos() = runTest {
        val blobs = MemoryBlobs()
        val repo = repo(blobs)
        repo.start(request)
        repo.add("req-1", Entry("e1", "d", 1, DiaryNote("rash")), photo = tinyJpeg(ByteArray(10)))
        repo.add("req-1", Entry("e2", "f", 2, Food("soup")), photo = tinyJpeg(ByteArray(20)))
        repo.removeEntry("req-1", "e1")
        assertNull(blobs.map["photo/e1"])
        assertEquals(listOf("e2"), repo.records.value.single().entries.map { it.id })
        repo.delete("req-1")
        assertTrue(blobs.map.isEmpty(), blobs.map.keys.toString())
        assertEquals(emptyList(), repo.records.value)
    }

    @Test
    fun anotherKeyCannotReadThePhoto() = runTest {
        val blobs = MemoryBlobs()
        val repo = repo(blobs)
        repo.start(request)
        repo.add("req-1", Entry("e1", "f", 1, Food("soup")), photo = tinyJpeg(ByteArray(10)))
        assertFails { PhotoStore(blobs, generateRecordKey()).get("photo/e1") }
        assertNull(PhotoStore(blobs, generateRecordKey()).get("record/req-1"), "a record is never read as a photo")
    }

    @Test
    fun foreignPhotoRefIsNoPhotoHereAndCannotTouchRecords() = runTest {
        val blobs = MemoryBlobs()
        val repo = repo(blobs)
        repo.start(request)
        // the demo's ref (pavlo, problem 16) and a crafted one that points at the record itself
        repo.add("req-1", Entry("e1", "f", 1, Food("soup", photoRef = "demo-photo-1")))
        repo.add("req-1", Entry("e2", "d", 2, DiaryNote("x", photoRef = "record/req-1")))
        assertNull(repo.photo("demo-photo-1"))
        repo.removeEntry("req-1", "e2")
        repo.removeEntry("req-1", "e1")
        assertEquals(emptyList(), repo.records.value.single().entries, "both removed, the record itself kept")
    }
}
