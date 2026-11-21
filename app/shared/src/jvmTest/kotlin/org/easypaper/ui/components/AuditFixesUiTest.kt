package org.easypaper.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import org.easypaper.invite.MAX_REQUEST_DAYS
import org.easypaper.ui.doctor.RequestBuilderScreen
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellTheme
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Task 55 (clickability audit, problem 28): items 3 and 6. */
@OptIn(ExperimentalTestApi::class)
class AuditFixesUiTest {
    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @Test
    fun aTapBelowTheTextStillFocusesTheTextArea() = runComposeUiTest {
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                var text by remember { mutableStateOf("") }
                GroupedSection { TextAreaRow(text, { text = it }, placeholder = "Write how you feel", modifier = Modifier.testTag("cell")) }
            }
        }
        // near the bottom of the cell, well below the one-line placeholder
        onNodeWithTag("cell", useUnmergedTree = true).performTouchInput { click(Offset(width / 2f, height - 4f)) }
        onNode(hasSetTextAction() and hasContentDescription("Write how you feel")).assertIsFocused()
    }

    @Test
    fun theStartDateStopsAtTheLimit() = runComposeUiTest {
        setContent { ShellTheme(ShellSettings(dark = false)) { RequestBuilderScreen(back = null) } }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithContentDescription("Start one day later").fetchSemanticsNodes().isNotEmpty() }
        val later = onNodeWithContentDescription("Start one day later")
        repeat(MAX_REQUEST_DAYS) { later.performClick() }
        later.assertIsNotEnabled()
    }
}
