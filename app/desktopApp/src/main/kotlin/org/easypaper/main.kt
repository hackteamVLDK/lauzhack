package org.easypaper

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.easypaper.ui.shell.EasyPaperApp
import org.easypaper.ui.shell.Role
import org.easypaper.ui.shell.ShellSettings
import java.util.Locale

/**
 * Desktop: both roles. For screenshots and demos the start can be fixed with system properties:
 * -Deasypaper.role=patient|doctor, -Deasypaper.dark=true|false, -Deasypaper.largeText=true,
 * -Deasypaper.demo=true, -Deasypaper.width=… / height=… (dp), -Deasypaper.lang=de|uk|en (Gradle's run
 * task passes its own -Duser.language, so the language has its own property).
 */
fun main() {
    System.getProperty("easypaper.lang")?.let { Locale.setDefault(Locale.forLanguageTag(it)) }
    desktopApp()
}

private fun desktopApp() = application {
    val role = when (System.getProperty("easypaper.role")) {
        "patient" -> Role.Patient
        "doctor" -> Role.Doctor
        else -> null
    }
    val settings = ShellSettings(
        dark = System.getProperty("easypaper.dark")?.toBooleanStrictOrNull(),
        largeText = System.getProperty("easypaper.largeText") == "true",
    )
    val width = System.getProperty("easypaper.width")?.toIntOrNull() ?: 1100
    val height = System.getProperty("easypaper.height")?.toIntOrNull() ?: 760
    Window(
        onCloseRequest = ::exitApplication,
        title = "Easy Paper",
        state = rememberWindowState(width = width.dp, height = height.dp),
    ) {
        if (role == null) App() else EasyPaperApp(role = role, settings = settings, demo = System.getProperty("easypaper.demo") == "true")
    }
}
