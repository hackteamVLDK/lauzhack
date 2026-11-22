package org.easypaper.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.v2_cancel
import easypaper.app.shared.generated.resources.v2_clear_search
import easypaper.app.shared.generated.resources.v2_later
import easypaper.app.shared.generated.resources.v2_open_profile
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * iOS capsule, 32 dp visually at the default font size, with separate 48 dp tab targets.
 * [descriptions] are spoken instead of short labels ("W" -> "Week"); [role] is Tab for a view
 * switch (period) and RadioButton when the control answers a question.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    descriptions: List<String>? = null,
    role: Role = Role.Tab,
) = V2SegmentedControl(options, selected, onSelect, modifier, descriptions, role)

/** Nullable selection also supports existing forms whose question has not been answered yet. */
@Composable
internal fun V2SegmentedControl(
    options: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    descriptions: List<String>? = null,
    role: Role = Role.Tab,
) {
    if (options.isEmpty()) return
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val shape = EasyPaperTheme.shapes.capsule
    val motion = EasyPaperTheme.motion
    val selectedIndex = segmentSelection(options.size, selected)
    // In dark mode fill and cellRaised are the same grey and a shadow is invisible, so the thumb
    // is a lighter grey there (like UISegmentedControl's dark thumb); light keeps the white card.
    val thumb = segmentThumb(colors)
    Box(modifier.fillMaxWidth()) {
        // The vertical inset reserves touch space outside the visible track. As labels grow,
        // the row and track grow together, rather than clipping Dynamic Type text.
        BoxWithConstraints(Modifier.matchParentSize().padding(vertical = spacing.xs)) {
            val segmentWidth = maxWidth / options.size
            Box(Modifier.fillMaxSize().background(colors.fill, shape))
            if (selectedIndex != null) {
                val position = if (motion.reduceMotion) selectedIndex.toFloat() else {
                    val animated by animateFloatAsState(
                        selectedIndex.toFloat(), animationSpec = motion.snappy.spec(), label = "segment selection",
                    )
                    animated
                }
                Box(
                    Modifier.offset(x = segmentWidth * position).width(segmentWidth)
                        .fillMaxSize().padding(SEGMENT_INSET)
                        .shadow(SEGMENT_SHADOW, shape, ambientColor = colors.scrim, spotColor = colors.scrim)
                        .background(thumb, shape),
                )
            }
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val description = descriptions?.getOrNull(index)
                Box(
                    Modifier.weight(1f).minimumInteractiveComponentSize().heightIn(min = spacing.rowMinHeight)
                        .clip(shape)
                        .selectable(
                            selected = isSelected, role = role,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = { onSelect(index) },
                        )
                        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
                        .heightIn(min = SEGMENT_HEIGHT)
                        .padding(horizontal = spacing.xs, vertical = spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option, color = colors.label, textAlign = TextAlign.Center,
                        style = EasyPaperTheme.typography.subheadline.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                }
            }
        }
    }
}

/** Selected segment: the raised white cell in light mode, a lighter grey than the track in dark. */
internal fun segmentThumb(colors: org.easypaper.ui.theme.EasyPaperColors): androidx.compose.ui.graphics.Color =
    if (colors.isDark) colors.label.copy(alpha = 0.35f).compositeOver(colors.fill) else colors.cellRaised

/** An invalid or missing index means no selection, including an empty options list. */
internal fun segmentSelection(optionCount: Int, selected: Int?): Int? =
    selected?.takeIf { it >= 0 && it < optionCount }

data class SheetOption(
    val title: String,
    val subtitle: String?,
    val icon: DrawableResource,
    val enabled: Boolean,
    val badge: String? = null,
    val onClick: () -> Unit,
)

/** Shared dispatch guard, also used by non-UI callers; unavailable actions never run. */
internal fun SheetOption.performClick(): Boolean {
    if (!enabled) return false
    onClick()
    return true
}

