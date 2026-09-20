package dev.lightcopy.browser.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleLogTest {
    @Test fun `logs are bounded independently per tab`() {
        val logs = TabConsoleLogs(capacityPerTab = 2, clock = ConsoleClock { 42L })
        logs.append("first", ConsoleLevel.LOG, "one", "one.js", 1)
        logs.append("second", ConsoleLevel.ERROR, "other", "other.js", 4)
        logs.append("first", ConsoleLevel.WARNING, "two", "two.js", 2)
        logs.append("first", ConsoleLevel.ERROR, "three", "three.js", 3)

        assertEquals(listOf("two", "three"), logs.entries("first").map { it.message })
        assertEquals(listOf("other"), logs.entries("second").map { it.message })

        logs.clear("first")
        assertTrue(logs.entries("first").isEmpty())
        assertEquals(1, logs.entries("second").size)
    }

    @Test fun `errors filter accepts only error level`() {
        val error = entry(ConsoleLevel.ERROR)
        assertTrue(ConsoleFilter.ERRORS.accepts(error))
        ConsoleLevel.entries.filterNot { it == ConsoleLevel.ERROR }.forEach {
            assertFalse(ConsoleFilter.ERRORS.accepts(entry(it)))
        }
    }

    @Test fun `formatter includes level message source and line with optional timestamp`() {
        val entry = ConsoleEntry(1, 0, ConsoleLevel.WARNING, "deprecated", "app.js", 17)
        assertEquals("[WARNING] deprecated (app.js:17)", ConsoleLogFormatter.format(listOf(entry), false))
        assertTrue(ConsoleLogFormatter.format(listOf(entry), true).matches(
            Regex("\\[\\d{2}:\\d{2}:\\d{2}\\.\\d{3}] \\[WARNING] deprecated \\(app\\.js:17\\)"),
        ))
    }

    private fun entry(level: ConsoleLevel) = ConsoleEntry(1, 0, level, "message", "source", 1)
}
