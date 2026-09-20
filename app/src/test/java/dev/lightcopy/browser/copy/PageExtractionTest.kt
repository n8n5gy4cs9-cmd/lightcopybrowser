package dev.lightcopy.browser.copy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageExtractionTest {
    @Test fun `decoder preserves html whitespace and escapes`() {
        val callbackValue = "\"{\\\"ok\\\":true,\\\"value\\\":\\\"<p>one\\\\ntwo &amp; \\\\\\\"quoted\\\\\\\"</p>\\\"}\""

        val result = ExtractionResultDecoder.decode(ExtractionKind.OriginalHtml, callbackValue)

        assertTrue(result.isSuccess)
        assertEquals("<p>one\ntwo &amp; \"quoted\"</p>", result.content)
    }

    @Test fun `decoder exposes a safe script error`() {
        val callbackValue = "\"{\\\"ok\\\":false,\\\"error\\\":\\\"Original HTML is unavailable.\\\"}\""
        val result = ExtractionResultDecoder.decode(ExtractionKind.OriginalHtml, callbackValue)
        assertFalse(result.isSuccess)
        assertEquals("Original HTML is unavailable.", result.error)
    }

    @Test fun `scripts use callbacks without a javascript interface`() {
        ExtractionKind.entries.forEach { kind ->
            val script = PageExtractionScripts.forKind(kind)
            assertTrue(script.contains("JSON.stringify"))
            assertFalse(script.contains("addJavascriptInterface"))
        }
        assertTrue(PageExtractionScripts.forKind(ExtractionKind.RenderedDom).contains("outerHTML"))
        assertTrue(PageExtractionScripts.forKind(ExtractionKind.VisibleText).contains("innerText"))
        assertTrue(PageExtractionScripts.forKind(ExtractionKind.SelectedHtml).contains("querySelector(selector)"))
        assertTrue(PageExtractionScripts.forKind(ExtractionKind.Markdown).contains("document.body"))
    }
}
