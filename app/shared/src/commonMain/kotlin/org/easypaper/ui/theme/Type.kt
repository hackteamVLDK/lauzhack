package org.easypaper.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.inter_bold
import easypaper.app.shared.generated.resources.inter_display_bold
import easypaper.app.shared.generated.resources.inter_display_semibold
import easypaper.app.shared.generated.resources.inter_medium
import easypaper.app.shared.generated.resources.inter_regular
import easypaper.app.shared.generated.resources.inter_semibold
import org.jetbrains.compose.resources.Font
import kotlin.math.exp

/** Apple's text styles at the default Dynamic Type size (sp = pt), see docs/design.md. */
@Immutable
data class EasyPaperTypography(
    val largeTitle: TextStyle,  // 34/41 bold: "Today", patient name
    val title1: TextStyle,      // 28/34 semibold: big values, use with tabular()
    val title2: TextStyle,      // 22/28 bold: doctor section titles
    val title3: TextStyle,      // 20/25 semibold: sheet titles
    val headline: TextStyle,    // 17/22 semibold: row titles, button labels
    val body: TextStyle,        // 17/22 regular: default text, patient's own words
    val callout: TextStyle,     // 16/21 regular: doctor summary text
    val subheadline: TextStyle, // 15/20 regular: category names (semibold), secondary lines
    val footnote: TextStyle,    // 13/18 regular: section footers, times, source links
    val caption1: TextStyle,    // 12/16 regular: chart axes, badges
)

/** Inter's dynamic metrics: tighter tracking as the size grows, like SF Pro. */
internal fun interTracking(sizeSp: Float) = (-0.0223f + 0.185f * exp(-0.1745f * sizeSp)).em

private fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = interTracking(size.toFloat()),
)

/** [text] is Inter (sizes under 20), [display] Inter Display (20 and above), as SF Pro Text / Display. */
fun easyPaperTypography(text: FontFamily, display: FontFamily) = EasyPaperTypography(
    largeTitle = style(display, 34, 41, FontWeight.Bold),
    title1 = style(display, 28, 34, FontWeight.SemiBold),
    title2 = style(display, 22, 28, FontWeight.Bold),
    title3 = style(display, 20, 25, FontWeight.SemiBold),
    headline = style(text, 17, 22, FontWeight.SemiBold),
    body = style(text, 17, 22, FontWeight.Normal),
    callout = style(text, 16, 21, FontWeight.Normal),
    subheadline = style(text, 15, 20, FontWeight.Normal),
    footnote = style(text, 13, 18, FontWeight.Normal),
    caption1 = style(text, 12, 16, FontWeight.Normal),
)

/** Numbers that line up in columns: blood pressure, times, counts. */
fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

@Composable
internal fun interText() = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

@Composable
internal fun interDisplay() = FontFamily(
    Font(Res.font.inter_display_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_display_bold, FontWeight.Bold),
)

/** Material 3 slots, so stock components (buttons, text fields, lists) inherit the iOS styles. */
internal fun EasyPaperTypography.toMaterial() = Typography(
    displayLarge = largeTitle,
    displayMedium = largeTitle,
    displaySmall = largeTitle,
    headlineLarge = title1,
    headlineMedium = title2,
    headlineSmall = title3,
    titleLarge = title3,
    titleMedium = headline,
    titleSmall = subheadline,
    bodyLarge = body,
    bodyMedium = callout,
    bodySmall = footnote,
    labelLarge = headline,
    labelMedium = caption1,
    labelSmall = caption1,
)
