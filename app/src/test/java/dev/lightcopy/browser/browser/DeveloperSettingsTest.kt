package dev.lightcopy.browser.browser

import org.junit.Assert.*
import org.junit.Test

class DeveloperSettingsTest {
    @Test fun `custom user agent rejects blank overlong and control characters`() {
        assertNotNull(UserAgentValidator.error(" "))
        assertNotNull(UserAgentValidator.error("x".repeat(513)))
        assertNotNull(UserAgentValidator.error("Agent\nInjected"))
        assertNull(UserAgentValidator.error("LightCopy/1.0 Android"))
    }
}
