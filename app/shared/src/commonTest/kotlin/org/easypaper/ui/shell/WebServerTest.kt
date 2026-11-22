package org.easypaper.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals

class WebServerTest {
    @Test
    fun githubPagesUsesThePublicServerOtherPagesTheirOwn() {
        assertEquals(PUBLIC_SERVER, webServerFor("https://hackteamvldk.github.io"))
        assertEquals("https://74-148-56-1.sslip.io", webServerFor("https://74-148-56-1.sslip.io"))
        assertEquals("http://localhost:8080", webServerFor("http://localhost:8080"))
    }
}
