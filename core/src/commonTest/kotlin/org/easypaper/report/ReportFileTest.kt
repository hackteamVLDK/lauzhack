package org.easypaper.report

import kotlinx.coroutines.test.runTest
import org.easypaper.demo.Demo
import org.easypaper.storage.BACKUP_ITERATIONS
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ReportFileTest {
    private val password = "separate password"
    private val photo = tinyJpeg("photo content".encodeToByteArray())

    @Test
    fun reportAndPhotosRoundTrip() = runTest {
        val bytes = sealReportFile(Demo.report, mapOf("demo-photo-1" to photo), password)
        val (report, photos) = openReportFile(bytes, password)
        assertEquals(Demo.report, report)
        assertEquals(setOf("demo-photo-1"), photos.keys)
        assertContentEquals(photo, photos.getValue("demo-photo-1"))
        assertEquals("EPR1", bytes.copyOfRange(0, 4).decodeToString())
        assertEquals(BACKUP_ITERATIONS, (4 until 8).fold(0) { n, i -> (n shl 8) or (bytes[i].toInt() and 0xFF) })
        assertFalse(password in bytes.decodeToString())
        assertFalse("Headache" in bytes.decodeToString())
        assertFalse("photo content" in bytes.decodeToString())
    }

    @Test
    fun wrongPasswordIsRejected() = runTest {
        val bytes = sealReportFile(Demo.report, emptyMap(), password)
        assertFails { openReportFile(bytes, "different password") }
    }

    @Test
    fun changedCiphertextOrHeaderIsRejected() = runTest {
        val bytes = sealReportFile(Demo.report, emptyMap(), password)
        // Ciphertext, salt and a still-supported iteration count are all authenticated.
        for (index in listOf(bytes.lastIndex, 8, 7)) {
            val damaged = bytes.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertFails { openReportFile(damaged, password) }
        }
        assertFailsWith<IllegalArgumentException> { openReportFile(bytes.copyOf(23), password) }
        assertFailsWith<IllegalArgumentException> { openReportFile(bytes.copyOf().also { it[0] = 0 }, password) }
        assertFailsWith<IllegalArgumentException> { openReportFile(bytes.copyOf().also { it[4] = 0x7F }, password) }
    }

    @Test
    fun freshSaltAndPhotoPrivacyMatchQr() = runTest {
        val first = sealReportFile(Demo.report, emptyMap(), password)
        val second = sealReportFile(Demo.report, emptyMap(), password)
        assertFalse(first.copyOfRange(8, 24).contentEquals(second.copyOfRange(8, 24)))
        assertFails { sealReportFile(Demo.report, mapOf("unknown" to photo), password) }
        assertFails { sealReportFile(Demo.report, mapOf("demo-photo-1" to byteArrayOf(1, 2, 3)), password) }
        assertFailsWith<IllegalArgumentException> { sealReportFile(Demo.report, emptyMap(), "short") }
    }
}
