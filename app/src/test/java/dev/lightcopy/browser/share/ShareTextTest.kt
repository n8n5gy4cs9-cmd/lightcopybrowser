package dev.lightcopy.browser.share
import org.junit.Assert.*
import org.junit.Test
class ShareTextTest { @Test fun formatsOnlyWebPages(){ assertEquals("Title\nhttps://example.com",ShareText.value("Title","https://example.com")); assertNull(ShareText.value("x","file:///x")) } }
