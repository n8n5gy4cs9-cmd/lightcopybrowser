package dev.lightcopy.browser.pageinfo

import org.junit.Assert.*
import org.junit.Test

class PageInfoTest {
    @Test fun `decoder preserves metadata storage cookies and page signals`() {
        val json = """{"ok":true,"title":"Example","url":"https://example.test/x","origin":"https://example.test","viewport":"390 × 844 CSS px (3×)","meta":"description\u001fA page","og":"og:title\u001fCard","favicon":"https://example.test/icon.png","local":"theme\u001fdark","session":"draft\u001fyes"}"""
        val encoded = quoteForCallback(json)
        val info = requireNotNull(PageInfoDecoder.decode(encoded, SslState.Secure, 420, "sid=abc; mode=dev").info)
        assertEquals("Example", info.title)
        assertEquals(420L, info.loadTimeMillis)
        assertEquals(NamedValue("description", "A page"), info.meta.single())
        assertEquals(NamedValue("og:title", "Card"), info.openGraph.single())
        assertEquals(listOf("mode", "sid"), info.cookies.map { it.name })
        assertEquals("dark", info.localStorage.single().value)
        assertEquals("yes", info.sessionStorage.single().value)
    }

    @Test fun `clear script checks origin before scoped operation`() {
        val script = PageInfoScripts.clear(StorageKind.LocalStorage, "https://example.test")
        assertTrue(script.contains("location.origin!=='https://example.test'"))
        assertTrue(script.contains("localStorage.clear()"))
        assertFalse(script.contains("sessionStorage.clear()"))
    }

    @Test fun `formatter includes every page info group`() {
        val info = PageInfo("T","https://x","https://x",SslState.Secure,12,"10 × 20",listOf(NamedValue("description","d")),listOf(NamedValue("og:title","o")),"/favicon.ico",listOf(NamedValue("c","1")),listOf(NamedValue("l","2")),listOf(NamedValue("s","3")))
        val text = PageInfoFormatter.format(info)
        listOf("Title: T","SSL: Secure","Load time: 12 ms","[Meta]","[Open Graph]","[Cookies]","[localStorage]","[sessionStorage]").forEach { assertTrue(text.contains(it)) }
    }

    private fun quoteForCallback(value: String): String = buildString {
        append('"'); value.forEach { when(it) { '"' -> append("\\\""); '\\' -> append("\\\\"); else -> append(it) } }; append('"')
    }
}
