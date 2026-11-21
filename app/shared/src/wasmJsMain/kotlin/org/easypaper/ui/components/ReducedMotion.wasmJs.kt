package org.easypaper.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@OptIn(ExperimentalWasmJsInterop::class)
private fun prefersReducedMotion(): Boolean = js("window.matchMedia('(prefers-reduced-motion: reduce)').matches")

@Composable
actual fun reducedMotion(): Boolean = remember { prefersReducedMotion() }
