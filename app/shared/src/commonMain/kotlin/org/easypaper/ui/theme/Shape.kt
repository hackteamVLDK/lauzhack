package org.easypaper.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Rounded rectangle with iOS "continuous" corners: the curve eases out of the straight edge instead
 * of a plain circular arc (the squircle look of Apple's grouped lists and icons). Corner geometry
 * from PaintCode's measurement of UIKit: each corner spans 1.52866 × radius along both edges.
 * [top] = false keeps the bottom corners only; [bottom] = false the top ones (sheets).
 */
class ContinuousRoundedShape(
    private val radius: Dp,
    private val top: Boolean = true,
    private val bottom: Boolean = true,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val wanted = with(density) { radius.toPx() }
        val r = minOf(wanted, minOf(size.width, size.height) / 2f / EXTENT).coerceAtLeast(0f)
        if (r == 0f) return Outline.Rectangle(size.toRect())
        val w = size.width
        val h = size.height
        val path = Path()
        // Clockwise from the top-left corner's end; a = direction along the edge into the corner,
        // b = direction out of it. Point(u, v) = corner - u·a + v·b.
        val corners = listOf(
            Corner(Offset(w, 0f), Offset(1f, 0f), Offset(0f, 1f), top),
            Corner(Offset(w, h), Offset(0f, 1f), Offset(-1f, 0f), bottom),
            Corner(Offset(0f, h), Offset(-1f, 0f), Offset(0f, -1f), bottom),
            Corner(Offset(0f, 0f), Offset(0f, -1f), Offset(1f, 0f), top),
        )
        val startCorner = corners.last()
        if (startCorner.round) startCorner.at(r, 0f, EXTENT).let { path.moveTo(it.x, it.y) } else path.moveTo(0f, 0f)
        for (c in corners) {
            if (!c.round) {
                path.lineTo(c.point.x, c.point.y)
                continue
            }
            c.at(r, EXTENT, 0f).let { path.lineTo(it.x, it.y) }
            for (seg in SEGMENTS) {
                val c1 = c.at(r, seg[0], seg[1])
                val c2 = c.at(r, seg[2], seg[3])
                val end = c.at(r, seg[4], seg[5])
                path.cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
            }
        }
        path.close()
        return Outline.Generic(path)
    }

    private class Corner(val point: Offset, val a: Offset, val b: Offset, val round: Boolean) {
        fun at(r: Float, u: Float, v: Float) = point - a * (u * r) + b * (v * r)
    }

    override fun equals(other: Any?) =
        other is ContinuousRoundedShape && other.radius == radius && other.top == top && other.bottom == bottom

    override fun hashCode() = (radius.hashCode() * 31 + top.hashCode()) * 31 + bottom.hashCode()

    private companion object {
        const val EXTENT = 1.52866483f
        // cp1 (u, v), cp2 (u, v), end (u, v) in units of the radius, for the corner at (w, 0).
        val SEGMENTS = listOf(
            floatArrayOf(1.08849323f, 0f, 0.86840689f, 0.02221220f, 0.63149399f, 0.07491100f),
            floatArrayOf(0.37282392f, 0.16905899f, 0.16905899f, 0.37282392f, 0.07491100f, 0.63149399f),
            floatArrayOf(0.02221220f, 0.86840689f, 0f, 1.08849323f, 0f, EXTENT),
        )
    }
}

private fun Size.toRect() = androidx.compose.ui.geometry.Rect(Offset.Zero, this)

/** Shapes by hierarchy (docs/design.md), not one radius for everything. */
@Immutable
data class EasyPaperShapes(
    val tile: Shape = ContinuousRoundedShape(7.dp),       // 29 dp category icon tiles
    val group: Shape = ContinuousRoundedShape(12.dp),     // inset grouped sections
    val button: Shape = ContinuousRoundedShape(12.dp),    // buttons stacked vertically
    val capsule: Shape = RoundedCornerShape(50),          // buttons in a row, badges, steppers
    val sheet: Shape = ContinuousRoundedShape(16.dp, bottom = false),
    val small: Shape = ContinuousRoundedShape(6.dp),      // thumbnails, source links
)

/** Stock Material components need corner-based shapes; same radii, circular arcs. */
internal fun materialShapes() = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(7.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
)
