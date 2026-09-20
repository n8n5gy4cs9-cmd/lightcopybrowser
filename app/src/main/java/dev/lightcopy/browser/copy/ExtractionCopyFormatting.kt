package dev.lightcopy.browser.copy

import dev.lightcopy.browser.settings.CopyFormat

/** Renders link/image extractions as plain URLs or Markdown, driven by the preferred copy format. */
object ExtractionCopyFormatter {
    private const val PAIR_SEPARATOR = '\u0001'

    fun render(content: String, kind: ExtractionKind, format: CopyFormat): String = when (kind) {
        ExtractionKind.Links -> renderPairs(content, images = false, format)
        ExtractionKind.Images -> renderPairs(content, images = true, format)
        else -> content
    }

    fun renderPairs(content: String, images: Boolean, format: CopyFormat): String {
        val pairs = content.lineSequence().map { line ->
            val separator = line.indexOf(PAIR_SEPARATOR)
            if (separator < 0) line to "" else line.substring(0, separator) to line.substring(separator + 1)
        }.filter { it.first.isNotBlank() }.toList()
        return pairs.joinToString("\n") { (url, label) ->
            when (format) {
                CopyFormat.PlainText -> url
                CopyFormat.Markdown -> if (images) "![${label.ifBlank { "image" }}]($url)" else "[${label.ifBlank { url }}]($url)"
            }
        }
    }
}