/** Present last in a full-size Box, like [Sheet]. The owner controls dismissal after selection. */
@Composable
fun OptionsSheet(visible: Boolean, title: String, options: List<SheetOption>, onDismiss: () -> Unit) {
    val spacing = EasyPaperTheme.spacing
    Sheet(visible, title, stringResource(Res.string.v2_cancel), onDismiss) {
        GroupedSection {
            options.forEachIndexed { index, option ->
                if (index > 0) RowSeparator()
                SheetOptionRow(option)
            }
        }
        Spacer(Modifier.height(spacing.xs))
    }
}

@Composable
private fun SheetOptionRow(option: SheetOption) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    val label = if (option.enabled) colors.label else colors.secondaryLabel
    val badge = option.badge ?: if (!option.enabled) stringResource(Res.string.v2_later) else null
    Row(
        Modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight)
            .clickable(
                enabled = option.enabled, role = Role.Button,
                interactionSource = remember { MutableInteractionSource() }, indication = LocalIndication.current,
                onClick = { option.performClick() },
            )
            .semantics(mergeDescendants = true) { if (!option.enabled) disabled() }
            .padding(horizontal = spacing.m, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Box(
            Modifier.size(spacing.tile).background(
                if (option.enabled) colors.tint else colors.fill, EasyPaperTheme.shapes.tile,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(option.icon), contentDescription = null,
                tint = if (option.enabled) colors.onTint else colors.secondaryLabel,
                modifier = Modifier.size(spacing.tileIcon),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(option.title, style = type.body, color = label)
            if (option.subtitle != null) Text(option.subtitle, style = type.footnote, color = colors.secondaryLabel)
            // Stacking the badge avoids squeezing the title at 200% font size.
            if (badge != null) DraftBadge(badge)
        }
    }
}

