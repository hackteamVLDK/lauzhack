package org.easypaper.ui.doctor

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.easypaper.invite.inviteLink
import org.easypaper.model.CareRequest
import org.easypaper.ui.shell.PatientApp
import org.easypaper.ui.shell.Role
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellState
import org.easypaper.ui.shell.ShellTheme
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Task 5 criterion: the patient opens the invite and sees exactly the widgets the doctor chose. */
@OptIn(ExperimentalTestApi::class)
class InviteFlowUiTest {
    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.type(fieldDescription: String, text: String) =
        onNode(hasSetTextAction() and hasContentDescription(fieldDescription)).performTextInput(text)

    @Test
    fun doctorBuildsTheRequestAndThePatientSeesExactlyItsWidgets() {
        var created: CareRequest? = null
        runComposeUiTest {
            val settings = ShellSettings(dark = false)
            setContent { ShellTheme(settings) { RequestBuilderScreen(back = null, onCreated = { created = it }) } }
            waitForText("New request")
            type("For example: headache in the morning for two weeks", "Headache in the morning")
            type("Dr. Anna Keller", "Dr. Weber")
            onNode(hasScrollAction()).performScrollToNode(hasText("Create invite"))
            onNodeWithText("Create invite").performClick()
            waitUntil(timeoutMillis = 10_000) { created != null }
            waitForText("Copy link") // the invite sheet with QR and link
        }
        val request = assertNotNull(created)
        assertEquals("Dr. Weber", request.doctorName)

        runComposeUiTest {
            val state = ShellState(Role.Patient)
            val settings = ShellSettings(dark = false)
            setContent { ShellTheme(settings) { PatientApp(state, settings) } }
            waitForText("Open invite")
            onNodeWithText("Open invite").performClick()
            waitForText("Paste the link or code from your doctor")
            type("Paste the link or code from your doctor", inviteLink(request))
            waitForText("Start recording") // a valid invite shows what it asks for
            onNodeWithText("Start recording").performClick()
            waitUntil(timeoutMillis = 10_000) { state.repository.records.value.isNotEmpty() }
            assertEquals(request, state.repository.records.value.single().request)
            for (widget in request.widgets) {
                onNode(hasScrollAction()).performScrollToNode(hasText(widget.title))
                onNodeWithText(widget.title).assertExists()
            }
        }
    }
}
