package org.easypaper.ui.patient

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.easypaper.model.EntryPayload
import org.easypaper.model.Measurement
import org.easypaper.model.MeasurementsWidget
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellTheme
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Task 61: the person records a weight or an own measurement without a doctor's widget. */
@OptIn(ExperimentalTestApi::class)
class MeasurementsFormUiTest {
    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @Test
    fun weightWithADecimalCommaAndAnOwnMeasurement() = runComposeUiTest {
        var saved: EntryPayload? = null
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                Column { EntryForm(MeasurementsWidget("personal-measurements", "Body measurements"), emptyList(), false) { p, _ -> saved = p } }
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Save").fetchSemanticsNodes().isNotEmpty() }

        // nothing typed: a hint, nothing saved
        onNodeWithText("Save").performClick()
        onNodeWithText("Enter a number.").assertExists()
        assertNull(saved)

        onNode(hasSetTextAction() and hasContentDescription("Weight")).performTextInput("72,5")
        onNodeWithText("Save").performClick()
        assertEquals(Measurement.weight(72.5), saved)

        onNodeWithText("Other measurement").performClick()
        onNode(hasSetTextAction() and hasContentDescription("What you measured")).performTextInput("Waist")
        onNode(hasSetTextAction() and hasContentDescription("Value")).performTextInput("84")
        onNode(hasSetTextAction() and hasContentDescription("Unit")).performTextInput("cm")
        onNodeWithText("Save").performClick()
        assertEquals(Measurement("Waist", 84.0, "cm"), saved)
    }
}
