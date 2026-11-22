package org.easypaper.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.ic_chat_bubble
import easypaper.app.shared.generated.resources.ic_checklist
import easypaper.app.shared.generated.resources.ic_chevron_right
import easypaper.app.shared.generated.resources.ic_edit_note
import easypaper.app.shared.generated.resources.ic_favorite
import easypaper.app.shared.generated.resources.ic_pill
import easypaper.app.shared.generated.resources.ic_restaurant
import easypaper.app.shared.generated.resources.ic_scale
import easypaper.app.shared.generated.resources.ic_water_drop
import org.easypaper.ui.theme.Category
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Glyph of each category (Material Symbols Rounded, filled). */
val Category.icon: DrawableResource
    get() = when (this) {
        Category.BloodPressure -> Res.drawable.ic_favorite
        Category.Water -> Res.drawable.ic_water_drop
        Category.Food -> Res.drawable.ic_restaurant
        Category.Medicines -> Res.drawable.ic_pill
        Category.Questionnaire -> Res.drawable.ic_checklist
        Category.Diary -> Res.drawable.ic_edit_note
        Category.DoctorQuestion -> Res.drawable.ic_chat_bubble
        Category.Measurements -> Res.drawable.ic_scale
    }

/** 29 dp rounded tile in the category color with a white glyph, like Settings and Health on iOS. */
@Composable
fun IconTile(category: Category, modifier: Modifier = Modifier) {
    val spacing = EasyPaperTheme.spacing
    Box(
        modifier.size(spacing.tile).clip(EasyPaperTheme.shapes.tile).background(category.tile),
        contentAlignment = Alignment.Center,
    ) {
        // Decorative: the category name next to it carries the meaning.
        Icon(painterResource(category.icon), contentDescription = null, tint = Color.White,
            modifier = Modifier.size(spacing.tileIcon))
    }
}

/**
 * iOS inset grouped section: optional header above, rows in a rounded `cell` block, optional footer
 * below. Put [RowSeparator] between rows.
 */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    Column(modifier.fillMaxWidth()) {
        if (header != null) {
            Text(header, style = type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.label,
                modifier = Modifier.padding(start = spacing.m, end = spacing.m, bottom = spacing.xs))
        }
        Column(Modifier.fillMaxWidth().clip(EasyPaperTheme.shapes.group).background(colors.cell), content = content)
        if (footer != null) {
            Text(footer, style = type.footnote, color = colors.secondaryLabel,
                modifier = Modifier.padding(start = spacing.m, end = spacing.m, top = spacing.xs))
        }
    }
}

/** Hairline between rows of a [GroupedSection], inset to where the row text starts. */
@Composable
fun RowSeparator(inset: Dp = EasyPaperTheme.spacing.let { it.m + it.tile + it.s }) {
    HorizontalDivider(Modifier.padding(start = inset), thickness = Dp.Hairline, color = EasyPaperTheme.colors.separator)
}

/**
 * A row with a category tile and the category name in its color, an optional trailing element
 * (time, button) and optional content below the name (value, text). [onClick] adds the chevron.
 */
@Composable
fun CategoryRow(
    category: Category,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier.fillMaxWidth().then(clickable).heightIn(min = spacing.rowMinHeight)
            .padding(horizontal = spacing.m, vertical = spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        IconTile(category)
        Spacer(Modifier.width(spacing.s))
        Column(Modifier.weight(1f)) {
            Row(Modifier.heightIn(min = spacing.tile), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = EasyPaperTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = category.text(colors), modifier = Modifier.weight(1f))
                trailing?.invoke()
                if (onClick != null) {
                    Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null,
                        tint = colors.separator, modifier = Modifier.size(spacing.rowIcon))
                }
            }
            if (content != null) {
                Column(Modifier.padding(top = spacing.xxs), content = content)
            }
        }
    }
}

/** Shared background press feedback, with only disabled controls dimmed. */
@Composable
private fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .alpha(if (enabled) 1f else 0.4f)
        .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
}

/** The one prominent action of a view: filled tint, 50 dp, rounded rectangle (stacked) or full width. */
@Composable
fun ProminentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = EasyPaperTheme.colors
    Box(
        modifier.heightIn(min = EasyPaperTheme.spacing.prominentButtonHeight).clip(EasyPaperTheme.shapes.button)
            .background(colors.tint).pressable(onClick, enabled).padding(horizontal = EasyPaperTheme.spacing.l),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = EasyPaperTheme.typography.headline, color = colors.onTint)
    }
}

