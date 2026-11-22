package org.easypaper.ui.patient.v2

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.easypaper.ui.shell.PatientApp
import org.easypaper.ui.shell.Role
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellState
import org.easypaper.ui.shell.ShellTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Task 87: in a desktop window the patient's tabs are a sidebar and Browse has three tiles per row; a phone keeps both. */
@OptIn(ExperimentalTestApi::class)
class WideWindowUiTest {
    private fun ComposeUiTest.showPatient() {
        fun setting(value: String? = null) = SavedSetting(value) {}
        val patient = PatientSettings(setting("true"), setting("en"), setting(), setting("Anna"), setting(), setting(),
            setting(), setting(), setting(), setting(), setting())
        val settings = ShellSettings(dark = false)
        setContent { ShellTheme(settings) { PatientApp(ShellState(Role.Patient), settings, patientSettings = patient) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Browse").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.openBrowse() {
        onAllNodesWithText("Browse").onFirst().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Categories").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.top(text: String) = onNodeWithText(text).getBoundsInRoot().top

    @Test
    fun desktopWindowHasASidebarAndThreeTilesPerRow() = runDesktopComposeUiTest(1440, 900) {
        showPatient()
        onNodeWithText("Easy Paper").assertExists()
        val sidebarBrowse = onAllNodesWithText("Browse").onFirst().getBoundsInRoot()
        assertTrue(sidebarBrowse.left.value < 260f, "Browse is in the sidebar on the left")
        openBrowse()
        assertEquals(top("Blood pressure"), top("Food"), "Blood pressure, Water and Food share one row")
        assertEquals(top("Water"), top("Food"))
    }

    @Test
    fun phoneKeepsTheTabBarAndTwoTilesPerRow() = runDesktopComposeUiTest(400, 860) {
        showPatient()
        assertTrue(onAllNodesWithText("Easy Paper").fetchSemanticsNodes().isEmpty(), "no sidebar on a phone")
        openBrowse()
        assertEquals(top("Blood pressure"), top("Water"))
        assertTrue(top("Food") > top("Water"), "Food starts the second row")
    }

}
