package dev.lightcopy.browser.console

import android.webkit.ConsoleMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConsoleLevel { ERROR, WARNING, INFO, LOG, DEBUG }

data class ConsoleEntry(
    val sequence: Long,
    val timestampMillis: Long,
    val level: ConsoleLevel,
    val message: String,
    val source: String,
    val line: Int,
)

enum class ConsoleFilter(val label: String) {
    ALL("All"), ERRORS("Errors"), WARNING("Warnings"), INFO("Info"), LOG("Log"), DEBUG("Debug");

    fun accepts(entry: ConsoleEntry): Boolean = when (this) {
        ALL -> true
        ERRORS -> entry.level == ConsoleLevel.ERROR
        WARNING -> entry.level == ConsoleLevel.WARNING
        INFO -> entry.level == ConsoleLevel.INFO
        LOG -> entry.level == ConsoleLevel.LOG
        DEBUG -> entry.level == ConsoleLevel.DEBUG
    }
}

fun interface ConsoleClock { fun nowMillis(): Long }

object SystemConsoleClock : ConsoleClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

class TabConsoleLogs(
    private val capacityPerTab: Int = 500,
    private val clock: ConsoleClock = SystemConsoleClock,
) {
    init { require(capacityPerTab > 0) }

    private val logs = mutableMapOf<String, ArrayDeque<ConsoleEntry>>()
    private var nextSequence = 0L

    @Synchronized
    fun append(tabId: String, level: ConsoleLevel, message: String, source: String, line: Int): ConsoleEntry {
        val entry = ConsoleEntry(++nextSequence, clock.nowMillis(), level, message, source, line)
        val tabLogs = logs.getOrPut(tabId) { ArrayDeque() }
        tabLogs.addLast(entry)
        while (tabLogs.size > capacityPerTab) tabLogs.removeFirst()
        return entry
    }

    @Synchronized fun entries(tabId: String): List<ConsoleEntry> = logs[tabId]?.toList().orEmpty()
    @Synchronized fun clear(tabId: String) { logs.remove(tabId) }
    @Synchronized fun removeTab(tabId: String) { logs.remove(tabId) }
}

fun ConsoleMessage.toConsoleLevel(): ConsoleLevel = when (messageLevel()) {
    ConsoleMessage.MessageLevel.ERROR -> ConsoleLevel.ERROR
    ConsoleMessage.MessageLevel.WARNING -> ConsoleLevel.WARNING
    ConsoleMessage.MessageLevel.TIP -> ConsoleLevel.INFO
    ConsoleMessage.MessageLevel.LOG -> ConsoleLevel.LOG
    ConsoleMessage.MessageLevel.DEBUG -> ConsoleLevel.DEBUG
    else -> ConsoleLevel.LOG
}

object ConsoleLogFormatter {
    fun format(entries: List<ConsoleEntry>, includeTimestamps: Boolean): String = entries.joinToString("\n") { entry ->
        buildString {
            if (includeTimestamps) {
                val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date(entry.timestampMillis))
                append('[').append(time).append("] ")
            }
            append('[').append(entry.level.name).append("] ").append(entry.message)
            if (entry.source.isNotBlank()) {
                append(" (").append(entry.source)
                if (entry.line > 0) append(':').append(entry.line)
                append(')')
            }
        }
    }
}
