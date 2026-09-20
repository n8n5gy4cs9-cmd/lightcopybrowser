package dev.lightcopy.browser.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearOnExitPolicyTest {
    @Test fun `nothing selected means no clearing`() {
        assertFalse(ClearOnExitPolicy.hasSelectedData(ClearOnExit()))
    }

    @Test fun `any selected category triggers clearing`() {
        assertTrue(ClearOnExitPolicy.hasSelectedData(ClearOnExit(history = true)))
        assertTrue(ClearOnExitPolicy.hasSelectedData(ClearOnExit(bookmarks = true)))
        assertTrue(ClearOnExitPolicy.hasSelectedData(ClearOnExit(webData = true)))
        assertTrue(ClearOnExitPolicy.hasSelectedData(ClearOnExit(history = true, bookmarks = true, webData = true)))
    }
}
