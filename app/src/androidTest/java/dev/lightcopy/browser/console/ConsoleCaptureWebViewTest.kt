package dev.lightcopy.browser.console

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsoleCaptureWebViewTest {
    private lateinit var webView: WebView

    @After fun tearDown() {
        if (::webView.isInitialized) InstrumentationRegistry.getInstrumentation().runOnMainSync { webView.destroy() }
    }

    @Test fun capturesLevelsMessagesSourcesAndLinesFromJavascript() {
        val messages = mutableListOf<ConsoleMessage>()
        val latch = CountDownLatch(3)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView = WebView(ApplicationProvider.getApplicationContext()).apply {
                settings.javaScriptEnabled = true
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        synchronized(messages) { messages += message }
                        latch.countDown()
                        return true
                    }
                }
                loadDataWithBaseURL(
                    "https://fixture.local/console.html",
                    "<script>console.log('alpha');\nconsole.warn('beta');\nconsole.error('gamma');</script>",
                    "text/html", "UTF-8", null,
                )
            }
        }

        assertTrue("console messages timed out", latch.await(10, TimeUnit.SECONDS))
        val captured = synchronized(messages) { messages.toList() }
        assertEquals(listOf("alpha", "beta", "gamma"), captured.take(3).map { it.message() })
        assertEquals(
            listOf(ConsoleLevel.LOG, ConsoleLevel.WARNING, ConsoleLevel.ERROR),
            captured.take(3).map { it.toConsoleLevel() },
        )
        assertTrue(captured.take(3).all { it.sourceId().contains("fixture.local") && it.lineNumber() > 0 })
    }
}
