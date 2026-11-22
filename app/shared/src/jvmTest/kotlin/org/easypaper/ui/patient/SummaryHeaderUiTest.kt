package org.easypaper.ui.patient

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.ui.patient.v2.PatientLanguage
import org.easypaper.ui.patient.v2.PatientSettings
import org.easypaper.ui.patient.v2.SummaryTab
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SummaryHeaderUiTest {
    private fun settings(name: String? = null): PatientSettings {
        val setting = { SavedSetting(null) {} }
        return PatientSettings(setting(), setting(), SavedSetting("") {}, SavedSetting(name) {},
            setting(), setting(), setting(), setting(), setting(), setting(), setting())
    }

    private fun ComposeUiTest.showSummary(patient: PatientSettings, language: String) {
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                PatientLanguage(language) {
                    SummaryTab(emptyList(), patient, LocalDate(2026, 11, 21), TimeZone.UTC,
                        onProfile = {}, onDeveloper = {}, onPlan = {}, onCategory = {}, onAdd = {}, onGallery = {})
                }
            }
        }
    }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun missingAndBlankNamesShowTheDateInsteadOfHi() = runComposeUiTest {
        val patient = settings()
        showSummary(patient, "en")
        val date = "Saturday, 21 November"
        waitForText(date)
        onNodeWithText(date).assertExists()
        onNodeWithText("Hi").assertDoesNotExist()

        runOnIdle { patient.name.update("   ") }
        onNodeWithText(date).assertExists()
        onNodeWithText("Hi").assertDoesNotExist()

        runOnIdle { patient.name.update(" Anna ") }
        waitForText("Hi, Anna")
        onNodeWithText("Hi, Anna").assertExists()
        onNodeWithText(date).assertDoesNotExist()
    }

    @Test
    fun aRussianSummaryWithoutANameShowsALocalizedDate() = runComposeUiTest {
        showSummary(settings(), "ru")
        val date = "суббота, 21 ноября"
        waitForText(date)
        onNodeWithText(date).assertExists()
        onNodeWithText("Здравствуйте").assertDoesNotExist()
        onNodeWithText("Hi").assertDoesNotExist()
    }
}
