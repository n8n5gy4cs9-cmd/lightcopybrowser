package dev.lightcopy.browser.settings

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import dev.lightcopy.browser.data.FileLocalRecords

object ClearOnExitPolicy {
    fun hasSelectedData(value: ClearOnExit): Boolean = value.history || value.bookmarks || value.webData
}

/** Runs only when the activity is actually finishing, never for configuration changes. */
class ExitDataClearer(private val context: Context) {
    fun clear(selection: ClearOnExit, clearAttachedWebView: () -> Unit) {
        val records = FileLocalRecords(context)
        if (selection.history) records.clearHistory()
        if (selection.bookmarks) records.clearBookmarks()
        if (selection.webData) {
            clearAttachedWebView()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
    }
}
