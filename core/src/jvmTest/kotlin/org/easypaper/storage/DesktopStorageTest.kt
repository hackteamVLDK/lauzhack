package org.easypaper.storage

import kotlinx.coroutines.test.runTest
import org.easypaper.model.*
import org.easypaper.report.tinyJpeg
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.*

/** Desktop storage on a real disk (temporary folder instead of ~/.easypaper). */
class DesktopStorageTest {
    private val dir: File = Files.createTempDirectory("easypaper").toFile()
    private val request = CareRequest(
        id = "req-1", createdAt = 0, doctorName = "Dr", complaint = "secret complaint", startDate = "2026-11-21", days = 3,
        locale = "en", widgets = listOf(FoodWidget("f", "Food"), DiaryWidget("d", "Diary")),
    )

    @AfterTest
    fun cleanUp() { dir.deleteRecursively() }

    @Test
    fun recordAndPhotoSurviveARestart() = runTest {
        val photo = tinyJpeg(ByteArray(500) { (it % 200).toByte() })
        desktopPatientRepository(dir).apply {
            start(request)
            assertEquals(emptyList(), add("req-1", Entry("e1", "f", 1, Food("soup")), photo = photo))
            add("req-1", Entry("e2", "d", 2, DiaryNote("felt dizzy")))
        }
        // "restart": new objects over the same folder, key read back from the key file
        val again = desktopPatientRepository(dir)
        assertEquals(emptyList(), again.records.value)
        again.refresh()
        val record = again.records.value.single()
        assertEquals(listOf("e1", "e2"), record.entries.map { it.id })
        assertContentEquals(photo, again.photo("photo/e1"))
    }

    @Test
    fun filesRevealNothingAndNeedTheKey() = runTest {
        desktopPatientRepository(dir).apply {
            start(request)
            add("req-1", Entry("e1", "d", 1, DiaryNote("felt dizzy")))
        }
        val dataFiles = File(dir, "data").listFiles().orEmpty()
        assertTrue(dataFiles.isNotEmpty())
        dataFiles.forEach { f ->
            val text = f.readBytes().decodeToString()
            assertFalse("secret complaint" in text || "felt dizzy" in text, "plaintext in ${f.name}")
        }
        // without the key file a fresh key is made, and the old data cannot be decrypted
        File(dir, "record.key").delete()
        val stranger = desktopPatientRepository(dir)
        assertFails { stranger.refresh() }
    }

    @Test
    fun keyFileIsOwnerOnly() = runTest {
        val keyFile = File(dir, "record.key")
        val key = loadOrCreateDesktopKey(keyFile)
        assertEquals(32, key.size)
        assertContentEquals(key, loadOrCreateDesktopKey(keyFile)) // same key next time
        val posix = Files.getFileAttributeView(keyFile.toPath(), PosixFileAttributeView::class.java)
        if (posix != null) {
            assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), posix.readAttributes().permissions())
        }
        assertEquals(listOf("record.key"), dir.list()!!.toList()) // no temp file left behind
    }

    @Test
    fun fileBlobStoreBasics() = runTest {
        val store = FileBlobStore(File(dir, "blobs"))
        store.put("photo/e1", byteArrayOf(1, 2, 3))
        store.put("record/req:1 ü", byteArrayOf(4))
        store.put("photo/e1", byteArrayOf(9)) // replace
        assertContentEquals(byteArrayOf(9), store.get("photo/e1"))
        assertEquals(setOf("photo/e1", "record/req:1 ü"), store.keys().toSet())
        store.delete("photo/e1")
        store.delete("photo/e1")
        assertNull(store.get("photo/e1"))
        assertEquals(listOf("record/req:1 ü"), store.keys())
        assertEquals(1, File(dir, "blobs").list()!!.size) // no temp files left
    }
}
