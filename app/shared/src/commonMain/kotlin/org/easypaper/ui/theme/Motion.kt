package org.easypaper.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import kotlin.math.PI

/** Spatial transitions are replaced by crossfades when the system requests less motion. */
enum class MotionTransition { Spatial, Crossfade }

@Immutable
data class EasyPaperSpring(val stiffness: Float, val dampingRatio: Float) {
    fun <T> spec(): SpringSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)
}

/** SwiftUI's response is a period in seconds: k = (2π / response)² for unit mass. */
@Immutable
data class EasyPaperMotion(val reduceMotion: Boolean = false) {
    val responseSeconds: Float = 0.5f
    val stiffness: Float = ((2 * PI / responseSeconds) * (2 * PI / responseSeconds)).toFloat() *
        if (reduceMotion) 4f else 1f
    val default: EasyPaperSpring = preset(0.825f)
    val smooth: EasyPaperSpring = preset(1f)
    val snappy: EasyPaperSpring = preset(0.85f)
    val bouncy: EasyPaperSpring = preset(0.7f)
    val fadeDurationMillis: Int = 200
    val pressFadeDurationMillis: Int = 150
    val transition: MotionTransition = if (reduceMotion) MotionTransition.Crossfade else MotionTransition.Spatial

    fun <T> fade(): TweenSpec<T> = tween(durationMillis = fadeDurationMillis)

    private fun preset(damping: Float) = EasyPaperSpring(stiffness, if (reduceMotion) 1f else damping)
}
