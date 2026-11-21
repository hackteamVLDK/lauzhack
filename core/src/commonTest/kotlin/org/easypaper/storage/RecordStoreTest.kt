package org.easypaper.storage

import kotlinx.coroutines.test.runTest
import org.easypaper.model.CareRequest
import org.easypaper.model.DiaryNote
import org.easypaper.model.DiaryWidget
import org.easypaper.model.Entry
import org.easypaper.model.PatientRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RecordStoreTest {
    private class MemoryBlobs : BlobStore {
        val map = mutableMapOf<String, ByteArray>()
        override suspend fun put(key: String, bytes: ByteArray) { map[key] = bytes }
        override suspend fun get(key: String) = map[key]
        override suspend fun keys() = map.keys.sorted()
        override suspend fun delete(key: String) { map.remove(key) }
    }

    private fun record(id: String) = PatientRecord(
        CareRequest(
            id = id, createdAt = 0, doctorName = "Dr. Test", complaint = "secret complaint",
            startDate = "2026-11-21", days = 3, locale = "en", widgets = listOf(DiaryWidget("d", "Diary")),
        ),
        listOf(Entry("e1", "d", 1, DiaryNote("felt dizzy"))),
    )

    @Test
    fun saveLoadList() = runTest {
        val store = RecordStore(MemoryBlobs(), generateRecordKey())
        store.save(record("a"))
        store.save(record("b"))
        assertEquals(record("a"), store.load("a"))
        assertEquals(listOf("a", "b"), store.list())
        assertNull(store.load("missing"))
    }

    @Test
    fun bytesAtRestAreNotPlaintext() = runTest {
        val blobs = MemoryBlobs()
        RecordStore(blobs, generateRecordKey()).save(record("a"))
        assertFalse("secret complaint" in blobs.map.getValue("record/a").decodeToString())
    }

    @Test
    fun otherKeyCannotDecrypt() = runTest {
        val blobs = MemoryBlobs()
        RecordStore(blobs, generateRecordKey()).save(record("a"))
        assertFails { RecordStore(blobs, generateRecordKey()).load("a") }
    }

    @Test
    fun damagedBytesFail() = runTest {
        val blobs = MemoryBlobs()
        val store = RecordStore(blobs, generateRecordKey())
        store.save(record("a"))
        val bytes = blobs.map.getValue("record/a")
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
        assertFails { store.load("a") }
    }

    @Test
    fun blobMovedToAnotherIdFails() = runTest {
        val blobs = MemoryBlobs()
        val store = RecordStore(blobs, generateRecordKey())
        store.save(record("a"))
        blobs.map["record/b"] = blobs.map.getValue("record/a")
        assertFails { store.load("b") }
    }

    @Test
    fun wrongKeySizeRejected() {
        assertFails { RecordStore(MemoryBlobs(), ByteArray(16)) }
    }

    @Test
    fun deleteErasesOnlyThatRecord() = runTest {
        val blobs = MemoryBlobs()
        val store = RecordStore(blobs, generateRecordKey())
        store.save(record("a"))
        store.save(record("b"))
        store.delete("a")
        assertNull(store.load("a"))
        assertEquals(listOf("b"), store.list())
        assertFalse("record/a" in blobs.map)
        store.delete("a") // again
        store.delete("never-existed")
        assertEquals(record("b"), store.load("b"))
    }
}
