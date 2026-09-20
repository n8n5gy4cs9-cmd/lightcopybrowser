package dev.lightcopy.browser.network

import org.junit.Assert.*
import org.junit.Test

class NetworkLogTest {
    @Test fun `logs are bounded and isolated by tab`() {
        val logs = TabNetworkLogs(2, NetworkClock { 7 })
        logs.record("a", "https://one.test", "GET")
        logs.record("a", "https://two.test", "POST")
        logs.record("a", "https://three.test", "GET")
        logs.record("b", "https://other.test", "GET")
        assertEquals(listOf("https://two.test", "https://three.test"), logs.entries("a").map { it.url })
        assertEquals(1, logs.entries("b").size)
    }

    @Test fun `observable status updates latest matching request`() {
        val logs = TabNetworkLogs()
        logs.record("a", "https://x.test/a", "GET")
        logs.observeStatus("a", "https://x.test/a", "GET", 404)
        assertEquals(404, logs.entries("a").single().status)
        val blocked = logs.record("a", "https://doubleclick.net/ad", "GET", blocked = true)
        assertTrue(blocked.blocked)
        assertEquals(204, blocked.status)
    }

    @Test fun `ad blocking matches host boundaries`() {
        assertTrue(AdBlockPolicy.shouldBlock("https://pagead.doubleclick.net/a"))
        assertFalse(AdBlockPolicy.shouldBlock("https://notdoubleclick.net/a"))
        assertFalse(AdBlockPolicy.shouldBlock("not a url"))
    }
}
