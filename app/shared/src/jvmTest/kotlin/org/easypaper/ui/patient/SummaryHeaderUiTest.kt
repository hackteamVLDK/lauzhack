package org.easypaper.ui.patient

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
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
                        onProfile = {}, onDeveloper = {}, onPlan = {}, onCategory = {}, onGallery = {}, onOpenInvite = {})
                }
            }
        }
    }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun dateAlwaysAppearsAboveTheGreetingAndTrimmedName() = runComposeUiTest {
        val patient = settings()
        showSummary(patient, "en")
        val date = "Saturday, 21 November"
        waitForText(date)
        onNodeWithText(date).assertExists()
        val greetings = listOf("Good morning", "Good afternoon", "Good evening")
        val greeting = onNode(hasText(greetings[0]) or hasText(greetings[1]) or hasText(greetings[2]))
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        onNodeWithText(greeting).assertExists()

        runOnIdle { patient.name.update("   ") }
        onNodeWithText(date).assertExists()
        onNodeWithText(greeting).assertExists()

        runOnIdle { patient.name.update(" Anna ") }
        waitForText("$greeting, Anna")
        onNodeWithText("$greeting, Anna").assertExists()
        onNodeWithText(date).assertExists()
        val dateBounds = onNodeWithText(date).fetchSemanticsNode().boundsInRoot
        val titleBounds = onNodeWithText("$greeting, Anna").fetchSemanticsNode().boundsInRoot
        kotlin.test.assertTrue(dateBounds.bottom <= titleBounds.top)
    }

    @Test
    fun aRussianSummaryShowsTheLocalizedDateWithAndWithoutAName() = runComposeUiTest {
        val patient = settings()
        showSummary(patient, "ru")
        val date = "суббота, 21 ноября"
        waitForText(date)
        onNodeWithText(date).assertExists()
        val greetings = listOf("Доброе утро", "Добрый день", "Добрый вечер")
        val greeting = onNode(hasText(greetings[0]) or hasText(greetings[1]) or hasText(greetings[2]))
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        runOnIdle { patient.name.update(" Анна ") }
        waitForText("$greeting, Анна")
        onNodeWithText(date).assertExists()
        onNodeWithText("$greeting, Анна").assertExists()
    }
}
