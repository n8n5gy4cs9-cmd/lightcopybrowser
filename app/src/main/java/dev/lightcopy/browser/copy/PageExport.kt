package dev.lightcopy.browser.copy

enum class PageExportKind(val label: String, val extractionKind: ExtractionKind, val mimeType: String, val extension: String) {
    OriginalHtml("Original HTML", ExtractionKind.OriginalHtml, "text/html", "html"),
    RenderedHtml("Rendered HTML", ExtractionKind.RenderedDom, "text/html", "html"),
    VisibleText("Visible text", ExtractionKind.VisibleText, "text/plain", "txt"),
}

object ExportFileNames {
    fun safeTitle(title: String): String = title.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').take(60).ifEmpty { "page" }
    fun page(title: String, kind: PageExportKind): String {
        val safe = safeTitle(title)
        return "$safe-${kind.name.lowercase()}.${kind.extension}"
    }
}
