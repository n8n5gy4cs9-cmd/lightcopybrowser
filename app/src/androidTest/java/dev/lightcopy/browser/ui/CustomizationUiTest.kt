package dev.lightcopy.browser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.lightcopy.browser.console.ConsoleEntry
import dev.lightcopy.browser.console.ConsoleLevel
import dev.lightcopy.browser.settings.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CustomizationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun clearAddressAndOpenSettingsFromConsole() {
        var state by mutableStateOf(BrowserShellState())
        compose.setContent { LightCopyTheme { BrowserShell(state, { state = reduce(state, it) }) } }
        compose.onNodeWithContentDescription("Clear address").performClick()
        compose.runOnIdle { assertEquals("", state.address) }
        compose.onNodeWithContentDescription("Console", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Console settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test fun liveLayoutKeepsPageAliveAndExitVisibleInEveryPlacement() {
        var state by mutableStateOf(BrowserShellState(selectedTool = QuickTool.Console,
            consoleEntries = listOf(ConsoleEntry(1, 0, ConsoleLevel.LOG, "fixture message", "", 0))))
        var created = 0
        var disposed = 0
        compose.setContent {
            LightCopyTheme {
                BrowserShell(state, { state = reduce(state, it) }, page = {
                    DisposableEffect(Unit) { created++; onDispose { disposed++ } }
                    Box(Modifier.fillMaxSize().testTag("live-page")) { Text("Local fixture") }
                })
            }
        }
        compose.onNodeWithContentDescription("Open live page and console").performClick()
        ConsolePlacement.entries.forEach { placement ->
            compose.runOnIdle { state = state.copy(appSettings = state.appSettings.copy(consolePlacement = placement, consolePercent = 35)) }
            compose.onNodeWithContentDescription("Exit live console").assertIsDisplayed()
            compose.onNodeWithText("Local fixture").assertIsDisplayed()
            compose.onNodeWithText("[LOG] fixture message").assertIsDisplayed()
            compose.onNodeWithText("Time on").assertDoesNotExist()
            compose.onNodeWithText("All").assertDoesNotExist()
            val page = compose.onNodeWithTag("live-page").fetchSemanticsNode().boundsInRoot
            val log = compose.onNodeWithText("[LOG] fixture message").fetchSemanticsNode().boundsInRoot
            when (placement) {
                ConsolePlacement.Bottom -> assertTrue(page.bottom <= log.top)
                ConsolePlacement.Top -> assertTrue(log.bottom <= page.top)
                ConsolePlacement.Left -> assertTrue(log.right <= page.left)
                ConsolePlacement.Right -> assertTrue(page.right <= log.left)
            }
        }
        compose.onNodeWithContentDescription("Exit live console").performClick()
        compose.runOnIdle { assertEquals(1, created); assertEquals(0, disposed); assertFalse(state.isLiveConsole) }
    }
    @Test fun liveConsoleExplainsEmptyCaptureAndShowsNewMessages() {
        var state by mutableStateOf(BrowserShellState(isLiveConsole = true, selectedTool = QuickTool.Console))
        compose.setContent { LightCopyTheme { BrowserShell(state, { state = reduce(state, it) }) } }
        compose.onNodeWithText("Waiting for console messages").assertIsDisplayed()
        compose.runOnIdle {
            state = reduce(state, BrowserAction.ConsoleCaptured(listOf(ConsoleEntry(1, 0, ConsoleLevel.ERROR, "new live message", "", 0))))
        }
        compose.onNodeWithText("[ERROR] new live message").assertIsDisplayed()
        compose.onNodeWithText("Waiting for console messages").assertDoesNotExist()
    }
    @Test fun realWebViewMessagesRemainVisibleWhileDebuggingLive() {
        var state by mutableStateOf(BrowserShellState(isLiveConsole = true, selectedTool = QuickTool.Console))
        val controller = dev.lightcopy.browser.browser.BrowserController()
        val html = "<html><body style='background:white'>Local live fixture<script>setTimeout(function(){console.error('bridge live fixture')},250)</script></body></html>"
        val fixtureUrl = "data:text/html;base64," + android.util.Base64.encodeToString(html.toByteArray(), android.util.Base64.NO_WRAP)
        compose.setContent {
            LightCopyTheme {
                BrowserShell(state, { state = reduce(state, it) }, page = {
                    dev.lightcopy.browser.browser.WebViewHost(fixtureUrl, controller, null, {}, onConsoleMessage = { message ->
                        state = reduce(state, BrowserAction.ConsoleCaptured(state.consoleEntries + ConsoleEntry(1, 0, message.level, message.message, "", 0)))
                    }, modifier = Modifier.fillMaxSize())
                })
            }
        }
        compose.waitUntil(5000) { state.consoleEntries.any { it.message == "bridge live fixture" } }
        ConsolePlacement.entries.forEach { placement ->
            compose.runOnIdle { state = state.copy(appSettings = state.appSettings.copy(consolePlacement = placement)) }
            compose.onNodeWithText("[ERROR] bridge live fixture").assertIsDisplayed()
            val pixels = compose.onNodeWithText("[ERROR] bridge live fixture").captureToImage().toPixelMap()
            var coloredPixels = 0
            for (x in 0 until pixels.width step 3) for (y in 0 until pixels.height step 3) {
                val color = pixels[x, y]
                if (color.red > .7f && color.green < .65f && color.blue > .35f) coloredPixels++
            }
            assertTrue("Console message must render colored pixels instead of a black panel", coloredPixels > 0)
        }
    }
    @Test fun compactAddressAndSelectionPositionUseRequestedSizes() {
        var state by mutableStateOf(BrowserShellState(appSettings = AppSettings(
            addressHeightDp = 20, addressFontSp = 8, showStatusStrip = false,
            actionSizeDp = 20, actionContent = ActionContent.Icons,
            selectionButtonSizeDp = 16, showConsoleBadge = false)))
        compose.setContent { LightCopyTheme { BrowserShell(state, { state = reduce(state, it) }) } }
        compose.onNodeWithTag("address-field").assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(20f))
        val description = "Toggle long press between text selection and element inspection"
        QuickButtonPosition.entries.forEach { position ->
            compose.runOnIdle { state = state.copy(appSettings = state.appSettings.copy(selectionButtonPosition = position)) }
            compose.onNodeWithContentDescription(description).assertIsDisplayed().assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(16f)).assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(16f))
        }
        compose.runOnIdle { state = state.copy(appSettings = state.appSettings.copy(addressHeightDp = 5, addressFontSp = 5)) }
        compose.onNodeWithTag("address-field").assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(5f))
    }
    @Test fun singleContentActionsAreCenteredInBothDirections() {
        var state by mutableStateOf(BrowserShellState(appSettings = AppSettings(showSelectionToggle = false, showConsoleBadge = false)))
        compose.setContent { LightCopyTheme { BrowserShell(state, { state = reduce(state, it) }) } }
        listOf(ActionContent.Icons, ActionContent.Labels).forEach { content ->
            compose.runOnIdle { state = state.copy(appSettings = state.appSettings.copy(actionContent = content)) }
            QuickTool.entries.forEach { tool ->
                val button = compose.onNodeWithContentDescription(tool.label).fetchSemanticsNode().boundsInRoot
                val text = compose.onNodeWithText(if (content == ActionContent.Icons) tool.glyph else tool.label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals("$content ${tool.label} horizontal center", button.center.x, text.center.x, 2f)
                assertEquals("$content ${tool.label} vertical center", button.center.y, text.center.y, 2f)
            }
        }
    }
    @Test fun consoleBadgeCanBeSizedHiddenAndConfiguredIndependently() {
        var state by mutableStateOf(BrowserShellState(consoleEntries = listOf(ConsoleEntry(1, 0, ConsoleLevel.LOG, "fixture", "", 0)),
            appSettings = AppSettings(actionSizeDp = 70, actionContent = ActionContent.Icons, consoleBadgeSizeDp = 24, consoleBadgeContent = ActionContent.Both)))
        compose.setContent { LightCopyTheme { BrowserShell(state, { state = reduce(state, it) }) } }
        val description = "Open console, 1 messages"
        compose.onNodeWithContentDescription(description).assertIsDisplayed().assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(24f)).assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(24f))
        compose.onNodeWithText(">_ 1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(">_ 1", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { getLayout ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            getLayout(layouts)
            assertEquals(1, layouts.single().lineCount)
            assertTrue("Full icon and count must fit inside the badge", layouts.single().getLineRight(0) <= layouts.single().size.width)
            assertFalse(layouts.single().isLineEllipsized(0))
        }
        compose.runOnIdle { state = state.copy(appSettings = state.appSettings.copy(consoleBadgeSizeDp = 36, actionSizeDp = 20, consoleBadgeContent = ActionContent.Labels)) }
        compose.onNodeWithContentDescription(description).assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(36f)).assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(36f))
        compose.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { state = state.copy(appSettings = state.appSettings.copy(showConsoleBadge = false)) }
        compose.onNodeWithContentDescription(description).assertDoesNotExist()
    }
    @Test fun liveNavigationDefaultsAndNetworkOverlayPreservePage() {
        var state by mutableStateOf(BrowserShellState(isLiveConsole = true, selectedTool = QuickTool.Console, canGoBack = true,
            consoleEntries = listOf(ConsoleEntry(1, 0, ConsoleLevel.LOG, "kept console message", "", 0))))
        var pageCreated = 0
        compose.setContent { LightCopyTheme { BrowserShell(state, { state = reduce(state, it) }, page = {
            DisposableEffect(Unit) { pageCreated++; onDispose {} }
            Box(Modifier.fillMaxSize().testTag("network-fixture-page")) { Text("Offline page") }
        }) } }
        compose.onNodeWithContentDescription("Live Back").assertIsDisplayed()
        compose.onNodeWithContentDescription("Live Refresh").assertIsDisplayed()
        compose.onNodeWithContentDescription("Live Forward").assertDoesNotExist()
        compose.onNodeWithContentDescription("Live Stop").assertDoesNotExist()
        compose.onNodeWithContentDescription("Live Network").performClick()
        compose.onNodeWithText("Waiting for network requests").assertIsDisplayed()
        compose.runOnIdle { state = reduce(state, BrowserAction.NetworkCaptured(listOf(dev.lightcopy.browser.network.NetworkEntry(1, "https://fixture.test/request", "GET", 200, timestampMillis = 0)))) }
        ConsolePlacement.entries.forEach { placement ->
            compose.runOnIdle { state = state.copy(appSettings = state.appSettings.copy(consolePlacement = placement)) }
            compose.onNodeWithText("GET 200\nhttps://fixture.test/request").assertIsDisplayed()
            assertEquals(compose.onNodeWithTag("live-console-area").fetchSemanticsNode().boundsInRoot,
                compose.onNodeWithTag("live-network-panel").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithText("Offline page").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Close live network").performClick()
        compose.onNodeWithTag("live-network-panel").assertDoesNotExist()
        compose.onNodeWithText("[LOG] kept console message").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, pageCreated) }
    }

    @Test fun liveNavigationSizePlacementVisibilityAndStopAreConfigurable() {
        var state by mutableStateOf(BrowserShellState(isLiveConsole = true, selectedTool = QuickTool.Console,
            isLoading = true, canGoForward = true, appSettings = AppSettings(liveNavigationSizeDp = 20, showLiveStop = true, showLiveForward = true)))
        val actions = mutableListOf<BrowserAction>()
        compose.setContent { LightCopyTheme { BrowserShell(state, { actions += it; state = reduce(state, it) }) } }
        compose.onNodeWithContentDescription("Live Forward").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Live Stop").assertIsEnabled().performClick()
        compose.runOnIdle { assertFalse(state.isLoading); assertTrue(actions.contains(BrowserAction.StopLoading)); assertTrue(actions.contains(BrowserAction.NavigateForward)) }
        ActionPlacement.entries.forEach { position ->
            compose.runOnIdle { state = state.copy(appSettings = state.appSettings.copy(liveNavigationPosition = position)) }
            compose.onNodeWithContentDescription("Live Refresh").assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(20f)).assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(20f))
            val area = compose.onNodeWithTag("live-page-area").fetchSemanticsNode().boundsInRoot
            val bar = compose.onNodeWithTag("live-navigation").fetchSemanticsNode().boundsInRoot
            if (position == ActionPlacement.Top) assertEquals(area.top, bar.top, 2f) else assertEquals(area.bottom, bar.bottom, 2f)
        }
        compose.runOnIdle { state = state.copy(appSettings = state.appSettings.copy(showLiveNavigation = false)) }
        compose.onNodeWithTag("live-navigation").assertDoesNotExist()
        compose.onNodeWithContentDescription("Exit live console").assertIsDisplayed()
    }
}
