package dev.lightcopy.browser.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil

data class DownloadInput(val url: String, val userAgent: String?, val contentDisposition: String?, val mimeType: String?)

object DownloadPolicy {
    fun error(input: DownloadInput): String? = when {
        input.url.isBlank() -> "Invalid download URL"
        runCatching { java.net.URI(input.url).scheme?.lowercase() }.getOrNull() !in setOf("http", "https") -> "Only HTTP and HTTPS downloads are supported"
        else -> null
    }
}

class AndroidDownloader(private val context: Context) {
    fun enqueue(input: DownloadInput): Result<Long> = runCatching {
        DownloadPolicy.error(input)?.let(::error)
        val filename = URLUtil.guessFileName(input.url, input.contentDisposition, input.mimeType)
        val request = DownloadManager.Request(Uri.parse(input.url))
            .setTitle(filename).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        input.mimeType?.let(request::setMimeType)
        input.userAgent?.let { request.addRequestHeader("User-Agent", it) }
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }
}