/** Search capsule; the field and clear action retain distinct accessibility nodes. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    Row(
        modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight)
            .clip(EasyPaperTheme.shapes.capsule).background(colors.fill).padding(start = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Canvas(Modifier.size(spacing.rowIcon)) {
            val stroke = Stroke(width = SEARCH_STROKE.toPx(), cap = StrokeCap.Round)
            drawCircle(colors.secondaryLabel, radius = size.minDimension * 0.3f,
                center = Offset(size.width * 0.4f, size.height * 0.4f), style = stroke)
            drawLine(colors.secondaryLabel, Offset(size.width * 0.62f, size.height * 0.62f),
                Offset(size.width * 0.9f, size.height * 0.9f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            textStyle = type.body.copy(color = colors.label), cursorBrush = SolidColor(colors.tint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f).heightIn(min = spacing.rowMinHeight)
                .semantics { contentDescription = placeholder },
            decorationBox = { field ->
                Box(Modifier.padding(vertical = spacing.xs), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = type.body, color = colors.secondaryLabel,
                        modifier = Modifier.clearAndSetSemantics {})
                    field()
                }
            },
        )
        if (value.isNotEmpty()) {
            val clearLabel = stringResource(Res.string.v2_clear_search)
            Box(
                Modifier.minimumInteractiveComponentSize().size(spacing.rowMinHeight)
                    .clip(EasyPaperTheme.shapes.capsule)
                    .clickable(role = Role.Button, onClick = { onValueChange("") })
                    .semantics { contentDescription = clearLabel },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(spacing.rowIcon)) {
                    val inset = size.minDimension * 0.28f
                    drawLine(colors.secondaryLabel, Offset(inset, inset), Offset(size.width - inset, size.height - inset),
                        strokeWidth = SEARCH_STROKE.toPx(), cap = StrokeCap.Round)
                    drawLine(colors.secondaryLabel, Offset(size.width - inset, inset), Offset(inset, size.height - inset),
                        strokeWidth = SEARCH_STROKE.toPx(), cap = StrokeCap.Round)
                }
            }
        } else {
            Spacer(Modifier.width(spacing.s))
        }
    }
}

/** Determinate progress, animated only after a value changes. Semantics report the target value. */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, size: Dp = EasyPaperTheme.spacing.rowMinHeight) {
    val colors = EasyPaperTheme.colors
    val motion = EasyPaperTheme.motion
    val target = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    val displayed = if (motion.reduceMotion) target else {
        val animated by animateFloatAsState(target, animationSpec = motion.snappy.spec(), label = "progress ring")
        animated.coerceIn(0f, 1f)
    }
    Canvas(modifier.size(size).semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) }) {
        val diameter = this.size.minDimension
        val stroke = diameter * RING_STROKE_FRACTION
        val topLeft = Offset((this.size.width - diameter + stroke) / 2f, (this.size.height - diameter + stroke) / 2f)
        val arcSize = Size(diameter - stroke, diameter - stroke)
        drawArc(colors.fill, startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(stroke))
        if (displayed > 0f) drawArc(colors.tint, startAngle = -90f, sweepAngle = displayed * 360f, useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** 36 dp visible avatar inside a 48 dp button. Initials are decorative to the action label. */
@Composable
fun AvatarButton(initials: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EasyPaperTheme.colors
    val label = stringResource(Res.string.v2_open_profile)
    Box(
        modifier.minimumInteractiveComponentSize().size(EasyPaperTheme.spacing.rowMinHeight)
            .clip(EasyPaperTheme.shapes.capsule).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(AVATAR_SIZE).background(colors.fill, EasyPaperTheme.shapes.capsule),
            contentAlignment = Alignment.Center) {
            if (initials.isNotBlank()) {
                // The circle has a fixed size and the button is named for screen readers, so the initials
                // stop growing where they would leave it (like iOS avatars at accessibility sizes).
                CappedFontScale(AVATAR_FONT_SCALE) {
                    Text(initials, style = EasyPaperTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.label, maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
                }
            } else {
                val spacing = EasyPaperTheme.spacing
                Canvas(Modifier.size(spacing.rowIcon)) {
                    val stroke = Stroke(spacing.glyphStroke.toPx(), cap = StrokeCap.Round)
                    drawCircle(colors.label, size.minDimension * 0.16f,
                        Offset(size.width * 0.5f, size.height * 0.25f), style = stroke)
                    drawArc(colors.label, 180f, 180f, false,
                        topLeft = Offset(size.width * 0.15f, size.height * 0.52f),
                        size = Size(size.width * 0.7f, size.height * 0.65f), style = stroke)
                }
            }
        }
    }
}

/** Floating capsule. The shell measures its full height to reserve space, including Dynamic Type. */
@Composable
fun FloatingTabBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val shape = EasyPaperTheme.shapes.capsule
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
        .padding(horizontal = spacing.screenMargin, vertical = spacing.s), contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = spacing.floatingBarMaxWidth).fillMaxWidth()
            .shadow(spacing.floatingBarShadow, shape, ambientColor = colors.scrim, spotColor = colors.scrim)
            .background(colors.cellRaised, shape).padding(spacing.xxs).selectableGroup()) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selected
                val foreground = if (isSelected) colors.onTint else colors.label
                Column(Modifier.weight(1f).clip(shape)
                    .background(if (isSelected) colors.tint else colors.cellRaised)
                    .selectable(isSelected, role = Role.Tab, onClick = { onSelect(index) })
                    .heightIn(min = spacing.rowMinHeight).padding(horizontal = spacing.xxs, vertical = spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Icon(painterResource(item.icon), null, Modifier.size(spacing.rowIcon), tint = foreground)
                    // Tab labels grow only so far (as in iOS tab bars): at 200 % a German label would break
                    // inside the word in a third of a phone width. The tab still says its full name.
                    CappedFontScale(TAB_LABEL_FONT_SCALE) {
                        Text(item.label, style = EasyPaperTheme.typography.caption1, color = foreground,
                            textAlign = TextAlign.Center, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Large, wrapping capsule label; disabled account options retain readable text and explicit status. */
@Composable
fun CapsuleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                  prominent: Boolean = false, enabled: Boolean = true, badge: String? = null) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    Column(modifier.fillMaxWidth().clip(EasyPaperTheme.shapes.capsule)
        .background(if (prominent) colors.tint else colors.fill)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { if (!enabled) disabled() }
        .heightIn(min = spacing.prominentButtonHeight).padding(horizontal = spacing.l, vertical = spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        val foreground = if (prominent) colors.onTint else colors.label
        Text(text, style = EasyPaperTheme.typography.headline, color = foreground, textAlign = TextAlign.Center)
        if (badge != null) Text(badge, style = EasyPaperTheme.typography.footnote, color = foreground)
    }
}

@Composable
fun RoundIconButton(icon: DrawableResource, label: String, onClick: () -> Unit,
                    modifier: Modifier = Modifier, large: Boolean = false, enabled: Boolean = true) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    Box(modifier.size(if (large) spacing.emptyAddSize else spacing.rowMinHeight)
        .clip(EasyPaperTheme.shapes.capsule).background(colors.cellRaised)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label; if (!enabled) disabled() }, contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(if (large) spacing.xxl else spacing.rowIcon),
            tint = if (enabled) colors.link else colors.secondaryLabel)
    }
}

