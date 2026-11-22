package org.easypaper.ui.theme

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import org.easypaper.ui.components.disclosureEnter
import org.easypaper.ui.components.disclosureExit
import org.easypaper.ui.components.pushTransition
import org.easypaper.ui.components.sheetEnter
import org.easypaper.ui.components.sheetExit
import org.easypaper.ui.components.valueTransition
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MotionTest {
    @Test
    fun swiftUiResponseAndDampingMapToComposeSprings() {
        val motion = EasyPaperMotion()
        assertEquals(0.5f, motion.responseSeconds)
        assertEquals(158f, motion.stiffness, absoluteTolerance = 0.1f)
        assertEquals(((2 * PI / 0.5) * (2 * PI / 0.5)).toFloat(), motion.stiffness, absoluteTolerance = 0.001f)
        val presets = listOf(motion.default to 0.825f, motion.smooth to 1f, motion.snappy to 0.85f, motion.bouncy to 0.7f)
        for ((preset, damping) in presets) {
            val spec = preset.spec<Float>()
            assertEquals(motion.stiffness, spec.stiffness)
            assertEquals(damping, spec.dampingRatio)
        }
        assertEquals(MotionTransition.Spatial, motion.transition)
    }

    @Test
    fun reducedSpringsAreFourTimesStifferAndNeverBounce() {
        val standard = EasyPaperMotion()
        val reduced = EasyPaperMotion(reduceMotion = true)
        assertTrue(reduced.stiffness > standard.stiffness)
        assertEquals(standard.stiffness * 4, reduced.stiffness)
        for (preset in listOf(reduced.default, reduced.smooth, reduced.snappy, reduced.bouncy)) {
            val spec = preset.spec<Float>()
            assertEquals(1f, spec.dampingRatio)
            assertEquals(reduced.stiffness, spec.stiffness)
        }
    }

    @Test
    fun fadesAreShortAndHaveNoDelay() {
        for (motion in listOf(EasyPaperMotion(), EasyPaperMotion(reduceMotion = true))) {
            assertEquals(200, motion.fade<Float>().durationMillis)
            assertEquals(0, motion.fade<Float>().delay)
            assertEquals(150, motion.pressFadeDurationMillis)
        }
    }

    @Test
    fun selectionUsesTheSnappySpringOrSnapsWithReducedMotion() {
        val motion = EasyPaperMotion()
        val selection = assertIs<SpringSpec<Float>>(motion.selection<Float>())
        assertEquals(motion.snappy.stiffness, selection.stiffness)
        assertEquals(motion.snappy.dampingRatio, selection.dampingRatio)
        assertIs<SnapSpec<Float>>(EasyPaperMotion(reduceMotion = true).selection<Float>())
        assertEquals(1f / 3f, motion.navigationParallaxFraction)
        assertTrue(motion.navigationFadeAlpha in 0.9f..1f)
    }

    @Test
    fun reducedMotionSelectsOnlyFadeForEverySpatialTransition() {
        val motion = EasyPaperMotion(reduceMotion = true)
        assertEquals(MotionTransition.Crossfade, motion.transition)
        val enter = fadeIn(motion.fade())
        val exit = fadeOut(motion.fade())
        assertEquals(enter, motion.sheetEnter())
        assertEquals(exit, motion.sheetExit())
        assertEquals(enter, motion.disclosureEnter())
        assertEquals(exit, motion.disclosureExit())
        for (forward in listOf(true, false)) {
            for (transform in listOf(motion.pushTransition(forward), motion.valueTransition(increasing = forward))) {
                assertEquals(enter, transform.targetContentEnter)
                assertEquals(exit, transform.initialContentExit)
            }
        }
    }

    @Test
    fun standardMotionKeepsSpatialTransitions() {
        val motion = EasyPaperMotion()
        val fade = fadeIn(motion.fade())
        assertNotEquals(fade, motion.sheetEnter())
        assertNotEquals(fade, motion.disclosureEnter())
        assertNotEquals(fade, motion.pushTransition(forward = true).targetContentEnter)
        assertNotEquals(fade, motion.valueTransition(increasing = true).targetContentEnter)
    }
}
