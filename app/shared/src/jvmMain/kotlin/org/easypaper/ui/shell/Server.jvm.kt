package org.easypaper.ui.shell

/** Desktop: -Deasypaper.server, else EASYPAPER_SERVER, else the team's public server (a local one: http://localhost:8080). */
actual fun defaultServerUrl(): String? =
    System.getProperty("easypaper.server") ?: System.getenv("EASYPAPER_SERVER") ?: PUBLIC_SERVER
