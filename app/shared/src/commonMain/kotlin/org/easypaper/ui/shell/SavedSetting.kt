package org.easypaper.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A small setting kept on this device between launches (developer menu: the server address); null removes it. */
class SavedSetting(initial: String?, private val save: (String?) -> Unit) {
    var value: String? by mutableStateOf(initial)
        private set

    fun update(new: String?) {
        value = new
        save(new)
    }
}

@Composable
expect fun rememberSavedSetting(key: String): SavedSetting
