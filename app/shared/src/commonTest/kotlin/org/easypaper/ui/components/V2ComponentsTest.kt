package org.easypaper.ui.components

import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.ic_qr_code_2
import org.easypaper.ui.theme.DarkColors
import org.easypaper.ui.theme.LightColors
import org.easypaper.ui.theme.contrastRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class V2ComponentsTest {
    @Test
    fun segmentSelectionPreservesFirstAndLastOptions() {
        assertEquals(0, segmentSelection(optionCount = 4, selected = 0))
        assertEquals(3, segmentSelection(optionCount = 4, selected = 3))
    }

    @Test
    fun segmentSelectionHandlesUnansweredQuestionsAndChangingOptions() {
        assertNull(segmentSelection(optionCount = 4, selected = null))
        assertNull(segmentSelection(optionCount = 4, selected = -1))
        assertNull(segmentSelection(optionCount = 4, selected = 4))
        assertNull(segmentSelection(optionCount = 0, selected = 0))
        assertNull(segmentSelection(optionCount = 2, selected = 3))
    }

    @Test
    fun disabledSheetOptionNeverDispatchesEvenWhenItHasABadge() {
        var calls = 0
        val option = SheetOption(
            title = "Nearby", subtitle = null, icon = Res.drawable.ic_qr_code_2,
            enabled = false, badge = "Later", onClick = { calls++ },
        )

        repeat(2) { assertFalse(option.performClick()) }
        assertFalse(option.copy(badge = null).performClick())
        assertEquals(0, calls)
    }

    @Test
    fun enabledSheetOptionDispatchesExactlyOncePerClick() {
        var calls = 0
        val option = SheetOption(
            title = "QR", subtitle = "One-time use", icon = Res.drawable.ic_qr_code_2,
            enabled = true, onClick = { calls++ },
        )

        assertTrue(option.performClick())
        assertEquals(1, calls)
        assertFalse(option.copy(enabled = false).performClick())
        assertEquals(1, calls)
        assertTrue(option.performClick())
        assertEquals(2, calls)
    }

    @Test
    fun selectedSegmentIsVisibleInBothThemesAndKeepsItsLabelReadable() {
        for (colors in listOf(LightColors, DarkColors)) {
            val thumb = segmentThumb(colors)
            assertTrue(contrastRatio(colors.label, thumb) >= 4.5f, "label on thumb, dark=${colors.isDark}")
        }
        // dark: fill and cellRaised are the same grey, so the thumb must stand out from the track itself
        assertTrue(contrastRatio(segmentThumb(DarkColors), DarkColors.fill) >= 3f, "dark thumb vs track")
    }

    @Test
    fun aDecimalFieldTakesOnlyPlainNumbers() {
        assertTrue(isDecimalInput("72,5", 5))
        assertTrue(isDecimalInput("72.", 5))
        assertTrue(isDecimalInput("", 5))
        assertFalse(isDecimalInput("-72,5", 6)) // a sign is refused, not dropped
        assertFalse(isDecimalInput("7.2.5", 6)) // two separators
        assertFalse(isDecimalInput("123456", 5)) // too long is refused, not cut
        assertFalse(isDecimalInput("7a", 5))
    }
}
