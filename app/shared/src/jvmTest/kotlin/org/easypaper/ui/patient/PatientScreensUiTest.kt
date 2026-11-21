package org.easypaper.ui.patient

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.runBlocking
import org.easypaper.ui.shell.PatientApp
import org.easypaper.ui.shell.Role
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellState
import org.easypaper.ui.shell.ShellTheme
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.patient.v2.PatientSettings
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Taps through the real patient screens on the desktop target (English strings). */
@OptIn(ExperimentalTestApi::class)
class PatientScreensUiTest {
    private fun settingsWithoutOnboarding(): PatientSettings {
        fun setting(value: String? = null) = SavedSetting(value) {}
        return PatientSettings(setting("true"), setting("en"), setting(), setting(), setting(), setting(),
            setting(), setting(), setting(), setting(), setting())
    }
    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun planOpensExistingFormsAndFoodOffersAPhoto() = runComposeUiTest {
        val state = ShellState(Role.Patient)
        runBlocking { state.fillWithDemoData() }
        val settings = ShellSettings(dark = false)
        val patient = settingsWithoutOnboarding()
        setContent { ShellTheme(settings) { PatientApp(state, settings, patientSettings = patient) } }

        waitForText("Dr. Demo")
        onNodeWithText("Dr. Demo").performClick()

        waitForText("Add blood pressure")
        onNodeWithText("Add blood pressure").performClick()
        waitForText("Upper value")
        onNodeWithText("Lower value").assertExists()
        onNodeWithText("Cancel").performClick()

        onNode(hasScrollAction()).performScrollToNode(hasText("+ Add a meal"))
        onNodeWithText("+ Add a meal").performClick()
        waitForText("What did you eat or drink?")
        onNodeWithText("Choose photo").assertExists() // Desktop: file dialog; the camera is Android only
        onNodeWithText("A photo is optional.").assertExists()
    }

    @Test
    fun medicinesRowOpensTheMedicinesScreen() = runComposeUiTest {
        val state = ShellState(Role.Patient)
        runBlocking { state.fillWithDemoData() }
        val settings = ShellSettings(dark = false)
        val patient = settingsWithoutOnboarding()
        setContent { ShellTheme(settings) { PatientApp(state, settings, patientSettings = patient) } }

        waitForText("Medicines")
        onNode(hasScrollAction()).performScrollToNode(hasText("Medicines"))
        onNodeWithText("Medicines").performClick()
        waitForText("Add Data")
        onNodeWithText("Add Data").performClick()
        waitForText("Type it in") // only the medicines screen of task 31 has this action
    }
}
