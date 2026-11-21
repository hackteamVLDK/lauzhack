package org.easypaper.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ThemeTest {
    private val themes = listOf("light" to LightColors, "dark" to DarkColors)

    private fun assertContrast(min: Float, fg: Color, bg: Color, what: String) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(ratio >= min, "$what: contrast $ratio < $min")
    }

    @Test
    fun textColorsMeetWcagAaInBothThemes() {
        for ((name, c) in themes) {
            for (bg in listOf(c.cell, c.background, c.cellRaised)) {
                assertContrast(4.5f, c.label, bg, "$name label")
                assertContrast(4.5f, c.secondaryLabel, bg, "$name secondaryLabel")
                assertContrast(4.5f, c.link, bg, "$name link")
                assertContrast(4.5f, c.confirmed, bg, "$name confirmed")
                assertContrast(4.5f, c.destructive, bg, "$name destructive")
                Category.entries.forEach { assertContrast(4.5f, it.text(c), bg, "$name ${it.name} name") }
            }
            assertContrast(4.5f, c.label, c.review, "$name label on review")
        }
    }

    @Test
    fun buttonLabelsOnTint() {
        // Prominent button labels are 17 sp semibold (large text): 3:1 is the AA minimum, light reaches 4.5.
        assertContrast(4.5f, LightColors.onTint, LightColors.tint, "light onTint")
        assertContrast(3f, DarkColors.onTint, DarkColors.tint, "dark onTint")
    }

    @Test
    fun appleTextStylesAtDefaultSize() {
        val t = easyPaperTypography(FontFamily.SansSerif, FontFamily.SansSerif)
        val expected = listOf(
            Triple(t.largeTitle, 34 to 41, FontWeight.Bold), Triple(t.title1, 28 to 34, FontWeight.SemiBold),
            Triple(t.title2, 22 to 28, FontWeight.Bold), Triple(t.title3, 20 to 25, FontWeight.SemiBold),
            Triple(t.headline, 17 to 22, FontWeight.SemiBold), Triple(t.body, 17 to 22, FontWeight.Normal),
            Triple(t.callout, 16 to 21, FontWeight.Normal), Triple(t.subheadline, 15 to 20, FontWeight.Normal),
            Triple(t.footnote, 13 to 18, FontWeight.Normal), Triple(t.caption1, 12 to 16, FontWeight.Normal),
        )
        for ((style, sizes, weight) in expected) {
            assertEquals(sizes.first.toFloat(), style.fontSize.value)
            assertEquals(sizes.second.toFloat(), style.lineHeight.value)
            assertEquals(weight, style.fontWeight)
        }
        // Larger text gets tighter tracking, like SF Pro.
        assertTrue(t.largeTitle.letterSpacing.value < t.body.letterSpacing.value)
        assertEquals("tnum", t.title1.tabular().fontFeatureSettings)
    }

    @Test
    fun continuousCornersFillTheBoundsAndClampOnSmallShapes() {
        val density = Density(2f)
        for (size in listOf(Size(300f, 120f), Size(40f, 40f), Size(500f, 20f))) {
            val outline = ContinuousRoundedShape(12.dp).createOutline(size, LayoutDirection.Ltr, density)
            assertIs<Outline.Generic>(outline)
            assertNear(Rect(0f, 0f, size.width, size.height), outline.path.getBounds(), "bounds for $size")
        }
        val sheet = ContinuousRoundedShape(16.dp, bottom = false).createOutline(Size(200f, 100f), LayoutDirection.Ltr, density)
        assertNear(Rect(0f, 0f, 200f, 100f), (sheet as Outline.Generic).path.getBounds(), "sheet")
    }

    private fun assertNear(expected: Rect, actual: Rect, what: String) {
        val near = listOf(expected.left to actual.left, expected.top to actual.top, expected.right to actual.right,
            expected.bottom to actual.bottom).all { (e, a) -> kotlin.math.abs(e - a) < 0.01f }
        assertTrue(near, "$what: expected $expected, got $actual")
    }
}
