package org.easypaper.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** A background highlight only while pressed; hover and focus never create a state layer. */
data class PressIndication(val fill: Color, val fadeDurationMillis: Int) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressIndicationNode(interactionSource, fill, fadeDurationMillis)
}

private class PressIndicationNode(
    private val interactionSource: InteractionSource,
    private val fill: Color,
    private val fadeDurationMillis: Int,
) : Modifier.Node(), DrawModifierNode {
    private val opacity = Animatable(0f)
    private val presses = mutableSetOf<PressInteraction.Press>()

    override fun onAttach() {
        coroutineScope.launch {
            opacity.snapTo(0f)
            var animation: Job? = null
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses.add(interaction)
                    is PressInteraction.Release -> presses.remove(interaction.press)
                    is PressInteraction.Cancel -> presses.remove(interaction.press)
                    else -> return@collect
                }
                animation?.cancel()
                if (presses.isNotEmpty()) {
                    opacity.snapTo(1f)
                } else {
                    animation = launch { opacity.animateTo(0f, tween(fadeDurationMillis)) }
                }
            }
        }
    }

    override fun onDetach() {
        presses.clear()
    }

    override fun ContentDrawScope.draw() {
        // The control's container is outside the indication; its label stays above the highlight.
        drawRect(fill, alpha = opacity.value)
        drawContent()
    }
}
