package org.easypaper.ui.shell

import kotlinx.coroutines.test.runTest
import org.easypaper.demo.Demo
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.FoodWidget
import org.easypaper.storage.desktopPatientRepository
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Task 27: what the patient saves in the app is there after a restart (a new ShellState over the same disk store). */
class DeviceStorageTest {
    @Test
    fun anEntryAndItsPhotoSurviveARestart() = runTest {
        val dir = Files.createTempDirectory("easypaper-restart").toFile()
        try {
            val first = ShellState(Role.Patient, desktopPatientRepository(dir))
            first.fillWithDemoData()
            val food = Demo.request.widgets.first { it is FoodWidget }.id
            val photo = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB), "jpeg", it) }.toByteArray()
            assertEquals(emptyList(), first.repository.add(Demo.request.id, Entry("e99", food, Demo.record.entries.maxOf { it.at }, Food()), photo))

            val afterRestart = ShellState(Role.Patient, desktopPatientRepository(dir)).also { it.repository.refresh() }
            val record = afterRestart.repository.records.value.single()
            assertEquals(Demo.record.entries.size + 1, record.entries.size)
            assertNotNull(afterRestart.repository.photo("photo/e99"), "the photo is on disk too")
        } finally {
            dir.deleteRecursively()
        }
    }
}
