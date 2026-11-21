package org.easypaper.storage

import kotlinx.coroutines.test.runTest
import org.easypaper.model.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BackupTest {
    private val fast = 10_000 // minimum allowed; the default is BACKUP_ITERATIONS
    private val pass = "correct horse battery"

    private fun record(id: String) = PatientRecord(
        CareRequest(
            id = id, createdAt = 0, doctorName = "Dr", complaint = "secret complaint $id", startDate = "2026-11-17",
            days = 2, locale = "en", widgets = listOf(DiaryWidget("d", "Diary"), FoodWidget("f", "Food")),
        ),
        listOf(Entry("e1", "d", 1, DiaryNote("felt dizzy")), Entry("e2", "f", 2, Food("soup", "p1"))),
    )
    private val records = listOf(record("a"), record("b"))
    private val photos = mapOf("p1" to ByteArray(2000) { (it * 31).toByte() })

    @Test
    fun restoresRecordsAndPhotos() = runTest {
        val file = exportBackup(records, photos, pass, fast)
        val restored = importBackup(file, pass)
        assertEquals(records, restored.records)
        assertEquals(photos.keys, restored.photos.keys)
        assertContentEquals(photos.getValue("p1"), restored.photos.getValue("p1"))
    }

    @Test
    fun wrongPassphraseOrDamageFails() = runTest {
        val file = exportBackup(records, photos, pass, fast)
        assertFails { importBackup(file, "correct horse battery!") }
        assertFails { importBackup(file.copyOf().also { it[file.size - 5] = (it[file.size - 5] + 1).toByte() }, pass) }
        // raising the iteration count in the header changes the key and the bound header
        assertFails { importBackup(file.copyOf().also { it[6] = (it[6] + 1).toByte() }, pass) }
        assertFails { importBackup(file.copyOf().also { it[10] = (it[10] + 1).toByte() }, pass) } // salt
    }

    @Test
    fun foreignFilesAndWeakInputRejected() = runTest {
        assertFailsWith<IllegalArgumentException> { importBackup("PK\u0003\u0004 zip file".encodeToByteArray(), pass) }
        assertFailsWith<IllegalArgumentException> { exportBackup(records, photos, "short", fast) }
        assertFailsWith<IllegalArgumentException> { exportBackup(records, photos, pass, 1) }
        val bomb = exportBackup(records, photos, pass, fast).also { it[4] = 0x7F } // ~2 billion iterations
        assertFailsWith<IllegalArgumentException> { importBackup(bomb, pass) }
    }

    @Test
    fun fileRevealsNothingAndIsNeverTheSameTwice() = runTest {
        val first = exportBackup(records, photos, pass, fast)
        val second = exportBackup(records, photos, pass, fast)
        assertFalse("secret complaint" in first.decodeToString() || "felt dizzy" in first.decodeToString())
        assertFalse(first.contentEquals(second))
        assertEquals("EPB1", first.copyOfRange(0, 4).decodeToString())
    }
}
