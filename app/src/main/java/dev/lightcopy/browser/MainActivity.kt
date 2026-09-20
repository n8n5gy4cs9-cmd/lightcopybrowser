package dev.lightcopy.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.lightcopy.browser.ui.BrowserApp
import dev.lightcopy.browser.browser.BrowserController
import dev.lightcopy.browser.settings.ClearOnExitPolicy
import dev.lightcopy.browser.settings.ExitDataClearer
import dev.lightcopy.browser.settings.LocalAppSettingsStore

class MainActivity : ComponentActivity() {
    private val browserController = BrowserController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrowserApp(
                browserController,
                savedInstanceState?.getBundle(WEB_VIEW_STATE),
                onFullscreenChanged = ::setImmersiveFullscreen,
                onKeepScreenOnChanged = ::setKeepScreenOn,
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val webViewState = Bundle()
        browserController.saveState(webViewState)
        outState.putBundle(WEB_VIEW_STATE, webViewState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing) {
            val clearOnExit = LocalAppSettingsStore(applicationContext).load().clearOnExit
            if (ClearOnExitPolicy.hasSelectedData(clearOnExit)) {
                ExitDataClearer(applicationContext).clear(clearOnExit, browserController::clearSharedBrowsingData)
            }
        }
        super.onDestroy()
    }

    private fun setImmersiveFullscreen(enabled: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enabled) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setKeepScreenOn(enabled: Boolean) {
        if (enabled) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private companion object { const val WEB_VIEW_STATE = "web_view_state" }
}
