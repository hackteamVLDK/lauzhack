package org.easypaper.storage

import kotlinx.coroutines.test.runTest
import org.easypaper.model.*
import org.easypaper.report.tinyJpeg
import java.io.File
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*

/**
 * Android storage on the host's real disk. Android Keystore does not exist on the host, so a software AES-GCM
 * wrapper stands in for it; the Keystore wrapper itself runs in the device test.
 */
class AndroidStorageHostTest {
    private class SoftwareWrapper(val secret: ByteArray = ByteArray(32) { it.toByte() }) : KeyWrapper {
        var unwraps = 0
        override fun wrap(key: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES")) }
            return c.iv + c.doFinal(key)
        }
        override fun unwrap(wrapped: ByteArray): ByteArray {
            unwraps++
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, wrapped, 0, 12))
            return c.doFinal(wrapped, 12, wrapped.size - 12)
        }
    }

    private val dir: File = Files.createTempDirectory("easypaper-android").toFile()
    private val request = CareRequest(
        id = "req-1", createdAt = 0, doctorName = "Dr", complaint = "secret complaint", startDate = "2026-11-21", days = 2,
        locale = "en", widgets = listOf(FoodWidget("f", "Food")),
    )

    @AfterTest
    fun cleanUp() { dir.deleteRecursively() }

    @Test
    fun recordAndPhotoSurviveARestart() = runTest {
        val wrapper = SoftwareWrapper()
        val photo = tinyJpeg(ByteArray(300) { 5 })
        androidPatientRepository(dir, wrapper).apply {
            start(request)
            add("req-1", Entry("e1", "f", 1, Food("soup")), photo = photo)
        }
        val again = androidPatientRepository(dir, wrapper)
        again.refresh()
        assertEquals(listOf("e1"), again.records.value.single().entries.map { it.id })
        assertContentEquals(photo, again.photo("photo/e1"))
        assertEquals(1, wrapper.unwraps) // second start unwrapped the stored key instead of making a new one
    }

    @Test
    fun keyFileIsWrappedAndUselessWithoutTheWrapperKey() = runTest {
        androidPatientRepository(dir, SoftwareWrapper()).apply {
            start(request)
            add("req-1", Entry("e1", "f", 1, Food("soup")))
        }
        val keyFile = File(dir, "record.key.wrapped")
        assertEquals(12 + 32 + 16, keyFile.length().toInt()) // IV + wrapped key + tag, never the raw key
        File(dir, "data").listFiles()!!.forEach { assertFalse("secret complaint" in it.readBytes().decodeToString()) }
        // a different Keystore key (other phone, reinstalled app) cannot unwrap it
        assertFails { androidPatientRepository(dir, SoftwareWrapper(ByteArray(32) { 9 })) }
    }
}
