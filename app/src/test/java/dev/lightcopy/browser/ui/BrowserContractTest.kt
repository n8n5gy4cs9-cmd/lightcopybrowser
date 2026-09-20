package dev.lightcopy.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.lightcopy.browser.fullscreen.ExitControlPosition
import dev.lightcopy.browser.copy.ExtractionKind
import dev.lightcopy.browser.copy.ExtractionResult
import dev.lightcopy.browser.inspect.ElementInspection
import dev.lightcopy.browser.inspect.InspectionResult
import dev.lightcopy.browser.pageinfo.*
import dev.lightcopy.browser.browser.UserAgentMode
import dev.lightcopy.browser.reader.ReaderContent

class BrowserContractTest {
    @Test fun `reader and dark mode have explicit reversible state`() {
        val loading = reduce(BrowserShellState(isMenuOpen = true), BrowserAction.OpenReader)
        assertTrue(loading.isReaderLoading)
        assertFalse(loading.isMenuOpen)
        val content = ReaderContent("Title", "Author", "Body")
        val ready = reduce(loading, BrowserAction.ReaderCompleted(content, null))
        assertEquals(content, ready.readerContent)
        assertNull(reduce(ready, BrowserAction.CloseReader).readerContent)
        val dark = reduce(BrowserShellState(isMenuOpen = true), BrowserAction.TogglePageDark)
        assertTrue(dark.isPageDark)
        assertFalse(reduce(dark, BrowserAction.TogglePageDark).isPageDark)
    }
    @Test fun fullscreenActionsEnterExitAndPersistAClampedFreePosition() {
        val entered = reduce(BrowserShellState(isMenuOpen = true), BrowserAction.EnterFullscreen)
        assertTrue(entered.isFullscreen)
        assertFalse(entered.isMenuOpen)

        val dragged = reduce(entered, BrowserAction.SaveFreeDrag(1.4f, -.2f))
        assertEquals(ExitControlPosition.FreeDrag, dragged.fullscreenPreferences.position)
        assertEquals(1f, dragged.fullscreenPreferences.freeX)
        assertEquals(0f, dragged.fullscreenPreferences.freeY)
        assertFalse(reduce(dragged, BrowserAction.ExitFullscreen).isFullscreen)
    }
    @Test fun `editing address preserves draft`() {
        val state = reduce(BrowserShellState(), BrowserAction.EditAddress(" example.com "))
        assertEquals(" example.com ", state.address)
    }

    @Test fun `submit normalizes hostname and closes tool`() {
        val initial = BrowserShellState(address = "example.com", selectedTool = QuickTool.Console)
        val state = reduce(initial, BrowserAction.SubmitAddress)
        assertEquals("https://example.com", state.currentUrl)
        assertNull(state.selectedTool)
    }

    @Test fun `plain words become a search`() {
        assertEquals("https://www.google.com/search?q=compose+webview", normalizeAddress("compose webview"))
    }

    @Test fun `tool selection is represented in state`() {
        val state = reduce(BrowserShellState(isMenuOpen = true), BrowserAction.SelectTool(QuickTool.Source))
        assertEquals(QuickTool.Source, state.selectedTool)
        assertEquals(false, state.isMenuOpen)
    }

    @Test fun `extraction lifecycle retains its kind and content`() {
        val loading = reduce(BrowserShellState(), BrowserAction.RequestExtraction(ExtractionKind.RenderedDom))
        val complete = reduce(
            loading,
            BrowserAction.ExtractionCompleted(ExtractionResult(ExtractionKind.RenderedDom, content = "<html></html>")),
        )
        assertEquals(true, loading.extraction?.isLoading)
        assertEquals("<html></html>", complete.extraction?.content)
        assertEquals(false, complete.extraction?.isLoading)
    }

    @Test fun `stale extraction result does not reopen or replace viewer`() {
        val dismissed = reduce(
            reduce(BrowserShellState(), BrowserAction.RequestExtraction(ExtractionKind.OriginalHtml)),
            BrowserAction.DismissExtraction,
        )
        val stale = reduce(
            dismissed,
            BrowserAction.ExtractionCompleted(ExtractionResult(ExtractionKind.OriginalHtml, content = "stale")),
        )
        assertNull(stale.extraction)
    }

    @Test fun `advanced extraction opens the info workflow`() {
        val state = reduce(BrowserShellState(), BrowserAction.RequestExtraction(ExtractionKind.Markdown))
        assertEquals(QuickTool.Info, state.selectedTool)
        assertEquals(ExtractionKind.Markdown, state.extraction?.kind)
    }

    @Test fun `inspection details enter and leave state`() {
        val inspection = ElementInspection("button", "save", "primary", "#save", "<button id=\"save\">")
        val selected = reduce(BrowserShellState(), BrowserAction.InspectionCompleted(InspectionResult(inspection)))
        assertEquals(inspection, selected.inspection)
        assertEquals(QuickTool.Info, selected.selectedTool)
        assertNull(reduce(selected, BrowserAction.DismissInspector).inspection)
    }

    @Test fun `page info and confirmed clear have explicit state`() {
        val loading = reduce(BrowserShellState(), BrowserAction.RequestPageInfo)
        assertTrue(loading.pageInfo!!.isLoading)
        val info = PageInfo("T","https://x","https://x",SslState.Secure,1,"1 × 1",emptyList(),emptyList(),"",emptyList(),emptyList(),emptyList())
        val loaded = reduce(loading, BrowserAction.PageInfoCompleted(PageInfoResult(info)))
        val confirming = reduce(loaded, BrowserAction.RequestStorageClear(StorageKind.LocalStorage))
        assertEquals(StorageKind.LocalStorage, confirming.pendingStorageClear)
        assertNull(reduce(confirming, BrowserAction.CancelStorageClear).pendingStorageClear)
    }

    @Test fun `developer controls validate custom ua before applying`() {
        var state = reduce(BrowserShellState(), BrowserAction.OpenDeveloperSettings)
        state = reduce(state, BrowserAction.SetUserAgentMode(UserAgentMode.Custom))
        state = reduce(state, BrowserAction.ApplyDeveloperSettings)
        assertTrue(state.isDeveloperSettingsOpen)
        assertEquals("Enter a custom user agent.", state.userAgentError)
        state = reduce(state, BrowserAction.SetCustomUserAgent("LightCopy/8"))
        state = reduce(state, BrowserAction.ApplyDeveloperSettings)
        assertFalse(state.isDeveloperSettingsOpen)
        assertEquals("LightCopy/8", state.developerSettings.customUserAgent)
    }

    @Test fun `network panel lifecycle and entries are explicit`() {
        val open = reduce(BrowserShellState(isMenuOpen = true), BrowserAction.OpenNetwork)
        assertTrue(open.isNetworkOpen)
        assertFalse(open.isMenuOpen)
        assertFalse(reduce(open, BrowserAction.CloseNetwork).isNetworkOpen)
    }
}
