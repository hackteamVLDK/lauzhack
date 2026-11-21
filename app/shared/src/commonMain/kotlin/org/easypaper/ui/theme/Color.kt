package org.easypaper.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Semantic colors from docs/design.md (iOS names in comments). Screens use only these tokens,
 * never hex values. Light and dark are both measured for WCAG AA in ThemeTest.
 */
@Immutable
data class EasyPaperColors(
    val background: Color,     // systemGroupedBackground: behind grouped sections
    val cell: Color,           // secondarySystemGroupedBackground: sections, sidebar rows
    val cellRaised: Color,     // tertiary: sheets, popovers, nested groups
    val label: Color,          // primary text
    val secondaryLabel: Color, // dates, units, footers (Increase Contrast value)
    val separator: Color,      // hairlines between rows, decoration only
    val fill: Color,           // gray buttons, badges, steppers (systemGray5)
    val draftLabel: Color,     // small draft badge text on fill, at least 4.5:1
    val selectedRowBackground: Color, // sidebar selection, including small date text
    val selectedRowLabel: Color, // all text on selectedRowBackground, at least 4.5:1
    val scrim: Color,          // modal sheet background dimming
    val tint: Color,           // the one accent as a fill: prominent buttons, selection
    val link: Color,           // the accent as text: plain and tinted buttons, source links
    val onTint: Color,         // text and icons on a filled tint button
    val confirmed: Color,      // "Confirmed" text next to a check icon
    val destructive: Color,    // delete, errors (always with an icon and a sentence)
    val review: Color,         // background of a field the doctor still has to confirm
    val switchOn: Color,       // track of a switch that is on (systemGreen, as on iOS)
    val isDark: Boolean,
)

val LightColors = EasyPaperColors(
    background = Color(0xFFF2F2F7),
    cell = Color(0xFFFFFFFF),
    cellRaised = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0xFF6C6C70),
    separator = Color(0xFFC6C6C8),
    fill = Color(0xFFE5E5EA),
    draftLabel = Color(0xFF5C5C60),
    selectedRowBackground = Color(0xFF0066CC),
    selectedRowLabel = Color(0xFFFFFFFF),
    scrim = Color(0x66000000),
    tint = Color(0xFF0066CC),
    link = Color(0xFF0066CC),
    onTint = Color(0xFFFFFFFF),
    confirmed = Color(0xFF1F7A35),
    destructive = Color(0xFFD70015),
    review = Color(0xFFFFF4CC),
    switchOn = Color(0xFF34C759),
    isDark = false,
)

val DarkColors = EasyPaperColors(
    background = Color(0xFF000000),
    cell = Color(0xFF1C1C1E),
    cellRaised = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0xFF98989F),
    separator = Color(0xFF38383A),
    fill = Color(0xFF2C2C2E),
    draftLabel = Color(0xFF98989F),
    selectedRowBackground = Color(0xFF0066CC),
    selectedRowLabel = Color(0xFFFFFFFF),
    scrim = Color(0x66000000),
    tint = Color(0xFF0A84FF),
    link = Color(0xFF409CFF),
    onTint = Color(0xFFFFFFFF),
    confirmed = Color(0xFF30DB5B),
    destructive = Color(0xFFFF6961),
    review = Color(0xFF3A3000),
    switchOn = Color(0xFF30D158),
    isDark = true,
)

/**
 * Kinds of patient data, colored like Apple Health. [tile] is only the background of the 29 dp icon
 * tile (white glyph); the category name as text uses [text], darker in light mode for contrast.
 */
enum class Category(val tile: Color, private val textLight: Color, private val textDark: Color) {
    BloodPressure(Color(0xFFFF2D55), Color(0xFFC9184A), Color(0xFFFF6482)),
    Water(Color(0xFF00C0E8), Color(0xFF00739C), Color(0xFF5CD7F5)),
    Food(Color(0xFF34C759), Color(0xFF1F7A35), Color(0xFF30DB5B)),
    Medicines(Color(0xFF00C3D0), Color(0xFF00727A), Color(0xFF40DCE6)),
    Questionnaire(Color(0xFF6155F5), Color(0xFF5E5CE6), Color(0xFF9391FF)),
    Diary(Color(0xFFFF8D28), Color(0xFFB25000), Color(0xFFFFB340)),
    DoctorQuestion(Color(0xFF8E8E93), Color(0xFF6C6C70), Color(0xFF98989F)),
    Measurements(Color(0xFFAF52DE), Color(0xFF8944AB), Color(0xFFDA8FFF)); // systemPurple, as Body Measurements in Health

    fun text(colors: EasyPaperColors): Color = if (colors.isDark) textDark else textLight
}

/** WCAG 2.1 contrast ratio of two opaque colors (1..21). */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}