/** Secondary action in a row: tint at 15 % behind tint text, capsule. Touch target stays 48 dp. */
@Composable
fun TintedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    enabled: Boolean = true,
) = SmallButton(text, onClick, modifier, icon, enabled, EasyPaperTheme.colors.tint.copy(alpha = 0.15f), EasyPaperTheme.colors.link)

/** Neutral action: gray fill, label text, capsule (steppers, "Skip"). */
@Composable
fun GrayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    enabled: Boolean = true,
) = SmallButton(text, onClick, modifier, icon, enabled, EasyPaperTheme.colors.fill, EasyPaperTheme.colors.label)

@Composable
private fun SmallButton(
    text: String, onClick: () -> Unit, modifier: Modifier, icon: DrawableResource?, enabled: Boolean,
    container: Color, content: Color,
) {
    val spacing = EasyPaperTheme.spacing
    Row(
        modifier.minimumInteractiveComponentSize().clip(EasyPaperTheme.shapes.capsule).background(container)
            .pressable(onClick, enabled).padding(horizontal = spacing.s + spacing.xxs, vertical = spacing.xs - 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        // centered when the button is wider than its label (a full-width "+ Add widgets"); no change otherwise
        horizontalArrangement = Arrangement.spacedBy(spacing.xxs, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        if (text.isNotEmpty()) Text(text, style = EasyPaperTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold), color = content)
    }
}

/** Least prominent action: tint text only ("Edit", "Write how you feel"). */
@Composable
fun PlainButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(modifier.minimumInteractiveComponentSize().pressable(onClick, enabled), contentAlignment = Alignment.CenterStart) {
        Text(text, style = EasyPaperTheme.typography.body, color = EasyPaperTheme.colors.link)
    }
}

/** Gray capsule that marks AI text as a draft until the doctor confirms it. */
@Composable
fun DraftBadge(text: String, modifier: Modifier = Modifier) {
    val spacing = EasyPaperTheme.spacing
    Text(
        text,
        style = EasyPaperTheme.typography.caption1.copy(fontWeight = FontWeight.Medium),
        color = EasyPaperTheme.colors.draftLabel,
        modifier = modifier.clip(EasyPaperTheme.shapes.capsule).background(EasyPaperTheme.colors.fill)
            .padding(horizontal = spacing.xs, vertical = 2.dp),
    )
}

/** iOS switch: 51 × 31 capsule track, white 27 dp thumb with a soft shadow; green when on. */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = EasyPaperTheme.colors
    val motion = EasyPaperTheme.motion
    // Off track: systemGray5-like in light, a separator-gray in dark so it stays visible on `cell`.
    val track by animateColorAsState(
        if (checked) colors.switchOn else if (colors.isDark) colors.separator else colors.fill,
        animationSpec = motion.smooth.spec(),
        label = "switch track",
    )
    Box(
        modifier.minimumInteractiveComponentSize()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange,
                interactionSource = remember { MutableInteractionSource() }, indication = LocalIndication.current)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(SWITCH_WIDTH, SWITCH_HEIGHT).clip(EasyPaperTheme.shapes.capsule).background(track).padding(SWITCH_INSET)) {
            if (motion.reduceMotion) {
                Crossfade(checked, animationSpec = motion.fade(), label = "switch thumb fade") { on ->
                    SwitchThumb(if (on) SWITCH_TRAVEL else 0.dp)
                }
            } else {
                val thumbOffset by animateDpAsState(
                    if (checked) SWITCH_TRAVEL else 0.dp,
                    animationSpec = motion.snappy.spec(),
                    label = "switch thumb",
                )
                SwitchThumb(thumbOffset)
            }
        }
    }
}

@Composable
private fun SwitchThumb(offset: Dp) {
    Box(Modifier.offset(x = offset).size(SWITCH_THUMB).shadow(SWITCH_INSET, CircleShape).background(Color.White, CircleShape))
}

private val SWITCH_WIDTH = 51.dp
private val SWITCH_HEIGHT = 31.dp
private val SWITCH_INSET = 2.dp
private val SWITCH_THUMB = 27.dp
private val SWITCH_TRAVEL = SWITCH_WIDTH - SWITCH_THUMB - SWITCH_INSET * 2
