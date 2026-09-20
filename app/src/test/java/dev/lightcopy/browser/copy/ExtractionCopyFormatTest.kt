package dev.lightcopy.browser.copy

import dev.lightcopy.browser.settings.CopyFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtractionCopyFormatTest {
    private val linksContent = "https://a.test/docs\u0001Docs\nhttps://a.test/home\u0001\nhttps://b.test\n"

    @Test fun `links render as plain urls by default`() {
        val rendered = ExtractionCopyFormatter.renderPairs(linksContent, images = false, CopyFormat.PlainText)
        assertEquals("https://a.test/docs\nhttps://a.test/home\nhttps://b.test", rendered)
    }

    @Test fun `links render as markdown with link text`() {
        val rendered = ExtractionCopyFormatter.renderPairs(linksContent, images = false, CopyFormat.Markdown)
        assertEquals("[Docs](https://a.test/docs)\n[https://a.test/home](https://a.test/home)\n[https://b.test](https://b.test)", rendered)
    }

    @Test fun `images render as markdown images with alt text`() {
        val content = "https://a.test/pixel.png\u0001Pixel\nhttps://a.test/blank.png\u0001"
        val rendered = ExtractionCopyFormatter.renderPairs(content, images = true, CopyFormat.Markdown)
        assertEquals("![Pixel](https://a.test/pixel.png)\n![image](https://a.test/blank.png)", rendered)
    }

    @Test fun `lines without pair separator keep the raw value`() {
        val rendered = ExtractionCopyFormatter.renderPairs("https://a.test", images = false, CopyFormat.PlainText)
        assertEquals("https://a.test", rendered)
    }

    @Test fun `non list kinds pass through unchanged`() {
        assertEquals("<html></html>", ExtractionCopyFormatter.render("<html></html>", ExtractionKind.OriginalHtml, CopyFormat.Markdown))
    }

    @Test fun `blank lines and missing urls are dropped`() {
        assertEquals("", ExtractionCopyFormatter.renderPairs("\n\u0001label\n  \n", images = false, CopyFormat.PlainText))
    }
}
