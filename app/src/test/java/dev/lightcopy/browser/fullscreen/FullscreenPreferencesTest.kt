package dev.lightcopy.browser.fullscreen

import org.junit.Assert.assertEquals
import org.junit.Test

class FullscreenPreferencesTest {
    @Test fun exposesExactlySevenPositionModes() {
        assertEquals(7, ExitControlPosition.entries.size)
        assertEquals(ExitControlPosition.BottomMiddle, FullscreenPreferences().position)
        assertEquals(ExitControlPosition.FreeDrag, ExitControlPosition.entries.last())
    }

    @Test fun sanitizesValuesAndAllowsCompactExit() {
        val value = FullscreenPreferences(
            freeX = -2f,
            freeY = 4f,
            sizeDp = 20,
            opacity = .1f,
            autoHideMillis = 99_000,
        ).sanitized()

        assertEquals(0f, value.freeX)
        assertEquals(1f, value.freeY)
        assertEquals(20, value.sizeDp)
        assertEquals(.35f, value.opacity)
        assertEquals(10_000, value.autoHideMillis)
    }
}
