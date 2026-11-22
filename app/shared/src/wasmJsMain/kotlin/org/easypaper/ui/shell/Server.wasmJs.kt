package org.easypaper.ui.shell

@OptIn(ExperimentalWasmJsInterop::class)
private fun pageOrigin(): String = js("window.location.origin")

/** The page's own server, except on a static host (GitHub Pages) that serves only the app: then the public server. */
actual fun defaultServerUrl(): String? = webServerFor(pageOrigin())
