package dev.lightcopy.browser.browser

import android.os.Bundle
import android.webkit.WebView
import android.webkit.CookieManager
import android.webkit.WebStorage
import dev.lightcopy.browser.copy.ExtractionKind
import dev.lightcopy.browser.copy.ExtractionResult
import dev.lightcopy.browser.copy.ExtractionResultDecoder
import dev.lightcopy.browser.copy.PageExtractionScripts
import dev.lightcopy.browser.inspect.ElementInspectionScripts
import dev.lightcopy.browser.inspect.InspectionResult
import dev.lightcopy.browser.inspect.InspectionResultDecoder
import dev.lightcopy.browser.pageinfo.*
import dev.lightcopy.browser.capture.CaptureLimits
import dev.lightcopy.browser.capture.CaptureResult
import dev.lightcopy.browser.reader.ReaderResult
import dev.lightcopy.browser.reader.ReaderResultDecoder
import dev.lightcopy.browser.reader.ReaderScripts
import android.graphics.Bitmap
import android.graphics.Canvas
import java.net.URI

class BrowserController {
    private var webView: WebView? = null

    fun attach(view: WebView) { webView = view }
    fun detach(view: WebView) { if (webView === view) webView = null }
    internal fun hasAttachedView(): Boolean = webView != null
    fun load(url: String, doNotTrack: Boolean = false) {
        if (doNotTrack) webView?.loadUrl(url, mapOf("DNT" to "1")) else webView?.loadUrl(url)
    }
    fun back() { webView?.takeIf(WebView::canGoBack)?.goBack() }
    fun forward() { webView?.takeIf(WebView::canGoForward)?.goForward() }
    fun stop() { webView?.stopLoading() }
    fun reload() { webView?.reload() }
    fun find(query: String) { if (query.isBlank()) clearFind() else webView?.findAllAsync(query) }
    fun findNext(forward: Boolean) { webView?.findNext(forward) }
    fun clearFind() { webView?.clearMatches() }
    fun reader(onResult: (ReaderResult) -> Unit) {
        val view = webView ?: return onResult(ReaderResult(error = "No page is available."))
        view.evaluateJavascript(ReaderScripts.EXTRACT) { onResult(ReaderResultDecoder.decode(it)) }
    }
    fun setPageDark(enabled: Boolean) {
        webView?.evaluateJavascript(if (enabled) ReaderScripts.ENABLE_DARK else ReaderScripts.DISABLE_DARK, null)
    }
    @Suppress("DEPRECATION")
    fun captureFullPage(onResult: (CaptureResult) -> Unit) {
        val view = webView ?: return onResult(CaptureResult(error = "No page is available."))
        view.post {
            runCatching {
                val picture = view.capturePicture()
                val width = picture.width
                val height = picture.height
                CaptureLimits.validate(width, height)?.let { error -> return@post onResult(CaptureResult(error = error)) }
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                picture.draw(Canvas(bitmap))
                onResult(CaptureResult(bitmap = bitmap))
            }.onFailure { onResult(CaptureResult(error = "Full-page capture failed.")) }
        }
    }
    fun clearSharedBrowsingData() {
        webView?.apply { stopLoading(); clearCache(true); clearFormData() }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }
    @Suppress("DEPRECATION")
    fun applyDeveloperSettings(value: DeveloperSettings) {
        webView?.settings?.apply {
            javaScriptEnabled = value.javaScriptEnabled
            loadsImagesAutomatically = value.imagesEnabled
            blockNetworkImage = !value.imagesEnabled
            userAgentString = when (value.userAgentMode) {
                UserAgentMode.Mobile -> null
                UserAgentMode.Desktop -> DESKTOP_USER_AGENT
                UserAgentMode.Custom -> value.customUserAgent
            }
        }
    }
    fun saveState(outState: Bundle) { webView?.saveState(outState) }

    fun extract(kind: ExtractionKind, onResult: (ExtractionResult) -> Unit) {
        val view = webView
        if (view == null) {
            onResult(ExtractionResult(kind, error = "No page is available."))
            return
        }
        view.evaluateJavascript(PageExtractionScripts.forKind(kind)) { encoded ->
            onResult(ExtractionResultDecoder.decode(kind, encoded))
        }
    }

    fun inspectAt(x: Float, y: Float, onResult: (InspectionResult) -> Unit) {
        val view = webView
        if (view == null) {
            onResult(InspectionResult(error = "No page is available."))
            return
        }
        val density = view.resources.displayMetrics.density
        view.evaluateJavascript(ElementInspectionScripts.selectAt(x / density, y / density)) { encoded ->
            onResult(InspectionResultDecoder.decode(encoded))
        }
    }

    fun clearInspection() { webView?.evaluateJavascript(ElementInspectionScripts.clear, null) }

    fun pageInfo(ssl: SslState, loadTimeMillis: Long?, onResult: (PageInfoResult) -> Unit) {
        val view = webView ?: return onResult(PageInfoResult(error = "No page is available."))
        view.evaluateJavascript(PageInfoScripts.inspect) { encoded ->
            onResult(PageInfoDecoder.decode(encoded, ssl, loadTimeMillis, CookieManager.getInstance().getCookie(view.url)))
        }
    }

    fun clearStorage(kind: StorageKind, expectedOrigin: String, onResult: (ClearStorageResult) -> Unit) {
        val view = webView ?: return onResult(ClearStorageResult(kind, false, "No page is available."))
        val activeOrigin = runCatching { URI(view.url).let { "${it.scheme}://${it.rawAuthority}" } }.getOrNull()
        if (activeOrigin != expectedOrigin) return onResult(ClearStorageResult(kind, false, "The active page origin changed."))
        if (kind == StorageKind.Cookies) {
            val path = runCatching { URI(view.url).path.orEmpty() }.getOrDefault("/")
            val paths = buildList {
                add("/"); var current = ""
                path.substringBeforeLast('/').split('/').filter(String::isNotEmpty).forEach { segment -> current += "/$segment"; add(current) }
            }.distinct()
            CookieManager.getInstance().getCookie(view.url).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter(String::isNotEmpty).forEach { name ->
                paths.forEach { cookiePath -> CookieManager.getInstance().setCookie(view.url, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=$cookiePath") }
            }
            CookieManager.getInstance().flush()
        }
        view.evaluateJavascript(PageInfoScripts.clear(kind, expectedOrigin)) { encoded ->
            val success = PageInfoDecoder.clearSucceeded(encoded)
            onResult(ClearStorageResult(kind, success, if(success) "${kind.label} cleared for $expectedOrigin" else "${kind.label} could not be cleared."))
        }
    }
}
