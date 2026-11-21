package org.easypaper.storage

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.easypaper.model.*
import org.easypaper.report.tinyJpeg
import java.io.File
import java.security.KeyStore
import kotlin.test.*

/** On a real phone / emulator: the record key is wrapped by Android Keystore and data survives a restart. */
class AndroidKeystoreDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.filesDir, "easypaper-device-test")
    private val alias = "easypaper-device-test-key"
    private val request = CareRequest(
        id = "req-1", createdAt = 0, doctorName = "Dr", complaint = "secret complaint", startDate = "2026-11-21", days = 2,
        locale = "en", widgets = listOf(FoodWidget("f", "Food")),
    )

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test
    fun keystoreWrappedKeyAndRestart() = runTest {
        val photo = tinyJpeg(ByteArray(300) { 3 })
        androidPatientRepository(dir, AndroidKeystoreWrapper(alias)).apply {
            start(request)
            assertEquals(emptyList(), add("req-1", Entry("e1", "f", 1, Food("soup")), photo = photo))
        }
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(keyStore.containsAlias(alias))
        assertEquals(12 + 32 + 16, File(dir, "record.key.wrapped").length().toInt())

        val again = androidPatientRepository(dir, AndroidKeystoreWrapper(alias))
        again.refresh()
        assertEquals(listOf("e1"), again.records.value.single().entries.map { it.id })
        assertContentEquals(photo, again.photo("photo/e1"))

        // without the Keystore key (app reinstalled, file copied elsewhere) the wrapped key cannot be opened
        keyStore.deleteEntry(alias)
        assertFails { androidPatientRepository(dir, AndroidKeystoreWrapper(alias)) }
    }
}
