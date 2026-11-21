package org.easypaper.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// shortcut: the web app is the doctor's and talks to the server it was loaded from; keep settings for the tab only.
@Composable
actual fun rememberSavedSetting(key: String): SavedSetting = remember(key) { SavedSetting(null) {} }
