package org.easypaper.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Task 49: the doctor types the clinic's access code once; AI calls then send it (ShellSettings.aiToken). */
@OptIn(ExperimentalTestApi::class)
class ClinicCodeUiTest {
    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @Test
    fun theDoctorsCodeGoesToTheSettings() = runComposeUiTest {
        val settings = ShellSettings(dark = false)
        val doctor = ShellState(Role.Doctor)
        setContent {
            val scope = rememberCoroutineScope()
            ShellTheme(settings) {
                Column {
                    DeveloperMenuContent(doctor, settings, onOpenCatalog = {}, onSwitchRole = null, onDone = {}, scope = scope)
                }
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Clinic access code").fetchSemanticsNodes().isNotEmpty() }
        onNode(hasSetTextAction() and hasContentDescription("Clinic access code")).performTextInput("  c0de-123  ")
        waitForIdle()
        assertEquals("c0de-123", settings.aiToken)
    }

    @Test
    fun noCodeFieldForThePatient() = runComposeUiTest {
        val settings = ShellSettings(dark = false)
        setContent {
            val scope = rememberCoroutineScope()
            ShellTheme(settings) {
                Column { DeveloperMenuContent(ShellState(Role.Patient), settings, onOpenCatalog = {}, onSwitchRole = null, onDone = {}, scope = scope) }
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Design catalog").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodesWithText("Clinic access code").fetchSemanticsNodes().isEmpty())
    }
}
