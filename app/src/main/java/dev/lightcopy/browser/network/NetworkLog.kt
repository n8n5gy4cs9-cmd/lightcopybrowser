package dev.lightcopy.browser.network

data class NetworkEntry(
    val id: Long,
    val url: String,
    val method: String,
    val status: Int? = null,
    val blocked: Boolean = false,
    val timestampMillis: Long,
)

fun interface NetworkClock { fun now(): Long }

class TabNetworkLogs(
    private val capacityPerTab: Int = 200,
    private val clock: NetworkClock = NetworkClock(System::currentTimeMillis),
) {
    init { require(capacityPerTab > 0) }
    private val logs = mutableMapOf<String, ArrayDeque<NetworkEntry>>()
    private var nextId = 0L

    @Synchronized
    fun record(tabId: String, url: String, method: String, blocked: Boolean = false): NetworkEntry {
        val entry = NetworkEntry(++nextId, url, method.ifBlank { "GET" }, if (blocked) 204 else null, blocked, clock.now())
        val tabLogs = logs.getOrPut(tabId) { ArrayDeque() }
        tabLogs.addLast(entry)
        while (tabLogs.size > capacityPerTab) tabLogs.removeFirst()
        return entry
    }

    @Synchronized
    fun observeStatus(tabId: String, url: String, method: String, status: Int) {
        val tabLogs = logs.getOrPut(tabId) { ArrayDeque() }
        val index = tabLogs.indexOfLast { it.url == url && it.method == method && !it.blocked }
        if (index >= 0) tabLogs[index] = tabLogs[index].copy(status = status)
        else {
            tabLogs.addLast(NetworkEntry(++nextId, url, method.ifBlank { "GET" }, status, timestampMillis = clock.now()))
            while (tabLogs.size > capacityPerTab) tabLogs.removeFirst()
        }
    }

    @Synchronized fun entries(tabId: String): List<NetworkEntry> = logs[tabId]?.toList().orEmpty()
    @Synchronized fun clear(tabId: String) { logs.remove(tabId) }
    @Synchronized fun removeTab(tabId: String) { logs.remove(tabId) }
}

object AdBlockPolicy {
    private val blockedHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "adsrvr.org", "amazon-adsystem.com",
    )

    fun shouldBlock(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return false
        return blockedHosts.any { host == it || host.endsWith(".$it") }
    }
}
