package org.easypaper.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.easypaper.ui.components.PressIndication
import org.easypaper.ui.components.reducedMotion

/** Spacing and sizes from docs/design.md. Screens use these instead of dp literals. */
@Immutable
data class EasyPaperSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val s: Dp = 12.dp,
    val m: Dp = 16.dp,
    val l: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val screenMargin: Dp = 16.dp,      // phone
    val screenMarginWide: Dp = 20.dp,  // desktop / web
    val sectionGap: Dp = 24.dp,        // between grouped sections
    val rowMinHeight: Dp = 48.dp,      // touch target and minimum row height
    val tile: Dp = 29.dp,              // category icon tile
    val tileIcon: Dp = 18.dp,          // glyph inside a tile
    val rowIcon: Dp = 22.dp,           // standalone icons in rows
    val prominentButtonHeight: Dp = 50.dp,
    val sidebarWidth: Dp = 260.dp,     // doctor app
    val contentMaxWidth: Dp = 720.dp,  // doctor content column
    val patientMaxWidth: Dp = 520.dp,
    val categoryCardWidth: Dp = 156.dp,
    val categoryCardMinHeight: Dp = 176.dp,
    val browseTileMinHeight: Dp = 116.dp,
    val widgetMinHeight: Dp = 208.dp,
    val summaryChartWidth: Dp = 88.dp,
    val summaryChartHeight: Dp = 48.dp,
    val summaryTrendChartHeight: Dp = 64.dp,
    val summaryChartDotRadius: Dp = 3.dp,
    val summaryRemoveSize: Dp = 24.dp,
    val collageTall: Dp = 164.dp,
    val collageShort: Dp = 120.dp,
    val tourIllustrationHeight: Dp = 224.dp,
    val tourPageIndicatorSize: Dp = 8.dp,
    val heroIcon: Dp = 64.dp,
    val sharingHeroIcon: Dp = 48.dp,
    val emptyAddSize: Dp = 80.dp,
    val floatingBarMaxWidth: Dp = 440.dp,
    val floatingBarShadow: Dp = 6.dp,
    val glyphStroke: Dp = 2.dp,
)

private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalTypography = staticCompositionLocalOf<EasyPaperTypography> { error("EasyPaperTheme is missing") }
private val LocalSpacing = staticCompositionLocalOf { EasyPaperSpacing() }
private val LocalShapes = staticCompositionLocalOf { EasyPaperShapes() }
private val LocalMotion = staticCompositionLocalOf { EasyPaperMotion() }

/**
 * The Easy Paper look (Apple HIG, docs/design.md). Wrap every screen; read tokens through
 * [EasyPaperTheme.colors], [EasyPaperTheme.typography], [EasyPaperTheme.spacing], [EasyPaperTheme.shapes],
 * [EasyPaperTheme.motion].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasyPaperTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val text = interText()
    val display = interDisplay()
    val typography = remember(text, display) { easyPaperTypography(text, display) }
    val reduceMotion = reducedMotion()
    val motion = remember(reduceMotion) { EasyPaperMotion(reduceMotion) }
    val indication = remember(colors.fill, motion) { PressIndication(colors.fill, motion.pressFadeDurationMillis) }
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides typography,
        LocalSpacing provides EasyPaperSpacing(),
        LocalShapes provides EasyPaperShapes(),
        LocalMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = typography.toMaterial(),
            shapes = materialShapes(),
        ) {
            // MaterialTheme installs its own LocalIndication, so override it inside the theme.
            CompositionLocalProvider(
                LocalRippleConfiguration provides null,
                LocalIndication provides indication,
                content = content,
            )
        }
    }
}

object EasyPaperTheme {
    val colors: EasyPaperColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val typography: EasyPaperTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
    val spacing: EasyPaperSpacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val shapes: EasyPaperShapes
        @Composable @ReadOnlyComposable get() = LocalShapes.current
    val motion: EasyPaperMotion
        @Composable @ReadOnlyComposable get() = LocalMotion.current
}

/** Material roles for stock components; no baseline purple, no tonal elevation tint. */
internal fun EasyPaperColors.toMaterial(): ColorScheme {
    val tintFill = tint.copy(alpha = 0.15f)
    return if (isDark) darkColorScheme(
        primary = tint, onPrimary = onTint, primaryContainer = tintFill, onPrimaryContainer = tint,
        secondary = tint, onSecondary = onTint, secondaryContainer = fill, onSecondaryContainer = label,
        tertiary = tint, onTertiary = onTint,
        background = background, onBackground = label,
        surface = background, onSurface = label, surfaceVariant = fill, onSurfaceVariant = secondaryLabel,
        surfaceTint = background, inverseSurface = label, inverseOnSurface = background, inversePrimary = tint,
        surfaceContainerLowest = cell, surfaceContainerLow = cell, surfaceContainer = cell,
        surfaceContainerHigh = cellRaised, surfaceContainerHighest = cellRaised,
        surfaceBright = cellRaised, surfaceDim = background,
        outline = separator, outlineVariant = separator,
        error = destructive, onError = onTint, scrim = background.copy(alpha = 0.4f),
    ) else lightColorScheme(
        primary = tint, onPrimary = onTint, primaryContainer = tintFill, onPrimaryContainer = tint,
        secondary = tint, onSecondary = onTint, secondaryContainer = fill, onSecondaryContainer = label,
        tertiary = tint, onTertiary = onTint,
        background = background, onBackground = label,
        surface = background, onSurface = label, surfaceVariant = fill, onSurfaceVariant = secondaryLabel,
        surfaceTint = background, inverseSurface = label, inverseOnSurface = background, inversePrimary = tint,
        surfaceContainerLowest = cell, surfaceContainerLow = cell, surfaceContainer = cell,
        surfaceContainerHigh = cellRaised, surfaceContainerHighest = cellRaised,
        surfaceBright = cellRaised, surfaceDim = background,
        outline = separator, outlineVariant = separator,
        error = destructive, onError = onTint, scrim = label.copy(alpha = 0.4f),
    )
}
