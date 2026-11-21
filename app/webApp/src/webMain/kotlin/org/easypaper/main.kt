package org.easypaper

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.easypaper.ui.shell.EasyPaperApp
import org.easypaper.ui.shell.Role

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        EasyPaperApp(role = Role.Doctor)
    }
}