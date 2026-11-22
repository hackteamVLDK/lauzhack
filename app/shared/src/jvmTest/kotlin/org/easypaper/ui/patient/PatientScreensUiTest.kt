package org.easypaper.ui.patient

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

        waitForText("Browse")
        onNodeWithText("Browse").performClick()
        waitForText("Medicines")
        onNode(hasScrollAction()).performScrollToNode(hasText("Medicines"))
        onNodeWithText("Medicines").performClick()
        waitForText("Add Data")
        onNodeWithText("Add Data").performClick()
        waitForText("Type it in") // only the medicines screen of task 31 has this action
    }

    @Test
    fun onboardingCreatesLocalProfileAndLeavesSummaryEmpty() = runComposeUiTest {
        val state = ShellState(Role.Patient)
        val settings = ShellSettings(dark = false)
        val patient = settingsWithoutOnboarding().also { it.onboarding.update(null) }
        setContent { ShellTheme(settings) { PatientApp(state, settings, patientSettings = patient) } }
        waitForText("Your health diary")
        onNode(hasScrollAction()).performScrollToNode(hasText("Get started"))
        onNodeWithText("Get started").performClick()
        waitForText("Track what matters")
        onNodeWithText("Skip").performScrollTo().performClick()
        waitForText("Welcome")
        onNodeWithText("Continue with Google").assertIsNotEnabled()
        onNodeWithText("Continue with email").assertIsNotEnabled()
        onAllNodesWithText("Soon", useUnmergedTree = true).assertCountEquals(2)
        onNodeWithText("Continue").performClick()
        waitForText("Privacy & data")
        onNodeWithText("Language").assertExists()
        onNode(hasScrollAction()).performScrollToNode(hasText("Continue"))
        onNodeWithText("Continue").performClick()
        waitForText("What do you want to track?")
        assertEquals("true", patient.onboarding.value)
        assertTrue(patient.pinnedCategories.isEmpty())
        assertEquals(null, patient.pinned.value)
        assertTrue(state.repository.records.value.all { it.entries.isEmpty() })
        onNodeWithContentDescription("0 days in a row. Today: 0 entries. Add your first entry today").assertExists()
        val date = java.time.LocalDate.now().format(
            java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
        onNodeWithText(date).assertExists()
    }

    @Test
    fun summaryHasAnAddWidgetsButtonWithoutACategoryCarousel() = runComposeUiTest {
        val state = ShellState(Role.Patient)
        val settings = ShellSettings(dark = false)
        val patient = settingsWithoutOnboarding().also { it.pinned.update("Water") }
        setContent { ShellTheme(settings) { PatientApp(state, settings, patientSettings = patient) } }

        waitForText("Your widgets")
        onNode(hasScrollAction()).performScrollToNode(hasText("Add widgets"))
        val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button)
        onNode(hasText("Add widgets") and button).assertIsDisplayed().assertHasClickAction()
        onNodeWithText("Added").assertDoesNotExist()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).assertCountEquals(0)

        onNode(hasText("Add widgets") and button).performClick()
        waitForText("Choose a category to add it to Summary.")
        onNodeWithText("Added").assertExists() // The gallery still marks the pinned Water widget.
    }

    @Test
    fun galleryAddsAWidgetAndEditingRemovesIt() = runComposeUiTest {
        val state = ShellState(Role.Patient)
        val settings = ShellSettings(dark = false)
        val patient = settingsWithoutOnboarding()
        setContent { ShellTheme(settings) { PatientApp(state, settings, patientSettings = patient) } }
        waitForText("What do you want to track?")
        onAllNodes(hasContentDescription("Add widgets")).onFirst().performClick()
        waitForText("Choose a category to add it to Summary.")
        onNodeWithText("Water").performClick()
        waitForText("Your widgets")
        assertEquals("Water", patient.pinned.value)
        onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Edit"))
        onNodeWithText("Edit").performClick()
        onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasContentDescription("Remove Water widget"))
        onNodeWithContentDescription("Remove Water widget").performClick()
        waitForText("What do you want to track?")
        assertEquals("", patient.pinned.value)
    }
}
