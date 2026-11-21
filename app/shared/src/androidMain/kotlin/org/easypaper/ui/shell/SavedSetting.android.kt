package org.easypaper.ui.shell

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberSavedSetting(key: String): SavedSetting {
    val prefs = LocalContext.current.applicationContext.getSharedPreferences("easypaper-settings", Context.MODE_PRIVATE)
    return remember(key) {
        SavedSetting(prefs.getString(key, null)) { value -> prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply() }
    }
}
