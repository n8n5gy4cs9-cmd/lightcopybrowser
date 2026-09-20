package dev.lightcopy.browser.copy

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import dev.lightcopy.browser.inspect.ElementInspectionScripts
import dev.lightcopy.browser.inspect.InspectionResult
import dev.lightcopy.browser.inspect.InspectionResultDecoder

@RunWith(AndroidJUnit4::class)
class PageExtractionWebViewTest {
    private lateinit var server: FixtureServer
    private lateinit var webView: WebView

    @Before fun setUp() {
        server = FixtureServer()
        val loaded = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView = WebView(ApplicationProvider.getApplicationContext()).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (url == server.url) loaded.countDown()
                    }
                }
                loadUrl(server.url)
            }
        }
        assertTrue("fixture page did not load", loaded.await(10, TimeUnit.SECONDS))
    }

    @After fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { webView.destroy() }
        server.close()
    }

    @Test fun originalRenderedAndVisibleTextRemainDistinct() {
        val original = evaluate(ExtractionKind.OriginalHtml)
        val rendered = evaluate(ExtractionKind.RenderedDom)
        val text = evaluate(ExtractionKind.VisibleText)

        assertTrue("original=$original", original.content.orEmpty().contains("server-original"))
        assertFalse(original.content.orEmpty().contains("rendered-mutation"))
        assertTrue(rendered.content.orEmpty().contains("rendered-mutation"))
        assertTrue(text.content.orEmpty().contains("Visible heading"))
        assertFalse(text.content.orEmpty().contains("hidden words"))
        assertFalse(text.content.orEmpty().contains("<h1"))
    }

    @Test fun advancedFormatsAndSelectedHtmlAreExtractedFromLocalPage() {
        assertTrue(evaluate(ExtractionKind.Url).content.orEmpty().endsWith("/fixture.html"))
        assertTrue(evaluate(ExtractionKind.Title).content == "Fixture title")
        assertTrue(evaluate(ExtractionKind.Links).content.orEmpty().contains("/docs"))
        assertTrue(evaluate(ExtractionKind.Images).content.orEmpty().contains("/pixel.png"))
        val markdown = evaluate(ExtractionKind.Markdown).content.orEmpty()
        assertTrue(markdown.contains("# Visible heading"))
        assertTrue(markdown.contains("[Docs]"))

        // This headless fixture WebView is not attached for layout; make hit-testing deterministic.
        evaluateRaw("document.elementFromPoint=function(){return document.getElementById('target')}")
        val inspection = inspect(10f, 10f)
        assertTrue("inspection=$inspection", inspection.inspection?.selector == "#target")
        evaluateRaw("document.querySelector(window.__lightcopySelectedSelector).setAttribute('data-new','yes')")
        val selected = evaluate(ExtractionKind.SelectedHtml)
        assertTrue(selected.content.orEmpty().contains("data-new=\"yes\""))
    }

    private fun evaluate(kind: ExtractionKind): ExtractionResult {
        val latch = CountDownLatch(1)
        var result: ExtractionResult? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(PageExtractionScripts.forKind(kind)) {
                result = ExtractionResultDecoder.decode(kind, it)
                latch.countDown()
            }
        }
        assertTrue("extraction timed out", latch.await(10, TimeUnit.SECONDS))
        return checkNotNull(result)
    }

    private fun inspect(x: Float, y: Float): InspectionResult {
        val latch = CountDownLatch(1)
        var result: InspectionResult? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(ElementInspectionScripts.selectAt(x, y)) {
                result = InspectionResultDecoder.decode(it); latch.countDown()
            }
        }
        assertTrue("inspection timed out", latch.await(10, TimeUnit.SECONDS))
        return checkNotNull(result)
    }

    private fun evaluateRaw(script: String) {
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(script) { latch.countDown() }
        }
        assertTrue("script timed out", latch.await(10, TimeUnit.SECONDS))
    }

    private class FixtureServer : AutoCloseable {
        private val socket = ServerSocket(0)
        private val thread = Thread {
            while (!socket.isClosed) runCatching {
                socket.accept().use { client ->
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    val html = """<!doctype html><html><head><title>Fixture title</title><style>body{margin:0}#target{width:100px;height:30px}</style></head><body data-source="server-original"><div id="target" class="card featured">before</div><h1>Visible heading</h1><a href="/docs">Docs</a><img src="/pixel.png" alt="Pixel"><p style="display:none">hidden words</p><script>document.getElementById('target').textContent='rendered-' + 'mutation';</script></body></html>"""
                    val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.toByteArray().size}\r\nConnection: close\r\n\r\n$html"
                    client.getOutputStream().write(response.toByteArray())
                }
            }
        }.apply { isDaemon = true; start() }

        val url = "http://127.0.0.1:${socket.localPort}/fixture.html"
        override fun close() = socket.close()
    }
}
