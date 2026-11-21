package org.easypaper.ui.components

import androidx.compose.runtime.Composable

/**
 * True when the person asked the system for less motion (Android: animations off; web:
 * prefers-reduced-motion). Movement then becomes a short fade (docs/design.md, Motion).
 */
@Composable
expect fun reducedMotion(): Boolean
