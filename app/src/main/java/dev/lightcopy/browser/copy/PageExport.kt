package dev.lightcopy.browser.copy

enum class PageExportKind(val label: String, val extractionKind: ExtractionKind, val mimeType: String, val extension: String) {
    OriginalHtml("Original HTML", ExtractionKind.OriginalHtml, "text/html", "html"),
    RenderedHtml("Rendered HTML", ExtractionKind.RenderedDom, "text/html", "html"),
    VisibleText("Visible text", ExtractionKind.VisibleText, "text/plain", "txt"),
}

object ExportFileNames {
    fun page(title: String, kind: PageExportKind): String {
        val safe = title.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').take(60).ifEmpty { "page" }
        return "$safe-${kind.name.lowercase()}.${kind.extension}"
    }
}
