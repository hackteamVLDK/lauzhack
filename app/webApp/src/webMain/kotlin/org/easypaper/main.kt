package org.easypaper

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.easypaper.ui.shell.EasyPaperApp
import org.easypaper.ui.shell.webRole

/** The doctor's app by default; `?patient` opens the patient's diary (see [webRole]). */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val role = webRole()
    ComposeViewport {
        EasyPaperApp(role = role)
    }
}
