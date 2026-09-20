package dev.lightcopy.browser.reader

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderModeWebViewTest {
    private lateinit var webView: WebView

    @Before fun setUp() {
        val loaded = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView = WebView(ApplicationProvider.getApplicationContext()).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() { override fun onPageFinished(view: WebView, url: String) { loaded.countDown() } }
                loadDataWithBaseURL("https://fixture.test", """<html><head><title>Readable story</title><meta name="author" content="Ada"></head><body><nav>Menu</nav><article><h1>Heading</h1><p>This deliberately long article paragraph contains enough meaningful words for the bounded reader heuristic to select it as focused reading content without depending on any live internet page.</p></article></body></html>""", "text/html", "UTF-8", null)
            }
        }
        assertTrue(loaded.await(10, TimeUnit.SECONDS))
    }

    @After fun tearDown() = InstrumentationRegistry.getInstrumentation().runOnMainSync { webView.destroy() }

    @Test fun extractsArticleAndCanToggleDarkTransform() {
        val reader = evaluate(ReaderScripts.EXTRACT)
        val content = ReaderResultDecoder.decode(reader).content
        assertEquals("Readable story", content?.title)
        assertEquals("Ada", content?.byline)
        assertTrue(content?.text.orEmpty().contains("meaningful words"))
        assertFalse(content?.text.orEmpty().contains("Menu"))

        assertEquals("true", evaluate(ReaderScripts.ENABLE_DARK))
        assertEquals("\"lightcopy-page-dark\"", evaluate("document.getElementById('lightcopy-page-dark').id"))
        assertEquals("true", evaluate(ReaderScripts.DISABLE_DARK))
        assertEquals("null", evaluate("document.getElementById('lightcopy-page-dark')"))
    }

    private fun evaluate(script: String): String {
        val latch = CountDownLatch(1)
        var value: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { webView.evaluateJavascript(script) { value = it; latch.countDown() } }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        return checkNotNull(value)
    }
}
