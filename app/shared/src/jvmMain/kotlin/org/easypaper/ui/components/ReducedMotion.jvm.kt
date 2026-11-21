package org.easypaper.ui.components

import androidx.compose.runtime.Composable

/** Desktop has no common setting for this; `-Deasypaper.reducedMotion=true` forces it. */
@Composable
actual fun reducedMotion(): Boolean = System.getProperty("easypaper.reducedMotion") == "true"
