package org.easypaper.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.ic_calendar_today
import org.easypaper.ui.shell.Navigator
import org.easypaper.ui.theme.EasyPaperTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Task 76: real selection semantics, measured thumb geometry and independent navigation stacks. */
@OptIn(ExperimentalTestApi::class)
class MotionUiTest {
    @Test
    fun selectingATabCallsOnSelectAndSpringsOneSharedIndicator() = withReducedMotion(false) {
        runComposeUiTest {
            var selected by mutableIntStateOf(0)
            val selections = mutableListOf<Int>()
            setContent {
                EasyPaperTheme(darkTheme = false) {
                    Box(Modifier.width(360.dp)) {
                        FloatingTabBar(motionTabs, selected, { selected = it; selections += it })
                    }
                }
            }
            waitForIdle()
            val start = onNodeWithTag("floating-tab-indicator").fetchSemanticsNode().boundsInRoot
            val target = onNode(tab("Sharing")).fetchSemanticsNode().boundsInRoot
            mainClock.autoAdvance = false
            onNode(tab("Sharing")).performClick()
            mainClock.advanceTimeBy(80)
            waitForIdle()

            onNode(tab("Sharing")).assertIsSelected()
            onNode(tab("Summary")).assertIsNotSelected()
            assertEquals(listOf(1), selections)
            onAllNodesWithTag("floating-tab-indicator").assertCountEquals(1)
            val moving = onNodeWithTag("floating-tab-indicator").fetchSemanticsNode().boundsInRoot
            assertTrue(moving.left > start.left && moving.left < target.left, "thumb must travel between tabs")

            mainClock.advanceTimeBy(2_000)
            waitForIdle()
            assertSameBounds(target, onNodeWithTag("floating-tab-indicator").fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    fun reducedMotionPlacesTheIndicatorOnTheSelectedTabInOneFrame() = withReducedMotion(true) {
        runComposeUiTest {
            var selected by mutableIntStateOf(0)
            val selections = mutableListOf<Int>()
            setContent {
                EasyPaperTheme(darkTheme = false) {
                    // Physical measurements must also work when the visual tab order is reversed.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Box(Modifier.width(360.dp)) {
                            FloatingTabBar(motionTabs, selected, { selected = it; selections += it })
                        }
                    }
                }
            }
            waitForIdle()
            val target = onNode(tab("Browse")).fetchSemanticsNode().boundsInRoot
            mainClock.autoAdvance = false
            onNode(tab("Browse")).performClick()
            mainClock.advanceTimeByFrame() // recompose and lay out; do not run a spring to completion
            waitForIdle()

            onNode(tab("Browse")).assertIsSelected()
            onNode(tab("Summary")).assertIsNotSelected()
            assertEquals(listOf(2), selections)
            assertSameBounds(target, onNodeWithTag("floating-tab-indicator").fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    fun indicatorSpringsToTheMeasuredWidthAfterAResize() = withReducedMotion(false) {
        runComposeUiTest {
            var width by mutableStateOf(360.dp)
            setContent {
                EasyPaperTheme(darkTheme = false) {
                    Box(Modifier.width(width)) { FloatingTabBar(motionTabs, 0, {}) }
                }
            }
            waitForIdle()
            val start = onNodeWithTag("floating-tab-indicator").fetchSemanticsNode().boundsInRoot
            mainClock.autoAdvance = false
            runOnIdle { width = 280.dp }
            mainClock.advanceTimeByFrame()
            waitForIdle() // publish the resized tab measurements before advancing the spring
            mainClock.advanceTimeByFrame()
            waitForIdle()
            mainClock.advanceTimeBy(80)
            waitForIdle()
            val target = onNode(tab("Summary")).fetchSemanticsNode().boundsInRoot
            val moving = onNodeWithTag("floating-tab-indicator").fetchSemanticsNode().boundsInRoot
            assertTrue(moving.width < start.width && moving.width > target.width, "thumb width must animate")
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
            assertSameBounds(target, onNodeWithTag("floating-tab-indicator").fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    fun pushAndBackShowTheCorrectScreenAfterTheSpring() = verifyPushAndBack(reduced = false)

    @Test
    fun reducedMotionPushAndBackOnlyFade() = verifyPushAndBack(reduced = true)

    private fun verifyPushAndBack(reduced: Boolean) = withReducedMotion(reduced) {
        runComposeUiTest {
            val navigator = Navigator("Root")
            setContent {
                EasyPaperTheme(darkTheme = false) {
                    Box(Modifier.size(360.dp, 480.dp)) {
                        PushNavigation(navigator.stack, Modifier.fillMaxSize()) { screen ->
                            Column(Modifier.fillMaxSize().testTag(screen)) {
                                Text(screen)
                                if (screen == "Root") PlainButton("Open", { navigator.push("Detail") })
                                else NavigationBar(BackAction("Back") { navigator.back() })
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val rootBounds = onNodeWithTag("Root").fetchSemanticsNode().boundsInRoot
            mainClock.autoAdvance = false
            onNodeWithText("Open").performClick()
            mainClock.advanceTimeByFrame()
            waitForIdle() // slide transitions need the incoming screen's measured width
            mainClock.advanceTimeBy(80)
            waitForIdle()
            val entering = onNodeWithTag("Detail").fetchSemanticsNode().boundsInRoot
            if (reduced) assertSameBounds(rootBounds, entering)
            else assertTrue(entering.left > rootBounds.left, "detail must enter from the right")
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
            onNodeWithTag("Root").assertDoesNotExist()
            onNodeWithTag("Detail").assertIsDisplayed()

            onNodeWithText("Back").performClick()
            mainClock.advanceTimeByFrame()
            waitForIdle()
            mainClock.advanceTimeBy(80)
            waitForIdle()
            val exiting = onNodeWithTag("Detail").fetchSemanticsNode().boundsInRoot
            if (reduced) assertSameBounds(rootBounds, exiting)
            else assertTrue(exiting.left > rootBounds.left, "detail must leave to the right")
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
            onNodeWithTag("Root").assertIsDisplayed()
            onNodeWithTag("Detail").assertDoesNotExist()
            assertEquals("Root", navigator.current)
        }
    }

    @Test
    fun changingTabsCrossfadesWithoutTravelAndPreservesEachStack() = withReducedMotion(false) {
        runComposeUiTest {
            var selected by mutableIntStateOf(0)
            val navigators = listOf(Navigator("Root A"), Navigator("Root B"))
            navigators[0].push("Detail A")
            setContent {
                EasyPaperTheme(darkTheme = false) {
                    Box(Modifier.size(360.dp, 480.dp)) {
                        TabNavigation(selected, Modifier.fillMaxSize()) { tab ->
                            PushNavigation(navigators[tab].stack, Modifier.fillMaxSize()) { screen ->
                                Box(Modifier.fillMaxSize().testTag(screen)) { Text(screen) }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val bounds = onNodeWithTag("Detail A").fetchSemanticsNode().boundsInRoot
            mainClock.autoAdvance = false
            runOnIdle { selected = 1 }
            mainClock.advanceTimeBy(80)
            waitForIdle()
            assertSameBounds(bounds, onNodeWithTag("Root B").fetchSemanticsNode().boundsInRoot)
            assertSameBounds(bounds, onNodeWithTag("Detail A").fetchSemanticsNode().boundsInRoot)
            mainClock.advanceTimeBy(300)
            waitForIdle()
            onNodeWithTag("Detail A").assertDoesNotExist()
            onNodeWithTag("Root B").assertIsDisplayed()

            runOnIdle { selected = 0 }
            mainClock.advanceTimeBy(300)
            waitForIdle()
            onNodeWithTag("Detail A").assertIsDisplayed()
            onNodeWithTag("Root B").assertDoesNotExist()
            assertEquals(listOf("Root A", "Detail A"), navigators[0].stack)
        }
    }

    @Test
    fun sheetRisesFromTheBottomAndDismisses() = verifySheet(reduced = false)

    @Test
    fun reducedMotionSheetFadesAtItsFinalPosition() = verifySheet(reduced = true)

    private fun verifySheet(reduced: Boolean) = withReducedMotion(reduced) {
        runComposeUiTest {
            var visible by mutableStateOf(false)
            setContent {
                EasyPaperTheme(darkTheme = false) {
                    Box(Modifier.size(360.dp, 480.dp)) {
                        PlainButton("Show sheet", { visible = true })
                        Sheet(visible, "Sheet", "Done", { visible = false }) { Text("Sheet body") }
                    }
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            onNodeWithText("Show sheet").performClick()
            mainClock.advanceTimeByFrame()
            waitForIdle() // the slide offset depends on the measured sheet height
            mainClock.advanceTimeBy(80)
            waitForIdle()
            val entering = onNodeWithTag("sheet-surface").fetchSemanticsNode().boundsInRoot
            onNodeWithTag("sheet-scrim").assertExists()
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
            val settled = onNodeWithTag("sheet-surface").fetchSemanticsNode().boundsInRoot
            if (reduced) assertSameBounds(settled, entering)
            else assertTrue(entering.top > settled.top, "sheet must rise from below")
            onNodeWithText("Sheet body").assertIsDisplayed()

            onNodeWithText("Done").performClick()
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
            onNodeWithTag("sheet-surface").assertDoesNotExist()
            onNodeWithTag("sheet-scrim").assertDoesNotExist()
        }
    }
}

private val motionTabs = listOf("Summary", "Sharing", "Browse").map { TabItem(it, Res.drawable.ic_calendar_today) }

private fun tab(label: String) = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

private fun assertSameBounds(expected: Rect, actual: Rect) {
    assertEquals(expected.left, actual.left, 1f)
    assertEquals(expected.top, actual.top, 1f)
    assertEquals(expected.right, actual.right, 1f)
    assertEquals(expected.bottom, actual.bottom, 1f)
}

/** Use the desktop accessibility setting, restoring it even when an assertion fails. */
private fun withReducedMotion(reduced: Boolean, block: () -> Unit) {
    val previous = System.getProperty("easypaper.reducedMotion")
    try {
        System.setProperty("easypaper.reducedMotion", reduced.toString())
        block()
    } finally {
        if (previous == null) System.clearProperty("easypaper.reducedMotion")
        else System.setProperty("easypaper.reducedMotion", previous)
    }
}
