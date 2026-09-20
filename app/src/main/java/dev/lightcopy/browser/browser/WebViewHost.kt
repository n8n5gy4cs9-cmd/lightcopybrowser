package dev.lightcopy.browser.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.lightcopy.browser.ui.BrowserError
import dev.lightcopy.browser.console.ConsoleLevel
import dev.lightcopy.browser.console.toConsoleLevel
import dev.lightcopy.browser.inspect.InspectionResult
import dev.lightcopy.browser.pageinfo.SslState
import dev.lightcopy.browser.network.AdBlockPolicy
import dev.lightcopy.browser.downloads.DownloadInput
import dev.lightcopy.browser.settings.AppSettings
import org.json.JSONObject

data class NetworkObservation(val url: String, val method: String, val status: Int? = null, val blocked: Boolean = false)

data class PageUpdate(
    val url: String? = null,
    val title: String? = null,
    val progress: Int? = null,
    val loading: Boolean? = null,
    val canGoBack: Boolean? = null,
    val canGoForward: Boolean? = null,
    val error: BrowserError? = null,
    val clearError: Boolean = false,
    val clearInspection: Boolean = false,
    val sslState: SslState? = null,
    val loadTimeMillis: Long? = null,
    val favicon: Bitmap? = null,
    val clearFavicon: Boolean = false,
)

data class ConsoleUpdate(val level: ConsoleLevel, val message: String, val source: String, val line: Int)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewHost(
    url: String,
    controller: BrowserController,
    restoredState: Bundle?,
    onUpdate: (PageUpdate) -> Unit,
    onConsoleMessage: (ConsoleUpdate) -> Unit = {},
    onInspection: (InspectionResult) -> Unit = {},
    developerSettings: DeveloperSettings = DeveloperSettings(),
    appSettings: AppSettings = AppSettings(),
    onNetworkObservation: (NetworkObservation) -> Unit = {},
    incognito: Boolean = false,
    onFindResult: (activeMatch: Int, matchCount: Int, done: Boolean) -> Unit = { _, _, _ -> },
    onSaveState: (Bundle) -> Unit = {},
    onDownload: (DownloadInput) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsHolder = remember { arrayOf(developerSettings) }
    settingsHolder[0] = developerSettings
    val appSettingsHolder = remember { arrayOf(appSettings) }
    appSettingsHolder[0] = appSettings
    val view = remember {
        WebView(context).apply {
            var loadStartedAt = 0L
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = !incognito
            if (incognito) settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true
            settings.setGeolocationEnabled(false)
            if (android.os.Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            WebView.setWebContentsDebuggingEnabled(false)
            setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
                onDownload(DownloadInput(downloadUrl.orEmpty(), userAgent, contentDisposition, mimeType))
            }
            setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                onFindResult(activeMatchOrdinal, numberOfMatches, isDoneCounting)
            }

            val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true
                override fun onLongPress(event: MotionEvent) {
                    if (!appSettingsHolder[0].nativeTextSelection) controller.inspectAt(event.x, event.y, onInspection)
                }
            })
            setOnTouchListener { _, event ->
                if (!appSettingsHolder[0].nativeTextSelection) gestures.onTouchEvent(event)
                false
            }
            setOnLongClickListener { !appSettingsHolder[0].nativeTextSelection }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    onConsoleMessage(ConsoleUpdate(
                        consoleMessage.toConsoleLevel(), consoleMessage.message(),
                        consoleMessage.sourceId(), consoleMessage.lineNumber(),
                    ))
                    return true
                }

                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    onUpdate(PageUpdate(progress = newProgress, loading = newProgress < 100))
                }

                override fun onReceivedTitle(view: WebView, pageTitle: String?) {
                    onUpdate(PageUpdate(title = pageTitle?.takeIf(String::isNotBlank)))
                }

                override fun onReceivedIcon(view: WebView, icon: Bitmap?) { onUpdate(PageUpdate(favicon = icon)) }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme !in setOf("http", "https")

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val blocked = settingsHolder[0].adBlockingEnabled && AdBlockPolicy.shouldBlock(request.url.toString())
                    view.post { onNetworkObservation(NetworkObservation(request.url.toString(), request.method, blocked = blocked)) }
                    return if (blocked) WebResourceResponse(
                        "text/plain", "UTF-8", 204, "No Content", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)),
                    ) else null
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                    onNetworkObservation(NetworkObservation(request.url.toString(), request.method, errorResponse.statusCode))
                }

                override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) {
                    loadStartedAt = SystemClock.elapsedRealtime()
                    onUpdate(PageUpdate(url = pageUrl, loading = true, clearError = true, clearInspection = true, favicon = favicon, clearFavicon = favicon == null))
                }

                override fun onPageFinished(view: WebView, pageUrl: String?) {
                    view.applyInjection(appSettingsHolder[0])
                    val ssl = when { pageUrl?.startsWith("https://") == true && view.certificate != null -> SslState.Secure
                        pageUrl?.startsWith("http://") == true -> SslState.Insecure else -> SslState.Unavailable }
                    onUpdate(PageUpdate(url = pageUrl, progress = 100, loading = false, sslState = ssl,
                        loadTimeMillis = (SystemClock.elapsedRealtime() - loadStartedAt).takeIf { loadStartedAt > 0 },
                        canGoBack = view.canGoBack(), canGoForward = view.canGoForward()))
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) onUpdate(PageUpdate(
                        loading = false,
                        error = BrowserError(error.description.toString(),
                            error.errorCode == ERROR_HOST_LOOKUP || error.errorCode == ERROR_CONNECT),
                    ))
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel()
                    onUpdate(PageUpdate(loading = false, error = BrowserError("Secure connection failed", false)))
                }
            }
            controller.attach(this)
            if (restoredState == null || restoreState(restoredState) == null) loadUrl(url)
        }
    }

    AndroidView(factory = { view }, modifier = modifier, update = { it.applyDeveloperSettings(developerSettings) })
    DisposableEffect(view) {
        onDispose {
            val state = Bundle()
            view.saveState(state)
            onSaveState(state)
            controller.detach(view)
            view.stopLoading()
            view.webChromeClient = null
            view.webViewClient = WebViewClient()
            view.destroy()
        }
    }
}

private fun WebView.applyInjection(settings: AppSettings) {
    if (!url.orEmpty().startsWith("http")) return
    if (settings.cssInjection.isNotBlank()) {
        val css = JSONObject.quote(settings.cssInjection)
        evaluateJavascript("""(function(){var id='lightcopy-injected-css';var node=document.getElementById(id);if(!node){node=document.createElement('style');node.id=id;(document.head||document.documentElement).appendChild(node)}node.textContent=$css;})()""", null)
    }
    if (settings.javaScriptInjection.isNotBlank()) {
        evaluateJavascript("(function(){\n${settings.javaScriptInjection}\n})()", null)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.applyDeveloperSettings(value: DeveloperSettings) {
    settings.javaScriptEnabled = value.javaScriptEnabled
    settings.loadsImagesAutomatically = value.imagesEnabled
    settings.blockNetworkImage = !value.imagesEnabled
    settings.userAgentString = when (value.userAgentMode) {
        UserAgentMode.Mobile -> null
        UserAgentMode.Desktop -> DESKTOP_USER_AGENT
        UserAgentMode.Custom -> value.customUserAgent
    }
}
