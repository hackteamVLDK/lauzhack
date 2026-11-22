package org.easypaper.ui.patient

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import org.easypaper.ui.patient.v2.Onboarding
import org.easypaper.ui.patient.v2.PatientLanguage
import org.easypaper.ui.patient.v2.PatientSettings
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TourUiTest {
    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.clickAction(text: String) {
        onNodeWithText(text).performScrollTo().performClick()
    }

    private fun ComposeUiTest.openTour() {
        val setting = { SavedSetting(null) {} }
        val patient = PatientSettings(setting(), setting(), setting(), setting(), setting(), setting(),
            setting(), setting(), setting(), setting(), setting())
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                PatientLanguage("en") { Onboarding(patient, saving = false, error = false, onFinish = {}) }
            }
        }
        waitForText("Your health diary")
        clickAction("Get started")
        waitForText("Track what matters")
    }

    @Test
    fun nextShowsTheDoctorPlanAndSkipOpensTheProfile() = runComposeUiTest {
        openTour()
        onNodeWithText("Track what matters").assertIsDisplayed()
        onNodeWithContentDescription("Page 1 of 4").assertExists()

        clickAction("Next")
        waitForText("A plan from your doctor")
        onNodeWithText("A plan from your doctor").assertIsDisplayed()
        onNodeWithContentDescription("Page 2 of 4").assertExists()

        clickAction("Skip")
        waitForText("Welcome")
        onNodeWithText("Welcome").assertIsDisplayed()
        onNodeWithText("Continue").assertExists()
        onNodeWithText("Name (optional)").assertExists()
    }

    @Test
    fun swipesAndNextReachTheLastPageAndBackPreservesTheTourPosition() = runComposeUiTest {
        openTour()
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .performTouchInput { swipeLeft() }
        waitForText("A plan from your doctor")
        onNodeWithContentDescription("Page 2 of 4").assertExists()

        clickAction("Next")
        waitForText("Share at the visit")
        onNodeWithContentDescription("Page 3 of 4").assertExists()
        clickAction("Next")
        waitForText("Private on your phone")
        onNodeWithContentDescription("Page 4 of 4").assertExists()
        onNodeWithText("Next").assertDoesNotExist()
        clickAction("Continue")
        waitForText("Welcome")

        clickAction("Continue")
        waitForText("Privacy & data")
        clickAction("Back")
        waitForText("Welcome")
        clickAction("Back")
        waitForText("Private on your phone")
        onNodeWithContentDescription("Page 4 of 4").assertExists()
        clickAction("Back")
        waitForText("Your health diary")
    }
}
