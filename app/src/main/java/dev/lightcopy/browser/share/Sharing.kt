package dev.lightcopy.browser.share

import android.content.Context
import android.content.Intent

object ShareText {
    fun value(title: String, url: String): String? = url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        ?.let { if (title.isBlank()) it else "$title\n$it" }
}

fun sharePage(context: Context, title: String, url: String): Boolean {
    val text = ShareText.value(title, url) ?: return false
    return runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }, "Share page").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
