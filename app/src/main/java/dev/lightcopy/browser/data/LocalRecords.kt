package dev.lightcopy.browser.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryRecord(val url: String, val title: String, val visitedAt: Long)
data class BookmarkRecord(val url: String, val title: String, val createdAt: Long)

interface LocalRecords {
    fun history(): List<HistoryRecord>
    fun bookmarks(): List<BookmarkRecord>
    fun recordVisit(url: String, title: String, incognito: Boolean, now: Long = System.currentTimeMillis())
    fun toggleBookmark(url: String, title: String, incognito: Boolean, now: Long = System.currentTimeMillis()): Boolean
    fun clearHistory()
    fun clearBookmarks()
}

class FileLocalRecords(context: Context, private val historyLimit: Int = 250) : LocalRecords {
    private val file = context.filesDir.resolve("browser-records.json")

    override fun history(): List<HistoryRecord> = read().first
    override fun bookmarks(): List<BookmarkRecord> = read().second

    override fun recordVisit(url: String, title: String, incognito: Boolean, now: Long) {
        if (incognito || !RecordPolicy.isWebUrl(url)) return
        val (history, bookmarks) = read()
        val updated = listOf(HistoryRecord(url, title.ifBlank { url }, now)) + history.filterNot { it.url == url }
        write(updated.take(historyLimit), bookmarks)
    }

    override fun toggleBookmark(url: String, title: String, incognito: Boolean, now: Long): Boolean {
        if (incognito || !RecordPolicy.isWebUrl(url)) return false
        val (history, bookmarks) = read()
        val exists = bookmarks.any { it.url == url }
        write(history, if (exists) bookmarks.filterNot { it.url == url }
        else listOf(BookmarkRecord(url, title.ifBlank { url }, now)) + bookmarks)
        return !exists
    }

    override fun clearHistory() { val (_, bookmarks) = read(); write(emptyList(), bookmarks) }
    override fun clearBookmarks() { val (history, _) = read(); write(history, emptyList()) }

    private fun read(): Pair<List<HistoryRecord>, List<BookmarkRecord>> = runCatching {
        val root = JSONObject(file.readText())
        fun JSONArray.historyItems() = (0 until length()).map { getJSONObject(it) }.map {
            HistoryRecord(it.getString("url"), it.getString("title"), it.getLong("at"))
        }
        fun JSONArray.bookmarkItems() = (0 until length()).map { getJSONObject(it) }.map {
            BookmarkRecord(it.getString("url"), it.getString("title"), it.getLong("at"))
        }
        root.optJSONArray("history")?.historyItems().orEmpty() to root.optJSONArray("bookmarks")?.bookmarkItems().orEmpty()
    }.getOrDefault(emptyList<HistoryRecord>() to emptyList())

    private fun write(history: List<HistoryRecord>, bookmarks: List<BookmarkRecord>) {
        val root = JSONObject().apply {
            put("history", JSONArray().apply { history.forEach { put(JSONObject().put("url", it.url).put("title", it.title).put("at", it.visitedAt)) } })
            put("bookmarks", JSONArray().apply { bookmarks.forEach { put(JSONObject().put("url", it.url).put("title", it.title).put("at", it.createdAt)) } })
        }
        file.writeText(root.toString())
    }
}

object RecordPolicy {
    fun isWebUrl(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")
}
