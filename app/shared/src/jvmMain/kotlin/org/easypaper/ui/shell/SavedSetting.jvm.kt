package org.easypaper.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.prefs.Preferences

@Composable
actual fun rememberSavedSetting(key: String): SavedSetting = remember(key) {
    val prefs = Preferences.userRoot().node("org/easypaper")
    SavedSetting(prefs.get(key, null)) { value -> if (value == null) prefs.remove(key) else prefs.put(key, value) }
}
