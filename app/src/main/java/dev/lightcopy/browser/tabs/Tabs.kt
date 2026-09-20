package dev.lightcopy.browser.tabs

const val MAX_TABS = 8

data class BrowserTab(
    val id: String,
    val title: String = "New tab",
    val url: String = "https://developer.android.com",
    val incognito: Boolean = false,
)

class TabRegistry(
    initial: BrowserTab = BrowserTab("tab-1"),
    private val capacity: Int = MAX_TABS,
) {
    private val tabs = mutableListOf(initial)
    private var nextId = 2
    var activeId: String = initial.id
        private set

    init { require(capacity > 0) }

    fun all(): List<BrowserTab> = tabs.toList()
    fun active(): BrowserTab = tabs.first { it.id == activeId }

    fun create(incognito: Boolean): BrowserTab? {
        if (tabs.size >= capacity) return null
        val tab = BrowserTab("tab-${nextId++}", incognito = incognito)
        tabs += tab
        activeId = tab.id
        return tab
    }

    fun select(id: String): Boolean {
        if (tabs.none { it.id == id }) return false
        activeId = id
        return true
    }

    fun update(id: String, title: String? = null, url: String? = null) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index >= 0) tabs[index] = tabs[index].copy(
            title = title ?: tabs[index].title,
            url = url ?: tabs[index].url,
        )
    }

    /** Returns the closed tab. The last tab is replaced by a fresh normal tab. */
    fun close(id: String): BrowserTab? {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return null
        val closed = tabs.removeAt(index)
        if (tabs.isEmpty()) tabs += BrowserTab("tab-${nextId++}")
        if (activeId == id) activeId = tabs[index.coerceAtMost(tabs.lastIndex)].id
        return closed
    }
}

/**
 * Android WebView has one process-wide cookie/storage profile. LightCopy therefore clears that
 * profile whenever browsing crosses the normal/incognito boundary, and again when the last
 * incognito tab closes. Incognito WebViews additionally use no cache and no DOM storage.
 */
object IncognitoPolicy {
    fun requiresSharedDataClear(fromIncognito: Boolean, toIncognito: Boolean): Boolean =
        fromIncognito != toIncognito

    fun shouldRecordInAppHistory(incognito: Boolean): Boolean = !incognito
}