/** A whole-page scroll keeps content and bottom actions reachable at 200% Dynamic Type. */
@Composable
fun OnboardingPage(
    icon: DrawableResource?,
    title: String,
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val type = EasyPaperTheme.typography
    BoxWithConstraints(
        Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        val viewportHeight = maxHeight
        Column(
            Modifier.widthIn(max = spacing.contentMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight).padding(horizontal = spacing.screenMargin, vertical = spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (icon != null) {
                    Icon(painterResource(icon), contentDescription = null, tint = colors.tint,
                        modifier = Modifier.size(ONBOARDING_ICON_SIZE))
                    Spacer(Modifier.height(spacing.xl))
                }
                Text(title, style = type.largeTitle, color = colors.label, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() })
                Spacer(Modifier.height(spacing.m))
                Text(body, style = type.body, color = colors.secondaryLabel, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
                Column(Modifier.fillMaxWidth().padding(top = spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(spacing.s), content = content)
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ProminentButton(primary, onPrimary, Modifier.fillMaxWidth())
                if (secondary != null) {
                    Spacer(Modifier.height(spacing.xs))
                    PlainButton(secondary, onSecondary)
                }
            }
        }
    }
}

private val SEGMENT_HEIGHT = 32.dp
private val SEGMENT_INSET = 2.dp
private val SEGMENT_SHADOW = 2.dp
private val SEARCH_STROKE = 2.dp
private val AVATAR_SIZE = 36.dp
internal const val TAB_LABEL_FONT_SCALE = 1.3f
private const val AVATAR_FONT_SCALE = 1.2f

/** Text inside fixed chrome (tab bar, avatar) grows with the system font only up to [max]. */
@Composable
internal fun CappedFontScale(max: Float, content: @Composable () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    if (density.fontScale <= max) content()
    else androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, max),
        content = content,
    )
}
private val ONBOARDING_ICON_SIZE = 88.dp
private const val RING_STROKE_FRACTION = 0.09f

/**
 * A big title that never breaks inside a word: when its longest word is wider than the line (a German compound
 * at 200 % text on a phone), the whole title is set smaller until that word fits, like iOS minimumScaleFactor.
 */
@Composable
fun WordFitText(text: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color,
                modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = constraints.maxWidth
        val longest = remember(text, style, measurer) {
            text.split(' ', '\n').filter { it.isNotEmpty() }
                .maxOfOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width } ?: 0
        }
        val fitted = if (constraints.hasBoundedWidth && longest > available) {
            val factor = available.toFloat() / longest * WORD_FIT_MARGIN
            style.copy(
                fontSize = style.fontSize * factor,
                lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * factor else style.lineHeight,
                letterSpacing = if (style.letterSpacing.isSpecified) style.letterSpacing * factor else style.letterSpacing,
            )
        } else style
        Text(text, style = fitted, color = color, textAlign = textAlign, modifier = modifier.fillMaxWidth())
    }
}

private const val WORD_FIT_MARGIN = 0.97f
