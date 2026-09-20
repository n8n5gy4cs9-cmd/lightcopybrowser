package dev.lightcopy.browser.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsSanitizationTest {
    @Test fun `font size is clamped into range`() {
        assertEquals(5, AppSettings(sourceFontSizeSp = 4).sanitized().sourceFontSizeSp)
        assertEquals(24, AppSettings(sourceFontSizeSp = 99).sanitized().sourceFontSizeSp)
    }

    @Test fun `unknown console level falls back to all`() {
        assertEquals("ALL", AppSettings(consoleLevel = "VERBOSE").sanitized().consoleLevel)
    }

    @Test fun `user agents are trimmed validated deduplicated and bounded`() {
        val agents = listOf("  Agent/1  ", "Agent/1", "bad\nagent", "") + (1..20).map { "Agent/$it" }
        val result = AppSettings(userAgents = agents).sanitized().userAgents
        assertTrue(result.size <= AppSettings.MAX_USER_AGENTS)
        assertEquals(result.distinct(), result)
        assertTrue(result.all { UserAgentEntryValidator.error(it) == null })
    }

    @Test fun `injections are truncated to the limit`() {
        assertEquals(
            AppSettings.MAX_INJECTION_LENGTH,
            AppSettings(cssInjection = "x".repeat(AppSettings.MAX_INJECTION_LENGTH + 50)).sanitized().cssInjection.length,
        )
        assertEquals(
            AppSettings.MAX_INJECTION_LENGTH,
            AppSettings(javaScriptInjection = "y".repeat(AppSettings.MAX_INJECTION_LENGTH + 1)).sanitized().javaScriptInjection.length,
        )
    }
    @Test fun customizationAllowsSmallSizesAndRetainsValidColors() {
        val safe = AppSettings(addressHeightDp = 5, addressFontSp = 5, actionSizeDp = 5,
            consolePercent = 100, consoleFontSp = 0, primaryHex = "invalid", consoleErrorHex = "#red").sanitized()
        assertEquals(5, safe.addressHeightDp)
        assertEquals(5, safe.addressFontSp)
        assertEquals(5, safe.actionSizeDp)
        assertEquals(95, safe.consolePercent)
        assertEquals(5, safe.consoleFontSp)
        assertEquals("5DD6FF", safe.primaryHex)
        assertEquals("FF7B8B", safe.consoleErrorHex)
        assertEquals(10, themePresets.size)
        assertTrue(themePresets.all { validHex(it.primary) && validHex(it.secondary) })
    }
}
