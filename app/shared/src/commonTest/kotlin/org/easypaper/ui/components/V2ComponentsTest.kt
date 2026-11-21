package org.easypaper.ui.components

import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.ic_qr_code_2
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
}
