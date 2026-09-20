package dev.lightcopy.browser.fullscreen

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class FullscreenPreferencesStoreTest {
    @Test fun presetAndFreeDragSettingsSurviveStoreRecreation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("fullscreen_preferences", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val expected = FullscreenPreferences(
            position = ExitControlPosition.FreeDrag,
            freeX = .17f,
            freeY = .63f,
            sizeDp = 72,
            opacity = .55f,
            autoHideMillis = 5_000,
        )

        LocalFullscreenPreferencesStore(context).save(expected)

        assertEquals(expected, LocalFullscreenPreferencesStore(context).load())
    }
}
