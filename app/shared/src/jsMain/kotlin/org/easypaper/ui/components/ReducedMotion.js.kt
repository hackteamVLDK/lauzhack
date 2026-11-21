package org.easypaper.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun reducedMotion(): Boolean =
    remember { js("window.matchMedia('(prefers-reduced-motion: reduce)').matches") as Boolean }
