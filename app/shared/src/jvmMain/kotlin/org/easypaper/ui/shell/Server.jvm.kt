package org.easypaper.ui.shell

/** Desktop: -Deasypaper.server, else EASYPAPER_SERVER, else the server on this computer. */
actual fun defaultServerUrl(): String? =
    System.getProperty("easypaper.server") ?: System.getenv("EASYPAPER_SERVER") ?: "http://localhost:8080"
