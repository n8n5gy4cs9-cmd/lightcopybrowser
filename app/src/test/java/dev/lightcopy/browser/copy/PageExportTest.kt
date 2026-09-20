package dev.lightcopy.browser.copy

import org.junit.Assert.assertEquals
import org.junit.Test

class PageExportTest {
    @Test fun createsSafeBoundedFileName() {
        assertEquals("A-title-renderedhtml.html", ExportFileNames.page("  A title!? ", PageExportKind.RenderedHtml))
    }
    @Test fun textUsesTxtExtension() {
        assertEquals("page-visibletext.txt", ExportFileNames.page("", PageExportKind.VisibleText))
    }
}
