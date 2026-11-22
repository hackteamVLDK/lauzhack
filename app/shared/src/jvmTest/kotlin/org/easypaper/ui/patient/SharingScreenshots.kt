package org.easypaper.ui.patient

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.p2_how_share
import easypaper.app.shared.generated.resources.p2_next
import easypaper.app.shared.generated.resources.p2_sharing_doctor_title
import easypaper.app.shared.generated.resources.p2_sharing
import kotlinx.coroutines.runBlocking
import org.easypaper.ui.patient.v2.PatientSettings
import org.easypaper.ui.shell.PatientApp
import org.easypaper.ui.shell.Role
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellState
import org.easypaper.ui.shell.ShellTheme
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Screenshots of the patient's Sharing tab and both share sheets (task 48), light and dark, EN / DE / UK, phone
 * width 400 dp. Runs only with EASYPAPER_SCREENSHOTS=<folder> set, so normal test runs stay fast:
 *   EASYPAPER_SCREENSHOTS=$PWD/docs/screenshots/sharing ./gradlew :app:shared:jvmTest --tests '*SharingScreenshots*'
 */
@OptIn(ExperimentalTestApi::class)
class SharingScreenshots {
    @Test
    fun sharingTabAndSheets() {
        val out = System.getenv("EASYPAPER_SCREENSHOTS")?.let(::File) ?: return
        out.mkdirs()
        for (lang in listOf("en", "de", "uk")) for (dark in listOf(false, true)) shoot(out, lang, dark)
    }

    private fun shoot(out: File, lang: String, dark: Boolean) = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag(lang))
        fun setting(value: String? = null) = SavedSetting(value) {}
        val patient = PatientSettings(setting("true"), setting(lang), setting(), setting("Anna"), setting(), setting(),
            setting(), setting(), setting(), setting(), setting())
        val state = ShellState(Role.Patient)
        runBlocking { state.fillWithDemoData() }
        val settings = ShellSettings(dark = dark)
        setContent { Box(Modifier.size(400.dp, 860.dp)) { ShellTheme(settings) { PatientApp(state, settings, patientSettings = patient) } } }

        fun text(s: String) = waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(s).fetchSemanticsNodes().isNotEmpty() }
        fun save(name: String) {
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name-$lang-${if (dark) "dark" else "light"}.png"))
        }
        val (sharing, share, next, how) = runBlocking {
            listOf(getString(Res.string.p2_sharing), getString(Res.string.p2_sharing_doctor_title), getString(Res.string.p2_next), getString(Res.string.p2_how_share))
        }
        text(sharing)
        onAllNodesWithText(sharing).onFirst().performClick()
        text(share)
        save("1-sharing-tab")
        onAllNodesWithText(share).onFirst().performClick()
        text(next)
        save("2-what-to-share")
        onAllNodesWithText(next).onFirst().performScrollTo()
        save("2b-what-to-share-total") // the footer: N entries, M photos
        onAllNodesWithText(next).onFirst().performClick()
        text(how)
        save("3-how-to-share")
    }
}
