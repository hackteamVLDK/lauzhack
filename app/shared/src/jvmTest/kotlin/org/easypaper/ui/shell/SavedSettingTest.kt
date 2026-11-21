package org.easypaper.ui.shell

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The server address typed in the developer menu is there on the next launch (task 30: real phones, other laptops). */
class SavedSettingTest {
    private fun launch(key: String): SavedSetting {
        var setting: SavedSetting? = null
        ImageComposeScene(10, 10, Density(1f)) { setting = rememberSavedSetting(key) }.apply { render(); close() }
        return setting!!
    }

    @Test
    fun aSavedValueSurvivesARestartAndCanBeCleared() {
        val key = "test-" + System.nanoTime()
        launch(key).update("http://192.168.1.20:8080")
        assertEquals("http://192.168.1.20:8080", launch(key).value)
        launch(key).update(null)
        assertNull(launch(key).value)
    }
}
