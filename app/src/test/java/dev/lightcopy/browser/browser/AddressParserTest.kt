package dev.lightcopy.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressParserTest {
    @Test fun `hostname defaults to secure HTTP`() {
        assertEquals("https://example.com/path", AddressParser.parse(" example.com/path "))
    }

    @Test fun `explicit HTTP and HTTPS URLs are preserved`() {
        assertEquals("http://localhost:8080/test", AddressParser.parse("http://localhost:8080/test"))
        assertEquals("https://example.com?q=one", AddressParser.parse("HTTPS://example.com?q=one"))
    }

    @Test fun `queries are safely encoded`() {
        assertEquals("https://www.google.com/search?q=kotlin+webview%2Fcompose",
            AddressParser.parse("kotlin webview/compose"))
    }

    @Test fun `configured search engine is used for queries`() {
        assertEquals("https://duckduckgo.com/?q=kotlin+webview",
            AddressParser.parse("kotlin webview", dev.lightcopy.browser.settings.SearchEngine.DuckDuckGo))
        assertEquals("https://www.bing.com/search?q=kotlin",
            AddressParser.parse("kotlin", dev.lightcopy.browser.settings.SearchEngine.Bing))
    }

    @Test fun `unsupported schemes are searched instead of loaded`() {
        assertEquals("https://www.google.com/search?q=javascript%3Aalert%281%29",
            AddressParser.parse("javascript:alert(1)"))
    }

    @Test fun `international hostnames use ascii representation`() {
        assertEquals("https://xn--bcher-kva.example", AddressParser.parse("bücher.example"))
    }
}
