package dev.lightcopy.browser.tabs

import org.junit.Assert.*
import org.junit.Test

class TabsTest {
    @Test fun `tabs are bounded and retain isolated metadata`() {
        val tabs = TabRegistry(capacity = 2)
        tabs.update("tab-1", title = "First", url = "https://one.test")
        val second = tabs.create(incognito = true)!!
        tabs.update(second.id, title = "Private", url = "https://private.test")

        assertNull(tabs.create(incognito = false))
        assertEquals("First", tabs.all()[0].title)
        assertEquals("https://private.test", tabs.active().url)
        assertTrue(tabs.active().incognito)
    }

    @Test fun `closing active selects a neighbor and last close creates a clean tab`() {
        val tabs = TabRegistry()
        val second = tabs.create(false)!!
        assertEquals(second, tabs.close(second.id))
        assertEquals("tab-1", tabs.activeId)
        tabs.close("tab-1")
        assertEquals(1, tabs.all().size)
        assertEquals("New tab", tabs.active().title)
    }

    @Test fun `incognito never reaches app history and mode boundaries require clearing`() {
        assertFalse(IncognitoPolicy.shouldRecordInAppHistory(true))
        assertTrue(IncognitoPolicy.shouldRecordInAppHistory(false))
        assertTrue(IncognitoPolicy.requiresSharedDataClear(false, true))
        assertTrue(IncognitoPolicy.requiresSharedDataClear(true, false))
        assertFalse(IncognitoPolicy.requiresSharedDataClear(true, true))
    }
}
