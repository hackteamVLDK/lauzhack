package org.easypaper.ui.patient

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.ic_calendar_today
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.easypaper.ui.components.BackAction
import org.easypaper.ui.components.FloatingTabBar
import org.easypaper.ui.components.TabItem
import org.easypaper.ui.components.WordFitText
import org.easypaper.ui.format.today
import org.easypaper.ui.patient.v2.CategoryDetail
import org.easypaper.ui.patient.v2.WhatToShareSheet
import org.easypaper.ui.shell.Role as AppRole
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellState
import org.easypaper.ui.shell.ShellTheme
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Task 57: patient v2 polish from the reviews of #51 and #56. */
@OptIn(ExperimentalTestApi::class)
class PatientPolishUiTest {
    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }

    /** Every line ends at a space or at the end: no word is cut in two. */
    private fun assertNoBrokenWords(text: String, layout: TextLayoutResult) {
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue(text[end - 1].isWhitespace() || text.getOrNull(end)?.isWhitespace() == true,
                "line ${line + 1} of '$text' ends inside a word: '${text.substring(0, end)}'")
        }
    }

    @Test
    fun tabLabelsStayOnOneLineAt200Percent() = runComposeUiTest {
        val labels = listOf("Übersicht", "Teilen", "Entdecken")
        setContent {
            ShellTheme(ShellSettings(dark = false, largeText = true)) {
                Box(Modifier.width(360.dp)) {
                    FloatingTabBar(labels.map { TabItem(it, Res.drawable.ic_calendar_today) }, 0, {})
                }
            }
        }
        for (label in labels) {
            val layout = onNodeWithText(label).layout()
            assertEquals(1, layout.lineCount, "$label wraps")
            assertTrue(!layout.isLineEllipsized(0), "$label is cut short")
        }
    }

    @Test
    fun aLongTitleShrinksInsteadOfBreakingAWord() = runComposeUiTest {
        val title = "Dein Gesundheitstagebuch"
        setContent {
            ShellTheme(ShellSettings(dark = false, largeText = true)) {
                Box(Modifier.width(320.dp)) {
                    WordFitText(title, EasyPaperTheme.typography.largeTitle, EasyPaperTheme.colors.label)
                }
            }
        }
        assertNoBrokenWords(title, onNodeWithText(title).layout())
    }

    @Test
    fun theShareSourceIsOneChoiceOfARadioList() = runComposeUiTest {
        val state = ShellState(AppRole.Patient)
        runBlocking { state.fillWithDemoData() }
        val records = state.repository.records.value
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                WhatToShareSheet(true, records, today(), TimeZone.currentSystemDefault(), {}, {})
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Dr. Demo", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val doctor = onNode(radio and hasText("Dr. Demo", substring = true))
        doctor.assertIsSelected() // the active request is the default source
        val others = onAllNodes(radio).fetchSemanticsNodes().size
        assertEquals(records.size + 4, others, "one radio per source plus the four period choices of a doctor's request")
    }

    @Test
    fun theCategoryScreenWritesDatesInWords() = runComposeUiTest {
        val state = ShellState(AppRole.Patient)
        runBlocking { state.fillWithDemoData() }
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                CategoryDetail(Category.BloodPressure, state.repository.records.value, today(), TimeZone.currentSystemDefault(),
                    BackAction("Back") {}, {}, {}, {})
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("mmHg", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Readings follow the chart and category actions and may be below the viewport.
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val entryDate = SemanticsMatcher("entry date in words") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text ->
                months.any { Regex("""\d{1,2} $it, \d""").containsMatchIn(text.text) }
            }
        }
        onNode(hasScrollAction()).performScrollToNode(entryDate)
        val iso = Regex("""\d{4}-\d{2}-\d{2}""")
        val texts = onAllNodes(SemanticsMatcher("any text") { it.config.getOrNull(SemanticsProperties.Text) != null })
            .fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        assertTrue(texts.none { iso.containsMatchIn(it) }, "ISO date on the screen: ${texts.filter { iso.containsMatchIn(it) }}")
        assertTrue(texts.any { t -> months.any { Regex("""\d{1,2} $it, \d""").containsMatchIn(t) } },
            "no entry with a day-month date: $texts")
    }
}
