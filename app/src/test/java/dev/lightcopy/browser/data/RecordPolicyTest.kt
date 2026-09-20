package dev.lightcopy.browser.data
import org.junit.Assert.*
import org.junit.Test
class RecordPolicyTest { @Test fun acceptsOnlyWebPages(){ assertTrue(RecordPolicy.isWebUrl("https://example.com")); assertFalse(RecordPolicy.isWebUrl("file:///secret")) } }
