package org.easypaper.ui.patient.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.model.*
import org.easypaper.ui.components.BackAction
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellTheme
import org.easypaper.ui.theme.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class CategoryDetailUiTest {
    private val today = LocalDate(2026, 11, 21)
    private val zone = TimeZone.of("UTC+02:00")
    private val request = personalRequest(today, "en", 0L, Category.entries.associateWith { it.name })

    private fun entry(id: String, widget: String, date: String, payload: EntryPayload) =
        Entry(id, widget, Instant.parse(date).toEpochMilliseconds(), payload)

    private fun ComposeUiTest.waitForText(text: String) = waitUntil(timeoutMillis = 10_000) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun ComposeUiTest.showCategory(category: Category, entries: List<Entry>, language: String = "en") {
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                PatientLanguage(language) {
                    CategoryDetail(category, listOf(PatientRecord(request, entries)), today, zone,
                        BackAction("Back") {}, {}, {}, {})
                }
            }
        }
    }

    @Test
    fun addIsAnAccessibleButtonOnTheRightAndAllDataAndBackCallTheirCallbacks() = runComposeUiTest {
        var adds = 0
        var all = 0
        var backs = 0
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                PatientLanguage("en") {
                    CategoryDetail(Category.BloodPressure, emptyList(), today, zone,
                        BackAction("Back") { backs++ }, { adds++ }, { all++ }, {})
                }
            }
        }
        waitForText("Range")
        val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        val add = onNode(hasContentDescription("Add Data") and button)
        add.assertIsDisplayed().assertHasClickAction()
        onNodeWithText("Add Data").assertDoesNotExist()
        val addBounds = add.fetchSemanticsNode().boundsInRoot
        val backBounds = onNodeWithText("Back").fetchSemanticsNode().boundsInRoot
        assertTrue(addBounds.left > backBounds.right)
        assertEquals(addBounds.width, addBounds.height, 1f)
        add.performClick()
        assertEquals(1, adds)
        onNodeWithText("Show All Data").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, all)
        onNodeWithText("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun pressureShowsARangeAndThePeriodAboveTheChart() = runComposeUiTest {
        showCategory(Category.BloodPressure, listOf(
            entry("a", "personal-pressure", "2026-11-15T10:00:00Z", BloodPressure(120, 70)),
            entry("b", "personal-pressure", "2026-11-21T10:00:00Z", BloodPressure(140, 90)),
            entry("old", "personal-pressure", "2026-11-14T10:00:00Z", BloodPressure(180, 100)),
        ))
        waitForText("120–140 / 70–90\u00A0mmHg")
        onNodeWithText("Range").assertIsDisplayed()
        onNodeWithText("15–21 Nov").assertIsDisplayed()
        onNodeWithContentDescription("Day").performClick()
        waitForText("140–140 / 90–90\u00A0mmHg")
        onNodeWithContentDescription("Day").assertIsSelected()
        onNodeWithText("120–140 / 70–90\u00A0mmHg").assertDoesNotExist()
    }

    @Test
    fun waterUsesLocalDaysAndIncludesUnloggedDaysInEveryPeriod() = runComposeUiTest {
        showCategory(Category.Water, listOf(
            // Both belong to the 21st in the patient's time zone.
            entry("a", "personal-water", "2026-11-20T23:30:00Z", Water(700)),
            entry("b", "personal-water", "2026-11-21T10:00:00Z", Water(700)),
            entry("old", "personal-water", "2026-05-20T10:00:00Z", Water(9999)),
        ))
        waitForText("200\u00A0ml") // 1400 / 7, not 1400 / one logged day
        onNodeWithText("Daily average").assertIsDisplayed()
        onNodeWithContentDescription("Day").performClick()
        waitForText("1400\u00A0ml")
        onNodeWithContentDescription("Month").performClick()
        waitForText("46\u00A0ml")
        onNodeWithContentDescription("6 months").performClick()
        waitForText("7\u00A0ml")
        onNodeWithContentDescription("6 months").assertIsSelected()
        onNodeWithText("22 May – 21 Nov").assertIsDisplayed()
    }

    @Test
    fun weightAverageUsesOnlyWeightInKilogramsAndThePatientsDecimalSeparator() = runComposeUiTest {
        showCategory(Category.Measurements, listOf(
            entry("a", "personal-measurements", "2026-11-20T10:00:00Z", Measurement.weight(70.0)),
            entry("b", "personal-measurements", "2026-11-21T10:00:00Z", Measurement.weight(75.0)),
            entry("waist", "personal-measurements", "2026-11-21T10:00:00Z", Measurement("Waist", 90.0, "cm")),
            entry("other-unit", "personal-measurements", "2026-11-21T10:00:00Z",
                Measurement("Weight", 160.0, "lb", Measurement.WEIGHT_LOINC)),
        ), language = "fr")
        waitForText("72,5\u00A0kg")
        onNodeWithText("Moyenne").assertIsDisplayed()
    }

    @Test
    fun otherMeasurementsAreCountedWithoutAveragingDifferentUnits() = runComposeUiTest {
        showCategory(Category.Measurements, listOf(
            entry("a", "personal-measurements", "2026-11-21T10:00:00Z", Measurement("Waist", 90.0, "cm")),
            entry("b", "personal-measurements", "2026-11-21T11:00:00Z", Measurement("Glucose", 5.0, "mmol/L")),
        ))
        waitForText("2\u00A0entries")
        onNodeWithText("Total").assertIsDisplayed()
        onNodeWithText("Average").assertDoesNotExist()
    }

    @Test
    fun emptyWaterShowsNoDataAndTheAboutRowOpensAndClosesAFactualSheet() = runComposeUiTest {
        showCategory(Category.Water, emptyList())
        waitForText("Daily average")
        onNodeWithText("No data yet").assertIsDisplayed()
        onNodeWithText("0\u00A0ml").assertDoesNotExist()
        onNodeWithText("About Water").performScrollTo().assertHasClickAction().performClick()
        val fact = "Water records contain the amount you logged in millilitres and the time recorded."
        waitForText(fact)
        onNodeWithText(fact).assertIsDisplayed()
        onNodeWithText("Done").performClick()
        onNodeWithText(fact).assertDoesNotExist()
        onNodeWithText("Show All Data").assertIsDisplayed()
    }

    @Test
    fun showAllDataOpensEntriesOutsideTheSelectedPeriodAndPreservesTheirOwner() = runComposeUiTest {
        val old = entry("old", "personal-diary", "2026-10-01T10:00:00Z", DiaryNote("Earlier note"))
        val record = PatientRecord(request, listOf(old))
        val showAll = mutableStateOf(false)
        var opened: OwnedEntry? = null
        var deleted: OwnedEntry? = null
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                PatientLanguage("en") {
                    if (showAll.value) {
                        AllCategoryData(Category.Diary, listOf(record), zone, BackAction("Back") { showAll.value = false },
                            { opened = it }, { deleted = it })
                    } else {
                        CategoryDetail(Category.Diary, listOf(record), today, zone, BackAction("Back") {}, {},
                            { showAll.value = true }, {})
                    }
                }
            }
        }
        waitForText("No data yet")
        onNodeWithText("Earlier note").assertDoesNotExist()
        onNodeWithText("Show All Data").performScrollTo().performClick()
        waitForText("Earlier note")
        onNodeWithText("Earlier note").performClick()
        assertEquals(OwnedEntry(record, old), opened)
        onNodeWithText("Delete").performClick()
        assertEquals(OwnedEntry(record, old), deleted)
        onNodeWithText("Back").performClick()
        waitForText("Show All Data")
    }

    @Test
    fun reducedMotionAndLargeTextKeepPeriodChangesAndActionsUsable() {
        val previous = System.getProperty("easypaper.reducedMotion")
        try {
            System.setProperty("easypaper.reducedMotion", "true")
            runComposeUiTest {
                setContent {
                    ShellTheme(ShellSettings(dark = true)) {
                        PatientLanguage("en") {
                            val density = LocalDensity.current
                            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                                Box(Modifier.width(320.dp)) {
                                    CategoryDetail(Category.Water, emptyList(), today, zone, BackAction("Back") {}, {}, {}, {})
                                }
                            }
                        }
                    }
                }
                waitForText("Daily average")
                onNodeWithContentDescription("Day").performClick().assertIsSelected()
                onNodeWithText("21–21 Nov").assertExists()
                onNodeWithText("Show All Data").performScrollTo().assertIsDisplayed()
                onNodeWithContentDescription("Add Data").assertIsDisplayed().assertHasClickAction()
                onNodeWithText("About Water").performScrollTo().performClick()
                onNodeWithText("Done").assertIsDisplayed().performClick()
            }
        } finally {
            if (previous == null) System.clearProperty("easypaper.reducedMotion")
            else System.setProperty("easypaper.reducedMotion", previous)
        }
    }
}
