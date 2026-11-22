package org.easypaper.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Keep every stored-photo viewer on the same EXIF-aware rendering path, including patient v2. */
class UprightJpegUsageTest {
    @Test
    fun patientEntryPhotoFormPreviewAndDoctorPhotoUseTheSharedComponent() {
        val root = listOf(File("src/commonMain/kotlin/org/easypaper/ui"),
            File("app/shared/src/commonMain/kotlin/org/easypaper/ui")).first { it.isDirectory }
        for (path in listOf("patient/v2/EntryPhoto.kt", "patient/EntryForms.kt", "doctor/VisitScreen.kt")) {
            val source = File(root, path).readText()
            assertTrue("import org.easypaper.ui.components.UprightJpeg" in source, path)
            assertTrue(Regex("""\bUprightJpeg\s*\(""").containsMatchIn(source), path)
            assertFalse("decodeToImageBitmap" in source, "$path must not bypass EXIF handling")
        }
    }
}
