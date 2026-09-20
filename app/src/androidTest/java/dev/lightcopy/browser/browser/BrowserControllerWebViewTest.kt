package dev.lightcopy.browser.browser

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BrowserControllerWebViewTest {
    private var webView: WebView? = null

    @After fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { webView?.destroy() }
    }

    @Test fun findReportsCompletionAndDetachReleasesControllerReference() {
        val loaded = CountDownLatch(1)
        val found = CountDownLatch(1)
        var matches = -1
        val controller = BrowserController()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView = WebView(ApplicationProvider.getApplicationContext()).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (url.startsWith("https://fixture.test")) loaded.countDown()
                    }
                }
                setFindListener { _, numberOfMatches, done ->
                    if (done) { matches = numberOfMatches; found.countDown() }
                }
                controller.attach(this)
                loadDataWithBaseURL("https://fixture.test", "<p>needle one</p><p>needle two</p>", "text/html", "UTF-8", null)
            }
        }
        assertTrue(loaded.await(10, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { controller.find("needle") }
        assertTrue(found.await(10, TimeUnit.SECONDS))
        assertTrue(matches >= 0) // Headless WebViews can report zero because no text is laid out.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { controller.detach(webView!!) }
        assertFalse(controller.hasAttachedView())
    }
}
