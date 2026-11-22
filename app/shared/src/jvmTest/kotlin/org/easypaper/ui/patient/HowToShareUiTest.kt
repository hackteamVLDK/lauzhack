package org.easypaper.ui.patient

import androidx.compose.ui.test.*
import org.easypaper.demo.Demo
import org.easypaper.model.PatientRecord
import org.easypaper.ui.patient.v2.HowToShareSheet
import org.easypaper.ui.patient.v2.ShareSelection
import org.easypaper.ui.theme.EasyPaperTheme
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HowToShareUiTest {
    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @Test
    fun threeEnabledMethodsWithoutBluetooth() = runComposeUiTest {
        val chosen = mutableListOf<Pair<String, PatientRecord>>()
        val selection = ShareSelection(Demo.report.record, emptySet())
        setContent {
            EasyPaperTheme(darkTheme = false) {
                HowToShareSheet(selection, true, {}, { chosen += "qr" to it },
                    { chosen += "code" to it }, { chosen += "file" to it })
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Show QR code").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Show QR code").assertIsEnabled().performClick()
        onNodeWithText("Send with a code").assertIsEnabled().performClick()
        onNodeWithText("Save encrypted file").assertIsEnabled().performClick()
        onAllNodesWithText("Bluetooth", substring = true).assertCountEquals(0)
        onAllNodesWithText("Later").assertCountEquals(0)
        runOnIdle {
            assertEquals(listOf("qr", "code", "file"), chosen.map { it.first })
            chosen.forEach { assertEquals(selection.record, it.second) }
        }
    }

    @Test
    fun methodsRequireSelectedEntries() = runComposeUiTest {
        setContent {
            EasyPaperTheme(darkTheme = false) { HowToShareSheet(null, true, {}, {}, {}, {}) }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Show QR code").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Show QR code").assertIsNotEnabled()
        onNodeWithText("Send with a code").assertIsNotEnabled()
        onNodeWithText("Save encrypted file").assertIsNotEnabled()
    }
}
