package org.easypaper

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import org.easypaper.ui.catalog.DesignCatalog
import org.easypaper.ui.theme.EasyPaperTheme

@Composable
@Preview
fun App() {
    val system = isSystemInDarkTheme()
    var dark by remember(system) { mutableStateOf(system) }
    EasyPaperTheme(darkTheme = dark) {
        DesignCatalog(dark = dark, onDarkChange = { dark = it })
    }
}
