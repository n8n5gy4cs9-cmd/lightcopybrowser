package dev.lightcopy.browser.ui

import dev.lightcopy.browser.console.ConsoleEntry
import dev.lightcopy.browser.console.ConsoleLevel
import org.junit.Assert.*
import org.junit.Test

class ConsoleCustomizationTest {
    private fun entry(sequence: Long) = ConsoleEntry(sequence, 0, ConsoleLevel.LOG, "message $sequence", "fixture", 1)

    @Test fun pauseKeepsCapturingAndResumeShowsLatestMessages() {
        val first = entry(1)
        var state = BrowserShellState(consoleEntries = listOf(first))
        state = reduce(state, BrowserAction.ToggleConsolePause)
        state = reduce(state, BrowserAction.ConsoleCaptured(listOf(first, entry(2))))
        assertEquals(listOf(first), state.pausedConsoleEntries)
        assertEquals(2, state.consoleEntries.size)
        state = reduce(state, BrowserAction.ToggleConsolePause)
        assertNull(state.pausedConsoleEntries)
        assertEquals(2, state.consoleEntries.size)
    }

    @Test fun clearAlsoClearsPausedSnapshot() {
        var state = reduce(BrowserShellState(consoleEntries = listOf(entry(1))), BrowserAction.ToggleConsolePause)
        state = reduce(state, BrowserAction.ClearConsole)
        assertTrue(state.consoleEntries.isEmpty())
        assertTrue(state.pausedConsoleEntries!!.isEmpty())
    }

    @Test fun liveConsoleHasAnExitAndSelectionModeIsIndependent() {
        var state = reduce(BrowserShellState(), BrowserAction.ToggleLiveConsole)
        assertTrue(state.isLiveConsole)
        state = reduce(state, BrowserAction.DismissConsole)
        assertFalse(state.isLiveConsole)
        assertNull(state.selectedTool)
        val original = state.appSettings.nativeTextSelection
        state = reduce(state, BrowserAction.ToggleSelectionMode)
        assertEquals(!original, state.appSettings.nativeTextSelection)
    }
    @Test fun networkPanelIsScopedToLiveModeAndExitResetsIt() {
        assertFalse(reduce(BrowserShellState(), BrowserAction.OpenLiveNetwork).isLiveNetworkOpen)
        val state = reduce(BrowserShellState(isLiveConsole = true), BrowserAction.OpenLiveNetwork)
        assertTrue(state.isLiveNetworkOpen)
        assertFalse(reduce(state, BrowserAction.CloseLiveNetwork).isLiveNetworkOpen)
        val exited = reduce(state, BrowserAction.DismissConsole)
        assertFalse(exited.isLiveNetworkOpen)
        assertFalse(exited.isLiveConsole)
    }
}
