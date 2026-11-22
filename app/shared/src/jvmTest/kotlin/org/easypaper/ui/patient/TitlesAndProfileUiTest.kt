package org.easypaper.ui.patient

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.easypaper.ui.components.LargeTitleScaffold
import org.easypaper.ui.components.Sheet
import androidx.compose.ui.test.onRoot
import org.easypaper.ui.format.today
import org.easypaper.ui.patient.v2.PatientSettings
import org.easypaper.ui.patient.v2.ProfileSheet
import org.easypaper.ui.shell.Role
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellState
import org.easypaper.ui.shell.ShellTheme
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Task 59: large titles keep their words whole; profile fields are named once. */
@OptIn(ExperimentalTestApi::class)
class TitlesAndProfileUiTest {
    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @Test
    fun aScreenTitleShrinksInsteadOfBreakingAWord() = runComposeUiTest {
        val title = "Programme du médecin"
        setContent {
            ShellTheme(ShellSettings(dark = false, largeText = true)) {
                Box(Modifier.width(360.dp)) { LargeTitleScaffold(title) { } }
            }
        }
        val node = onAllNodesWithText(title).fetchSemanticsNodes()
            .first { it.config.getOrNull(SemanticsProperties.Heading) != null }
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        val layout = results.first()
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue(title[end - 1].isWhitespace() || title.getOrNull(end)?.isWhitespace() == true,
                "line ${line + 1} ends inside a word: '${title.substring(0, end)}'")
        }
    }

    @Test
    fun profileFieldsShowTheirNameOnceAndAnExampleForTheDate() = runComposeUiTest {
        fun setting(value: String? = null) = SavedSetting(value) {}
        val settings = PatientSettings(setting("true"), setting("en"), setting(), setting(), setting(), setting(),
            setting(), setting(), setting(), setting(), setting())
        val state = ShellState(Role.Patient)
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                ProfileSheet(true, settings, state.repository, today(), onDismiss = {}, onDeleted = {})
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Date of birth").fetchSemanticsNodes().isNotEmpty() }
        for (name in listOf("Name", "Date of birth", "Height (cm, optional)", "Weight (kg, optional)", "Allergies (optional)")) {
            assertEquals(1, onAllNodesWithText(name, useUnmergedTree = true).fetchSemanticsNodes().size,
                "'$name' is written more than once (label and placeholder)")
            onNode(hasSetTextAction() and hasContentDescription(name)).assertExists("no field named '$name'")
        }
        onNodeWithText("14.03.1958").assertExists()
    }

    @Test
    fun theSheetTitleAndItsButtonDoNotOverlapAt200Percent() = runComposeUiTest {
        setContent {
            ShellTheme(ShellSettings(dark = false, largeText = true)) {
                Box(Modifier.width(360.dp)) { Sheet(true, "Профиль", "Готово", onDismiss = {}) { } }
            }
        }
        val title = onNodeWithText("Профиль", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val done = onNodeWithText("Готово", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(title.right <= done.left, "title $title runs into the button $done")
    }

    @Test
    fun aShortSheetTitleStaysCentered() = runComposeUiTest {
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                Box(Modifier.width(400.dp)) { Sheet(true, "Profile", "Done", onDismiss = {}) { } }
            }
        }
        val title = onNodeWithText("Profile", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val root = onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.center.x, title.center.x, 1f)
    }
}
