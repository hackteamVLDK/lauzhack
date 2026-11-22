package org.easypaper.ui




import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest

import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.p2_browse
import easypaper.app.shared.generated.resources.p2_sharing
import easypaper.app.shared.generated.resources.p2_summary
import kotlinx.coroutines.runBlocking
import org.easypaper.ui.patient.v2.PatientSettings
import org.easypaper.ui.shell.DoctorApp
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
 * The apps in a desktop window (task 87): 1440 x 900 dp, light and dark, with the demo diary. Runs only with
 * EASYPAPER_SCREENSHOTS=<folder> set:
 *   EASYPAPER_SCREENSHOTS=$PWD/docs/screenshots/desktop ./gradlew :app:shared:jvmTest --tests '*WideScreenshots*'
 */
@OptIn(ExperimentalTestApi::class)
class WideScreenshots {
    @Test
    fun patientAndDoctorInAWideWindow() {
        val out = System.getenv("EASYPAPER_SCREENSHOTS")?.let(::File) ?: return
        out.mkdirs()
        Locale.setDefault(Locale.ENGLISH)
        for (dark in listOf(false, true)) {
            patient(out, dark)
            doctor(out, dark)
        }
    }

    private fun name(out: File, screen: String, dark: Boolean) = File(out, "$screen-${if (dark) "dark" else "light"}.png")

    private fun patient(out: File, dark: Boolean) = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        fun setting(value: String? = null) = SavedSetting(value) {}
        val patient = PatientSettings(setting("true"), setting("en"), setting(), setting("Anna"), setting(), setting(),
            setting(), setting(), setting(), setting(), setting())
        val state = ShellState(Role.Patient)
        runBlocking { state.fillWithDemoData() }
        val settings = ShellSettings(dark = dark)
        setContent { ShellTheme(settings) { PatientApp(state, settings, patientSettings = patient) } }
        val tabs = runBlocking { listOf(getString(Res.string.p2_summary), getString(Res.string.p2_browse), getString(Res.string.p2_sharing)) }
        for ((index, tab) in tabs.withIndex()) {
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(tab).fetchSemanticsNodes().isNotEmpty() }
            onAllNodesWithText(tab).onFirst().performClick()
            waitForIdle()
            Thread.sleep(600) // tab crossfade
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", name(out, "patient-${index + 1}-${tab.lowercase()}", dark))
        }
    }

    private fun doctor(out: File, dark: Boolean) = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = ShellState(Role.Doctor)
        runBlocking { state.fillWithDemoData() }
        val settings = ShellSettings(dark = dark)
        setContent { ShellTheme(settings) { DoctorApp(state, settings) } }
        waitForIdle()
        Thread.sleep(600)
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", name(out, "doctor-1-home", dark))
    }
}

private const val WIDTH = 1440
private const val HEIGHT = 900
