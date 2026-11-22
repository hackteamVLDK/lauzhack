package org.easypaper.ui.patient.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.easypaper.model.DiaryNote
import org.easypaper.model.Entry
import org.easypaper.model.PatientRecord
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.shell.ShellSettings
import org.easypaper.ui.shell.ShellTheme
import org.easypaper.ui.theme.Category
import kotlin.test.Test
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class StreakCardUiTest {
    private val today = LocalDate(2026, 11, 21)

    private fun ComposeUiTest.waitForDescription(description: String) = waitUntil(timeoutMillis = 10_000) {
        onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun emptyDiaryHasOneAnnouncementWithTheEntryPrompt() = runComposeUiTest {
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                PatientLanguage("en") { StreakCard(streak(emptyList(), today), entriesToday = 0) }
            }
        }
        val description = "0 days in a row. Today: 0 entries. Add your first entry today"
        waitForDescription(description)
        onAllNodesWithContentDescription(description).assertCountEquals(1)
        onNodeWithContentDescription(description).assertIsDisplayed()
        // Separate numbers, weekday initials and labels must not repeat the card announcement.
        onNodeWithText("0").assertDoesNotExist()
        onNodeWithText("days in a row").assertDoesNotExist()
        onNodeWithText("M").assertDoesNotExist()
    }

    @Test
    fun yesterdayShowsTheReminderAndAddingTodaySwitchesToDone() = runComposeUiTest {
        val dates = mutableStateOf(listOf(LocalDate(2026, 11, 20)))
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                PatientLanguage("en") { StreakCard(streak(dates.value, today), dates.value.count { it == today }) }
            }
        }
        val pending = "1 day in a row. Today: 0 entries. Keep your streak: add an entry today"
        waitForDescription(pending)
        onNodeWithContentDescription(pending).assertIsDisplayed()
        runOnIdle { dates.value += listOf(today, today) }
        val done = "2 days in a row. Today: 2 entries. Done for today. See you tomorrow!"
        waitForDescription(done)
        onNodeWithContentDescription(done).assertIsDisplayed()
        onNodeWithContentDescription(pending).assertDoesNotExist()
    }

    @Test
    fun aGapShowsTheFirstEntryPromptAgain() = runComposeUiTest {
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                PatientLanguage("en") {
                    StreakCard(streak(listOf(LocalDate(2026, 11, 19)), today), entriesToday = 0)
                }
            }
        }
        val description = "0 days in a row. Today: 0 entries. Add your first entry today"
        waitForDescription(description)
        onNodeWithContentDescription(description).assertIsDisplayed()
    }

    @Test
    fun summaryCountsEntriesAcrossRecordsInThePatientsTimeZone() = runComposeUiTest {
        val request = personalRequest(today, "en", 0L, Category.entries.associateWith { it.name })
        val records = listOf(
            PatientRecord(request, listOf(
                Entry("a", "personal-diary", Instant.parse("2026-11-19T10:00:00Z").toEpochMilliseconds(), DiaryNote("a")),
                Entry("b", "personal-diary", Instant.parse("2026-11-20T10:00:00Z").toEpochMilliseconds(), DiaryNote("b")),
                Entry("c", "personal-diary", Instant.parse("2026-11-20T23:30:00Z").toEpochMilliseconds(), DiaryNote("c")),
            )),
            PatientRecord(request.copy(id = "another-diary"), listOf(
                Entry("d", "personal-diary", Instant.parse("2026-11-21T06:00:00Z").toEpochMilliseconds(), DiaryNote("d")),
            )),
        )
        val setting = { SavedSetting(null) {} }
        val patient = PatientSettings(setting(), setting(), SavedSetting("") {}, setting(),
            setting(), setting(), setting(), setting(), setting(), setting(), setting())
        setContent {
            ShellTheme(ShellSettings(dark = false)) {
                PatientLanguage("en") {
                    SummaryTab(records, patient, today, TimeZone.of("UTC+02:00"),
                        onProfile = {}, onDeveloper = {}, onPlan = {}, onCategory = {}, onAdd = {}, onGallery = {})
                }
            }
        }
        val description = "3 days in a row. Today: 2 entries. Done for today. See you tomorrow!"
        waitForDescription(description)
        onNodeWithContentDescription(description).assertIsDisplayed()
    }

    @Test
    fun russianPluralCardFitsANarrowDarkScreenWithLargeText() = runComposeUiTest {
        setContent {
            ShellTheme(ShellSettings(dark = true)) {
                PatientLanguage("ru") {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                        Box(Modifier.width(320.dp)) {
                            StreakCard(streak(listOf(LocalDate(2026, 11, 19), LocalDate(2026, 11, 20), today), today), entriesToday = 1)
                        }
                    }
                }
            }
        }
        val description = "3 дня подряд. Сегодня: 1 запись. На сегодня всё. До завтра!"
        waitForDescription(description)
        onNodeWithContentDescription(description).assertIsDisplayed()
    }
}
