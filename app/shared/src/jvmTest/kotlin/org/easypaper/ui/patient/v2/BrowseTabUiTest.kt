package org.easypaper.ui.patient.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.easypaper.ui.shell.SavedSetting
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BrowseTabUiTest {
    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun setting(value: String? = null) = SavedSetting(value) {}

    private fun ComposeUiTest.showBrowse(dark: Boolean = false, onCategory: (Category) -> Unit = {}) {
        val settings = PatientSettings(setting("true"), setting("en"), setting(), setting(), setting(), setting(),
            setting(), setting(), setting(), setting(), setting())
        setContent {
            EasyPaperTheme(darkTheme = dark) {
                PatientLanguage("en") {
                    Box(Modifier.width(360.dp)) {
                        BrowseTab(settings, onProfile = {}, onDeveloper = {}, onCategory = onCategory)
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Categories").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun categoriesStartInTwoEqualColumnsUnderTheHeading() = runComposeUiTest {
        showBrowse()
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        onNode(hasText("Browse") and heading).assertIsDisplayed()
        onNode(hasText("Categories") and heading).assertIsDisplayed()
        onNodeWithContentDescription("Search categories").assertIsDisplayed()
        onNodeWithText("Blood pressure").assertHasClickAction()
        onNodeWithText("Water").assertHasClickAction()
        val left = onNode(hasText("Blood pressure") and button).fetchSemanticsNode().boundsInRoot
        val right = onNode(hasText("Water") and button).fetchSemanticsNode().boundsInRoot
        val section = onNode(hasText("Categories") and heading).fetchSemanticsNode().boundsInRoot
        assertEquals(left.top, right.top, 1f)
        assertEquals(left.width, right.width, 1f)
        assertTrue(left.right < right.left)
        assertTrue(section.bottom <= left.top)
    }

    @Test
    fun searchingWatLeavesOnlyWaterAndClickCallsOnCategory() = runComposeUiTest {
        val selected = mutableListOf<Category>()
        showBrowse(onCategory = { selected += it })
        onNodeWithContentDescription("Search categories").performTextInput("wat")
        onNodeWithText("Water").assertIsDisplayed().performClick()
        for (title in listOf("Blood pressure", "Food", "Medicines", "Symptoms", "How you feel",
            "Questions for the doctor", "Body measurements")) {
            onNodeWithText(title).assertDoesNotExist()
        }
        runOnIdle { assertEquals(listOf(Category.Water), selected) }
    }

    @Test
    fun noMatchesShowsTheEmptyMessageAndClearingRestoresTilesInDarkTheme() = runComposeUiTest {
        showBrowse(dark = true)
        val search = onNodeWithContentDescription("Search categories")
        search.performTextInput("no matching category")
        onNodeWithText("No matching categories").assertIsDisplayed()
        onNodeWithText("Water").assertDoesNotExist()
        onNodeWithContentDescription("Clear search").performClick()
        onNodeWithText("No matching categories").assertDoesNotExist()
        onNodeWithText("Blood pressure").assertIsDisplayed()
        onNodeWithText("Water").assertIsDisplayed()
        search.performTextReplacement("  WAT  ")
        onNodeWithText("Water").assertIsDisplayed()
        onNodeWithText("Blood pressure").assertDoesNotExist()
    }
}
