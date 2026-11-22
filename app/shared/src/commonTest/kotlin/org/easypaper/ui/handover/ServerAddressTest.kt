package org.easypaper.ui.handover

import org.easypaper.ui.shell.PUBLIC_SERVER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Task 63: the apps use the team's public server unless a local one is set; only local ones need Android 17's permission. */
class ServerAddressTest {
    @Test
    fun theRelayIsThePublicServerUnlessSomethingIsSet() {
        assertEquals(PUBLIC_SERVER, relayUrl(null, null))
        assertEquals("http://10.0.2.2:8090", relayUrl("http://10.0.2.2:8090/", null))
        assertEquals("http://localhost:8080", relayUrl(null, "http://localhost:8080"))
    }

    @Test
    fun onlyLocalAddressesNeedTheLocalNetworkPermission() {
        assertFalse(isLocalNetworkUrl(PUBLIC_SERVER))
        assertFalse(isLocalNetworkUrl("https://example.org:443/x"))
        assertFalse(isLocalNetworkUrl("http://172.32.0.1:8090"))
        assertTrue(isLocalNetworkUrl("http://10.0.2.2:8090"))
        assertTrue(isLocalNetworkUrl("http://192.168.1.20:8090/"))
        assertTrue(isLocalNetworkUrl("http://172.20.3.4"))
        assertTrue(isLocalNetworkUrl("http://localhost:8080"))
        assertTrue(isLocalNetworkUrl("http://laptop.local:8090"))
        assertTrue(isLocalNetworkUrl("http://[::1]:8090"))
    }
}
