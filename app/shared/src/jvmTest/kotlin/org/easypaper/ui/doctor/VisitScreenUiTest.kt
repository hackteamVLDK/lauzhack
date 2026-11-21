package org.easypaper.ui.doctor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellTheme
import org.easypaper.visit.FieldState
import org.easypaper.visit.FormFieldKey
import org.easypaper.visit.demoVisitReport
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Review of task 10: the doctor confirms the text they see, and decisions survive leaving the screen. */
@OptIn(ExperimentalTestApi::class)
class VisitScreenUiTest {
    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun editedHistoryIsShownAndIsWhatGetsConfirmed() = runComposeUiTest {
        val report = demoVisitReport()
        val store = mutableMapOf<String, VisitState>()
        setContent { ShellTheme(ShellSettings(dark = false)) { VisitScreen(report, back = null, store = store) } }
        waitForText("Confirm summary")
        onNode(hasScrollAction()).performScrollToNode(hasText("Confirm summary")) // clicks land only on what is on screen
        onAllNodesWithText("Edit").onFirst().performClick() // the summary's Edit
        waitForText("Save")
        val field = onNode(hasSetTextAction() and hasContentDescription("History")) // the history field's label
        field.performTextClearance()
        field.performTextInput("Morning headache on 4 of 5 days, temples.")
        onNodeWithText("Save").performClick()

        waitForText("Edited by you")
        // The summary section and the form's history field show the same edited text.
        onAllNodesWithText("Morning headache on 4 of 5 days, temples.").assertCountEquals(2)
        onNode(hasScrollAction()).performScrollToNode(hasText("Confirm summary"))
        onNodeWithText("Confirm summary").performClick()
        waitForIdle()
        val history = store.getValue(report.id).form.field(FormFieldKey.HISTORY)
        assertEquals(FieldState.CONFIRMED, history.state)
        assertEquals("Morning headache on 4 of 5 days, temples.", history.value)
    }

    @Test
    fun decisionsSurviveLeavingTheScreen() = runComposeUiTest {
        val report = demoVisitReport()
        val store = mutableMapOf<String, VisitState>()
        var shown by mutableStateOf(true)
        setContent { ShellTheme(ShellSettings(dark = false)) { if (shown) VisitScreen(report, back = null, store = store) } }
        waitForText("Confirm summary")
        onNode(hasScrollAction()).performScrollToNode(hasText("Confirm summary"))
        onNodeWithText("Confirm summary").performClick()
        waitForIdle()
        assertEquals(FieldState.CONFIRMED, store.getValue(report.id).form.field(FormFieldKey.HISTORY).state)
        shown = false
        waitForIdle()
        shown = true
        waitForText(report.record.request.complaint)
        assertEquals(FieldState.CONFIRMED, store.getValue(report.id).form.field(FormFieldKey.HISTORY).state)
        onNode(hasScrollAction()).performScrollToNode(hasText("Undo"))
        onAllNodesWithText("Undo").onFirst().assertExists() // confirmed fields offer Undo, not Confirm
    }
}
