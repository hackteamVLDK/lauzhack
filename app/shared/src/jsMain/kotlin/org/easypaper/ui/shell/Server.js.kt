package org.easypaper.ui.shell

/** The page's own server, except on a static host (GitHub Pages) that serves only the app: then the public server. */
actual fun defaultServerUrl(): String? = webServerFor(js("window.location.origin") as String)
