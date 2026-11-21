package org.easypaper.ui.shell

actual fun defaultServerUrl(): String? = js("window.location.origin") as String
