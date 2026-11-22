package org.easypaper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import easypaper.app.shared.generated.resources.Res
import easypaper.app.shared.generated.resources.ic_chevron_left
import easypaper.app.shared.generated.resources.ic_chevron_right
import org.easypaper.ui.theme.EasyPaperTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** One tab of a [TabBar]. */
data class TabItem(val label: String, val icon: DrawableResource)

/** iOS tab bar: icon over a short label, the selected tab in tint, a hairline on top. */
@Composable
fun TabBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    Column(modifier.fillMaxWidth().background(colors.cell)) {
        HorizontalDivider(thickness = Dp.Hairline, color = colors.separator)
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).selectableGroup()) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selected
                val color = if (isSelected) colors.tint else colors.secondaryLabel
                Column(
                    Modifier.weight(1f).heightIn(min = spacing.rowMinHeight)
                        .selectable(
                            selected = isSelected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                        .padding(vertical = spacing.xxs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Decorative: the label below names the tab.
                    Icon(painterResource(item.icon), contentDescription = null, tint = color, modifier = Modifier.size(spacing.rowIcon))
                    Text(
                        item.label,
                        style = EasyPaperTheme.typography.caption1.copy(fontWeight = FontWeight.Medium),
                        color = color,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** "‹ Back" in the navigation bar of a pushed screen. */
data class BackAction(val label: String, val onClick: () -> Unit)

/**
 * A screen with an iOS large title (34 bold) that collapses into an inline 17 semibold title in the
 * bar when the list scrolls. [header] sits under the large title (date, context lines).
 * [onTitleLongPress] is the hidden entry to the developer menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    back: BackAction? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    onTitleLongPress: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    bottomInset: Boolean = true,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val type = EasyPaperTheme.typography
    var titleHeight by remember { mutableIntStateOf(1) }
    val collapsed by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset >= titleHeight }
    }
    val inlineAlpha by animateFloatAsState(
        if (collapsed) 1f else 0f,
        animationSpec = EasyPaperTheme.motion.fade(),
        label = "inline title",
    )

    Column(modifier.fillMaxSize().background(colors.background)) {
        // Navigation bar: back on the start, inline title in the middle once collapsed, actions on the end.
        Box(Modifier.fillMaxWidth().background(colors.cell.copy(alpha = inlineAlpha)).windowInsetsPadding(WindowInsets.statusBars)) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight).padding(horizontal = spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                if (back != null) {
                    BackButton(back, Modifier.align(Alignment.CenterStart))
                }
                Text(
                    title,
                    style = type.headline,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(inlineAlpha).padding(horizontal = spacing.xxl * 3),
                )
                if (actions != null) {
                    Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
            HorizontalDivider(Modifier.align(Alignment.BottomCenter).alpha(inlineAlpha), thickness = Dp.Hairline, color = colors.separator)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = spacing.contentMaxWidth).fillMaxWidth()
                    .then(if (bottomInset) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier),
                contentPadding = PaddingValues(start = spacing.screenMargin, end = spacing.screenMargin, bottom = spacing.xxl),
            ) {
                item(key = "large-title") {
                    Column(Modifier.fillMaxWidth().padding(bottom = spacing.l)) {
                        val longPress = if (onTitleLongPress != null) {
                            Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onLongClick = onTitleLongPress,
                                onClick = {},
                            )
                        } else Modifier
                        // Shrinks rather than breaking a long word ("Programme du médecin" in French at 200 %).
                        WordFitText(
                            title,
                            style = type.largeTitle,
                            color = colors.label,
                            modifier = longPress.onSizeChanged { titleHeight = it.height }.semantics { heading() },
                        )
                        header?.invoke(this)
                    }
                }
                content()
            }
        }
    }
}

/** Bar of a pushed screen that has no large title of its own (e.g. the design catalog). */
@Composable
fun NavigationBar(back: BackAction?, modifier: Modifier = Modifier, title: String? = null) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    Box(modifier.fillMaxWidth().background(colors.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight).padding(horizontal = spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            if (back != null) BackButton(back, Modifier.align(Alignment.CenterStart))
            if (title != null) {
                Text(title, style = EasyPaperTheme.typography.headline, color = colors.label, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = spacing.xxl * 3))
            }
        }
    }
}

@Composable
private fun BackButton(back: BackAction, modifier: Modifier = Modifier) {
    val colors = EasyPaperTheme.colors
    Row(
        modifier.minimumInteractiveComponentSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = back.onClick,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label says where back goes; the chevron is decoration.
        Icon(painterResource(Res.drawable.ic_chevron_left), contentDescription = null, tint = colors.link,
            modifier = Modifier.size(EasyPaperTheme.spacing.rowIcon + EasyPaperTheme.spacing.xxs))
        Text(back.label, style = EasyPaperTheme.typography.body, color = colors.link, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * iOS sheet over the current screen: scrim, rises from the bottom (a fade with reduced motion), a bar
 * with the title and a "Done" button. [grouped] = true puts grouped sections on `background`
 * like an iOS settings sheet; false uses `cellRaised`. Place it last inside a full-size Box.
 */
@Composable
fun Sheet(
    visible: Boolean,
    title: String,
    doneLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    grouped: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val motion = EasyPaperTheme.motion
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * SHEET_MAX_FRACTION
        AnimatedVisibility(visible, enter = fadeIn(motion.fade()), exit = fadeOut(motion.fade()), label = "sheet scrim") {
            Box(
                Modifier.fillMaxSize().testTag("sheet-scrim").background(MaterialTheme.colorScheme.scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = motion.sheetEnter(),
            exit = motion.sheetExit(),
            label = "sheet surface",
        ) {
            Column(
                Modifier.widthIn(max = spacing.contentMaxWidth).fillMaxWidth().heightIn(max = maxSheetHeight)
                    .testTag("sheet-surface")
                    .shadow(spacing.xs, EasyPaperTheme.shapes.sheet)
                    .clip(EasyPaperTheme.shapes.sheet)
                    .background(if (grouped) colors.background else colors.cellRaised)
                    // Taps inside the sheet must not reach the scrim.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            ) {
                SheetBar(title, doneLabel, onDismiss)
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = spacing.screenMargin)
                        .padding(top = spacing.xs, bottom = spacing.xxl)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    content = content,
                )
            }
        }
    }
}

/**
 * The sheet's bar: the title centered between equal insets as wide as the button, so they never overlap even
 * when "Done" grows with the font (200 % text); if the centered title would get less than [SHEET_TITLE_MIN] of
 * the bar, it starts at the leading edge and uses all the room the button leaves.
 */
@Composable
private fun SheetBar(title: String, doneLabel: String, onDismiss: () -> Unit) {
    val spacing = EasyPaperTheme.spacing
    androidx.compose.ui.layout.Layout(
        content = {
            // a long German word at 200 % shrinks rather than breaking ("Arzneimitt / el")
            WordFitText(title, style = EasyPaperTheme.typography.headline, color = EasyPaperTheme.colors.label,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            PlainButton(doneLabel, onDismiss)
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight).padding(horizontal = spacing.m),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val button = measurables[1].measure(loose)
        val inset = button.width + spacing.s.roundToPx()
        val centered = width - 2 * inset >= width * SHEET_TITLE_MIN
        val titleWidth = (if (centered) width - 2 * inset else width - inset).coerceAtLeast(0)
        val text = measurables[0].measure(loose.copy(maxWidth = titleWidth))
        val height = maxOf(constraints.minHeight, text.height, button.height)
        layout(width, height) {
            text.place(if (centered) (width - text.width) / 2 else 0, (height - text.height) / 2)
            button.place(width - button.width, (height - button.height) / 2)
        }
    }
}

private const val SHEET_TITLE_MIN = 0.4f

/** Doctor app sidebar (macOS style): a title, rows, an optional footer action; hairline on the end. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Sidebar(
    title: String,
    modifier: Modifier = Modifier,
    onTitleLongPress: (() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    Row(modifier.fillMaxHeight()) {
        Column(Modifier.width(spacing.sidebarWidth).fillMaxHeight().background(colors.cell).windowInsetsPadding(WindowInsets.statusBars)) {
            val longPress = if (onTitleLongPress != null) {
                Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onLongClick = onTitleLongPress,
                    onClick = {},
                )
            } else Modifier
            Text(
                title,
                style = EasyPaperTheme.typography.title2,
                color = colors.label,
                modifier = longPress.padding(start = spacing.m, end = spacing.m, top = spacing.l, bottom = spacing.s).semantics { heading() },
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = spacing.xs), content = content)
            if (footer != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = spacing.m, vertical = spacing.xs), content = footer)
            }
        }
        VerticalDivider(thickness = Dp.Hairline, color = colors.separator)
    }
}

/** A row of the [Sidebar]: the selected row is filled with tint, its text on tint. */
@Composable
fun SidebarRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EasyPaperTheme.colors
    val spacing = EasyPaperTheme.spacing
    val text = if (selected) colors.selectedRowLabel else colors.label
    val secondary = if (selected) colors.selectedRowLabel else colors.secondaryLabel
    Column(
        modifier.fillMaxWidth().heightIn(min = spacing.rowMinHeight)
            .clip(EasyPaperTheme.shapes.small)
            .background(if (selected) colors.selectedRowBackground else colors.cell)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = spacing.s, vertical = spacing.xs),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = EasyPaperTheme.typography.headline, color = text)
        if (subtitle != null) Text(subtitle, style = EasyPaperTheme.typography.footnote, color = secondary)
    }
}

/**
 * A plain row of a [GroupedSection]: title, optional secondary line, optional trailing element
 * (switch, value). [onClick] makes it tappable; [chevron] marks rows that open another screen
 * (HIG: actions such as "Clear data" have no chevron).
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = EasyPaperTheme.colors.label,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val spacing = EasyPaperTheme.spacing
    val colors = EasyPaperTheme.colors
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier.fillMaxWidth().then(clickable).heightIn(min = spacing.rowMinHeight)
            .padding(horizontal = spacing.m, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = EasyPaperTheme.typography.body, color = titleColor)
            if (subtitle != null) Text(subtitle, style = EasyPaperTheme.typography.footnote, color = colors.secondaryLabel)
        }
        if (trailing != null) {
            Spacer(Modifier.width(spacing.xs))
            trailing()
        }
        if (onClick != null && chevron) {
            Icon(
                painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.separator,
                modifier = Modifier.size(spacing.rowIcon),
            )
        }
    }
}

/** Empty state inside a grouped section: what is missing and what to do, in the interface's voice. */
@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val spacing = EasyPaperTheme.spacing
    GroupedSection(modifier) {
        Column(Modifier.fillMaxWidth().padding(spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(title, style = EasyPaperTheme.typography.headline, color = EasyPaperTheme.colors.label)
            Text(message, style = EasyPaperTheme.typography.body, color = EasyPaperTheme.colors.secondaryLabel)
            if (action != null) {
                Spacer(Modifier.height(spacing.xxs))
                action()
            }
        }
    }
}

private const val SHEET_MAX_FRACTION = 0.92f
