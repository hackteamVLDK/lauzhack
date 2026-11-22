package org.easypaper.ui.doctor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import kotlinx.datetime.TimeZone
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import org.easypaper.ai.DocumentFact
import org.easypaper.model.EasyPaperJson
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellTheme
import org.easypaper.visit.SavedClinicForm
import org.easypaper.visit.demoVisitReport
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Task 44: fixes from the final UI review on the doctor's visit screen. */
@OptIn(ExperimentalTestApi::class)
class VisitReviewUiTest {
    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun ComposeUiTest.waitForText(text: String, substring: Boolean = false) = waitUntil(timeoutMillis = 10_000) {
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun ComposeUiTest.scrollTo(text: String) = onNode(hasScrollAction()).performScrollToNode(hasText(text))

    @Suppress("DEPRECATION")
    private class FakeClipboard : ClipboardManager {
        var copied: AnnotatedString? = null
        override fun getText(): AnnotatedString? = copied
        override fun setText(annotatedString: AnnotatedString) { copied = annotatedString }
    }

    @Test
    fun theHeaderNamesTheReportByItsShortNumber() = runComposeUiTest {
        val report = demoVisitReport()
        setContent { ShellTheme(ShellSettings(dark = false)) { VisitScreen(report, back = null) } }
        waitForText("Report ${report.shortCode()}", substring = true)
    }

    @Test
    fun theSummaryBadgeFollowsTheHistoryField() = runComposeUiTest {
        val report = demoVisitReport()
        val store = mutableMapOf<String, VisitState>()
        setContent { ShellTheme(ShellSettings(dark = false)) { VisitScreen(report, back = null, store = store) } }
        waitForText("Confirm summary")
        scrollTo("Confirm summary")
        onNodeWithText("Confirm summary").performClick()
        waitForText("Confirmed ", substring = true)
        store.getValue(report.id).remove(org.easypaper.visit.FormFieldKey.HISTORY)
        waitForText("Removed")
    }

    @Test
    fun completingTheReviewOffersTheConfirmedFieldsForTheClinicRecord() = runComposeUiTest {
        val report = demoVisitReport()
        val store = mutableMapOf<String, VisitState>()
        val clipboard = FakeClipboard()
        setContent {
            @Suppress("DEPRECATION")
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                ShellTheme(ShellSettings(dark = false)) { VisitScreen(report, back = null, store = store) }
            }
        }
        waitForText("Confirm summary")
        val state = store.getValue(report.id)
        state.form.fields.forEach { state.confirm(it.key, 1_000L) }
        scrollTo("Complete review")
        onNodeWithText("Complete review").performClick()
        waitForText("Copy for the clinic record")
        scrollTo("Copy for the clinic record")
        onNodeWithText("Copy for the clinic record").performClick()
        waitForText("Copied. Paste it into the clinic record.")
        val copied = EasyPaperJson.decodeFromString(SavedClinicForm.serializer(), clipboard.copied!!.text)
        assertEquals(state.saved, copied)
        assertEquals(state.form.fields.size, copied.fields.size)
    }

    @Test
    fun theChartListsItsValuesOnRequest() = runComposeUiTest {
        val report = demoVisitReport()
        setContent { ShellTheme(ShellSettings(dark = false)) { VisitScreen(report, back = null) } }
        waitForText("Confirm summary") // the chart is below the fold: scrolling composes it
        scrollTo("Show values")
        onNodeWithText("Show values").performClick()
        waitForText("Hide values")
        val readings = pressureSeries(report.record.entries)!!.readings
        val first = readings.first().second
        waitForText("${first.systolic}/${first.diastolic} mmHg", substring = true)
    }

    @Test
    fun aFactOpensTheDocumentItCameFrom() = runComposeUiTest {
        val report = demoVisitReport()
        val fact = DocumentFact("medication", "Ramipril 5 mg once daily", "Ramipril 5 mg 1-0-0", "doc-1", verified = true)
        val letter = DocumentSource("Discharge letter. Medication: Ramipril 5 mg 1-0-0.", null)
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                VisitScreen(report, back = null, documents = { visit, show ->
                    Column { DocumentsSection(visit, ai = null, facts = listOf(fact), onFacts = {}, sources = mapOf("doc-1" to letter), onShow = show) }
                })
            }
        }
        waitForText("Confirm summary")
        scrollTo("Show document")
        assertTrue(onAllNodesWithText("Draft").fetchSemanticsNodes().isNotEmpty())
        onNodeWithText("Show document").performClick()
        waitForText("Discharge letter. Medication: Ramipril 5 mg 1-0-0.")
    }

    @Test
    fun theChartSurvivesAPlotNarrowerThanADayLabel() = runComposeUiTest {
        val series = pressureSeries(demoVisitReport().record.entries)!!
        // 60 dp minus the y scale leaves less room than one "14 Mar" label: it used to throw in coerceIn.
        setContent { ShellTheme(ShellSettings(dark = false)) { Box(Modifier.width(60.dp)) { PressureChart(series, TimeZone.UTC) } } }
        waitForIdle()
        onRoot().captureToImage() // forces the Canvas to draw
    }
}
