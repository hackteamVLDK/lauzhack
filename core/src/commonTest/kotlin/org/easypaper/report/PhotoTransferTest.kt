package org.easypaper.report

import kotlinx.coroutines.test.runTest
import org.easypaper.model.CareRequest
import org.easypaper.model.DiaryNote
import org.easypaper.model.DiaryWidget
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.FoodWidget
import org.easypaper.model.PatientRecord
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class PhotoTransferTest {
    private val report = Report(
        id = "rep-photo", createdAt = 0,
        record = PatientRecord(
            CareRequest(
                id = "req", createdAt = 0, doctorName = "Dr", complaint = "c", startDate = "2026-11-21", days = 1,
                locale = "en", widgets = listOf(FoodWidget("f", "Food"), DiaryWidget("d", "Diary")),
            ),
            listOf(
                Entry("e1", "f", 1, Food("soup", "p-food")),
                Entry("e2", "d", 2, DiaryNote("rash", "p-diary")),
                Entry("e3", "f", 3, Food("tea")),
            ),
        ),
    )
    private val food = ByteArray(5000) { (it * 7).toByte() }
    private val diary = "fake jpeg bytes".encodeToByteArray()

    @Test
    fun photosOpenWithTheReportQr() = runTest {
        val sealed = sealReport(report, mapOf("p-food" to food, "p-diary" to diary))
        assertEquals(setOf("p-food", "p-diary"), sealed.photos.keys)
        assertContentEquals(food, openPhoto(sealed.photos.getValue("p-food"), sealed.qr, "p-food"))
        assertContentEquals(diary, openPhoto(sealed.photos.getValue("p-diary"), sealed.qr, "p-diary"))
        assertEquals(report, openReport(sealed.bytes, sealed.qr))
        assertTrue(sealed.photos.getValue("p-food").size < food.size + 64) // IV + tag, no re-encoding
    }

    @Test
    fun wrongKeyRefOrBytesFail() = runTest {
        val sealed = sealReport(report, mapOf("p-food" to food, "p-diary" to diary))
        val photo = sealed.photos.getValue("p-food")
        assertFails { openPhoto(photo, sealReport(report).qr, "p-food") } // other key
        assertFails { openPhoto(photo, sealed.qr, "p-diary") } // swapped ref
        assertFails { openPhoto(photo.copyOf().also { it[20] = (it[20] + 1).toByte() }, sealed.qr, "p-food") }
        assertFails { openReport(photo, sealed.qr) } // a photo is not the report
        assertFails { openPhoto(sealed.bytes, sealed.qr, "p-food") } // the report is not a photo
    }

    @Test
    fun unreferencedPhotoRejected() = runTest {
        assertFails { sealReport(report, mapOf("p-other" to diary)) }
    }

    @Test
    fun reportWithoutPhotosUnchanged() = runTest {
        val sealed = sealReport(report)
        assertTrue(sealed.photos.isEmpty())
        assertEquals(report, openReport(sealed.bytes, sealed.qr))
    }
}
