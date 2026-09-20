package dev.lightcopy.browser.qr
import org.junit.Assert.*
import org.junit.Test
class QrPolicyTest { @Test fun validatesScannedUrls(){ assertEquals("https://example.com/x",QrPolicy.acceptedUrl(" https://example.com/x ")); assertNull(QrPolicy.acceptedUrl("hello")); assertNull(QrPolicy.acceptedUrl("javascript:alert(1)")) } }
