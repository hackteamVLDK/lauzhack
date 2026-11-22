package org.easypaper.ui.patient.v2

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.model.*
import org.easypaper.ui.components.category
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellTheme
import org.easypaper.ui.theme.Category
import kotlin.test.Test
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class SummaryTabUiTest {
    private val today = LocalDate(2026, 11, 22)
    private val zone = TimeZone.UTC
    private val request = personalRequest(today, "en", 0, Category.entries.associateWith { it.name })
    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private fun settings(pinned: String = "", dismissed: SavedSetting = SavedSetting(null) {}): PatientSettings {
        val empty = { SavedSetting(null) {} }
        return PatientSettings(empty(), empty(), SavedSetting(pinned) {}, empty(), empty(), empty(),
            empty(), empty(), empty(), empty(), empty(), dismissed)
    }
    private fun entry(id: String, category: Category, at: String, payload: EntryPayload) = Entry(id,
        request.widgets.first { it.category == category }.id, Instant.parse(at).toEpochMilliseconds(), payload)

    private fun ComposeUiTest.show(patient: PatientSettings, records: List<PatientRecord> = emptyList(),
                                   onCategory: (Category) -> Unit = {}, onGallery: () -> Unit = {}, onInvite: () -> Unit = {}) {
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                PatientLanguage("en") {
                    SummaryTab(records, patient, today, zone, onProfile = {}, onDeveloper = {}, onPlan = {},
                        onCategory = onCategory, onGallery = onGallery, onOpenInvite = onInvite)
                }
            }
        }
    }
    private fun ComposeUiTest.scrollTo(matcher: SemanticsMatcher) = onNode(hasScrollAction()).performScrollToNode(matcher)

    @Test
    fun pinnedCardsUseFullWidthAndOpenStatisticsWithoutAnAddDataButton() = runComposeUiTest {
        var opened: Category? = null
        var gallery = false
        val entries = listOf(
            entry("w", Category.Water, "2026-11-22T08:00:00Z", Water(250)),
            entry("p", Category.BloodPressure, "2026-11-21T08:00:00Z", BloodPressure(128, 84)),
        )
        show(settings("Water,BloodPressure"), listOf(PatientRecord(request, entries)),
            onCategory = { opened = it }, onGallery = { gallery = true })
        val water = hasContentDescription("Water, 250 ml, 08:00") and button
        val pressure = hasContentDescription("Blood pressure, 128/84 mmHg, Yesterday") and button
        scrollTo(water)
        onNode(water).assertHasClickAction().performClick()
        runOnIdle { assertEquals(Category.Water, opened) }
        val waterBounds = onNode(water).fetchSemanticsNode().boundsInRoot
        scrollTo(pressure)
        val pressureBounds = onNode(pressure).fetchSemanticsNode().boundsInRoot
        assertEquals(waterBounds.left, pressureBounds.left)
        assertEquals(waterBounds.width, pressureBounds.width)
        assertTrue(waterBounds.width > 0)
        onNode(pressure).performClick()
        runOnIdle { assertEquals(Category.BloodPressure, opened) }
        onAllNodesWithText("Add Data").assertCountEquals(0)
        scrollTo(hasText("Add to Summary"))
        onNodeWithText("Add to Summary").performClick()
        runOnIdle { assertTrue(gallery) }
    }

    @Test
    fun editingMovesAndRemovesCardsAndDisablesBoundaryArrows() = runComposeUiTest {
        val patient = settings("Water,BloodPressure")
        show(patient)
        scrollTo(hasText("Edit"))
        onNodeWithText("Edit").performClick()
        scrollTo(hasContentDescription("Move Water up"))
        onNodeWithContentDescription("Move Water up").assertIsNotEnabled()
        onNodeWithContentDescription("Move Water down").performClick()
        runOnIdle { assertEquals(listOf(Category.BloodPressure, Category.Water), patient.pinnedCategories) }
        scrollTo(hasContentDescription("Move Water down"))
        onNodeWithContentDescription("Move Water down").assertIsNotEnabled()
        onNodeWithContentDescription("Remove Water widget").performClick()
        runOnIdle { assertEquals(listOf(Category.BloodPressure), patient.pinnedCategories) }
        scrollTo(hasContentDescription("Remove Blood pressure widget"))
        onNodeWithContentDescription("Remove Blood pressure widget").performClick()
        scrollTo(hasText("What do you want to track?"))
        onNodeWithText("Done").assertDoesNotExist()
        runOnIdle { assertTrue(patient.pinnedCategories.isEmpty()) }
    }

    @Test
    fun tipsPinWidgetsDismissPersistentlyAndOpenTheDoctorRequest() = runComposeUiTest {
        var persisted: String? = null
        var invited = false
        val patient = settings(dismissed = SavedSetting(null) { persisted = it })
        show(patient, onInvite = { invited = true })
        scrollTo(hasText("Blood pressure log"))
        // Pin the first suggestion; it disappears when the widget becomes relevant.
        onAllNodesWithText("Pin to Summary").onFirst().performScrollTo().performClick()
        runOnIdle { assertTrue(Category.BloodPressure in patient.pinnedCategories) }
        scrollTo(hasContentDescription("Dismiss Medicines tip"))
        onNodeWithContentDescription("Dismiss Medicines tip").performClick()
        runOnIdle {
            assertEquals("Medicines", persisted)
            assertEquals(listOf(SummaryTip.Invite), summaryTips(patient, false))
        }
        scrollTo(hasText("Open request"))
        onNodeWithText("Open request").performClick()
        runOnIdle { assertTrue(invited) }
    }

    @Test
    fun trendsAppearOnlyWithFourteenDaysOfHistoryAndShowFactualAverages() = runComposeUiTest {
        val records = listOf(PatientRecord(request, listOf(
            entry("old", Category.Water, "2026-11-09T08:00:00Z", Water(700)),
            entry("new", Category.Water, "2026-11-22T08:00:00Z", Water(1400)),
        )))
        show(settings("Water"), records)
        scrollTo(hasText("Trends"))
        onNodeWithText("Trends").assertIsDisplayed()
        val fact = "You logged more water over the last 7 days than in the preceding 7 days."
        scrollTo(hasText(fact))
        onNodeWithText(fact).assertExists()
        scrollTo(hasText("Daily averages — preceding 7 days: 100; last 7 days: 200 (ml)."))
        onNodeWithText("Daily averages — preceding 7 days: 100; last 7 days: 200 (ml).").assertExists()
    }

    @Test
    fun emptySummaryHasNoTrendsOrInlineAddData() = runComposeUiTest {
        show(settings("Water"))
        scrollTo(hasText("No data yet"))
        onNodeWithText("No data yet").assertExists()
        scrollTo(hasText("Get more from Easy Paper"))
        onNodeWithText("Trends").assertDoesNotExist()
        onNodeWithText("Add Data").assertDoesNotExist()
    }

    @Test
    fun theStreakCardGreetsOnceADay() = runComposeUiTest {
        val seen = SavedSetting(null) {}
        val empty = { SavedSetting(null) {} }
        val patient = PatientSettings(empty(), empty(), SavedSetting("") {}, empty(), empty(), empty(),
            empty(), empty(), empty(), empty(), empty(), empty(), seen)
        var visible by mutableStateOf(true)
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                PatientLanguage("en") {
                    // switching tabs drops the Summary from composition; coming back must not show the card again
                    if (visible) SummaryTab(emptyList(), patient, today, zone, onProfile = {}, onDeveloper = {}, onPlan = {},
                        onCategory = {}, onGallery = {}, onOpenInvite = {})
                }
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithContentDescription("in a row", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(today.toString(), seen.value, "the first visit of the day marks the card as seen")
        visible = false
        waitForIdle()
        visible = true
        waitForIdle()
        assertEquals(0, onAllNodesWithContentDescription("in a row", substring = true).fetchSemanticsNodes().size)
    }
}
