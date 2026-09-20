package dev.lightcopy.browser.downloads
import org.junit.Assert.*
import org.junit.Test
class DownloadPolicyTest { @Test fun rejectsInvalidSchemes(){ assertNull(DownloadPolicy.error(DownloadInput("https://example.com/a",null,null,null))); assertNotNull(DownloadPolicy.error(DownloadInput("javascript:alert(1)",null,null,null))) } }
