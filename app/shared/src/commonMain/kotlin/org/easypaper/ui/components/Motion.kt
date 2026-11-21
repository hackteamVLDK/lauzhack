package org.easypaper.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import org.easypaper.ui.theme.EasyPaperMotion
import org.easypaper.ui.theme.EasyPaperTheme
import org.easypaper.ui.theme.MotionTransition
import org.easypaper.ui.theme.tabular
import kotlin.math.roundToInt

/** The caller keeps locale-specific formatting, units and punctuation in [text]. */
@Composable
fun AnimatedNumber(
    value: Number,
    modifier: Modifier = Modifier,
    text: String = value.toString(),
    style: TextStyle = EasyPaperTheme.typography.title1,
    color: Color = EasyPaperTheme.colors.label,
) = AnimatedValueText(text, modifier, style, color, numericValue = value.toDouble())

@Immutable
private data class ValueText(val text: String, val number: Double?)

/** Numeric-text replacement: a small roll and fade after a change, never on first composition. */
@Composable
fun AnimatedValueText(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = EasyPaperTheme.typography.title1,
    color: Color = EasyPaperTheme.colors.label,
    numericValue: Double? = null,
) {
    val motion = EasyPaperTheme.motion
    AnimatedContent(
        targetState = ValueText(value, numericValue),
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(value) },
        contentAlignment = Alignment.CenterStart,
        transitionSpec = {
            motion.valueTransition(increasing = (targetState.number ?: 0.0) >= (initialState.number ?: 0.0))
        },
        label = "value text",
    ) { shown ->
        Text(shown.text, style = style.tabular(), color = color)
    }
}

fun EasyPaperMotion.valueTransition(increasing: Boolean): ContentTransform {
    val direction = if (increasing) 1 else -1
    val enter = fadeIn(fade())
    val exit = fadeOut(fade())
    val reduced = transition == MotionTransition.Crossfade
    return ContentTransform(
        targetContentEnter = if (reduced) enter else enter + slideInVertically(snappy.spec()) { direction * it / 4 },
        initialContentExit = if (reduced) exit else exit + slideOutVertically(snappy.spec()) { -direction * it / 4 },
        // No animated size changes, especially when Reduce Motion requests only opacity.
        sizeTransform = SizeTransform(sizeAnimationSpec = { _, _ -> snap() }),
    )
}

/** An interruptible disclosure; its content remains composed until the exit completes. */
@Composable
fun MotionDisclosure(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val motion = EasyPaperTheme.motion
    AnimatedVisibility(expanded, modifier, enter = motion.disclosureEnter(), exit = motion.disclosureExit(), content = content)
}

fun EasyPaperMotion.disclosureEnter(): EnterTransition =
    if (transition == MotionTransition.Crossfade) fadeIn(fade())
    else expandVertically(default.spec(), expandFrom = Alignment.Top) + fadeIn(fade())

fun EasyPaperMotion.disclosureExit(): ExitTransition =
    if (transition == MotionTransition.Crossfade) fadeOut(fade())
    else shrinkVertically(default.spec(), shrinkTowards = Alignment.Top) + fadeOut(fade())

fun EasyPaperMotion.sheetEnter(): EnterTransition =
    if (transition == MotionTransition.Crossfade) fadeIn(fade())
    else slideInVertically(default.spec()) { it }

fun EasyPaperMotion.sheetExit(): ExitTransition =
    if (transition == MotionTransition.Crossfade) fadeOut(fade())
    else slideOutVertically(default.spec()) { it }

/** Push right-to-left, or the inverse for Back. The previous page travels 30% of its width. */
fun EasyPaperMotion.pushTransition(forward: Boolean): ContentTransform {
    if (transition == MotionTransition.Crossfade) {
        return ContentTransform(
            fadeIn(fade()), fadeOut(fade()),
            sizeTransform = SizeTransform(sizeAnimationSpec = { _, _ -> snap() }),
        )
    }
    return ContentTransform(
        targetContentEnter = slideInHorizontally(default.spec()) { width ->
            if (forward) width else -(width * PREVIOUS_PAGE_TRAVEL).roundToInt()
        },
        initialContentExit = slideOutHorizontally(default.spec()) { width ->
            if (forward) -(width * PREVIOUS_PAGE_TRAVEL).roundToInt() else width
        },
        targetContentZIndex = if (forward) 1f else -1f,
        sizeTransform = SizeTransform(sizeAnimationSpec = { _, _ -> snap() }),
    )
}

/** Animate stack changes, keeping tab switches separate by keying this host to the selected tab. */
@Composable
fun <S : Any> PushNavigation(
    stack: List<S>,
    modifier: Modifier = Modifier,
    content: @Composable (S) -> Unit,
) {
    val motion = EasyPaperTheme.motion
    val background = EasyPaperTheme.colors.background
    AnimatedContent(
        targetState = stack,
        modifier = modifier,
        contentKey = { it.last() },
        transitionSpec = { motion.pushTransition(forward = targetState.size >= initialState.size) },
        label = "navigation push",
    ) { shown ->
        Box(Modifier.fillMaxSize().background(background)) { content(shown.last()) }
    }
}

private const val PREVIOUS_PAGE_TRAVEL = 0.3f
