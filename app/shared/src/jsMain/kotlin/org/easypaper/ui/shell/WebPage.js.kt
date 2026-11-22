package org.easypaper.ui.shell

actual fun webPageSearch(): String = js("window.location.search") as String
