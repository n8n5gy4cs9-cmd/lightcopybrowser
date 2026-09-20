package dev.lightcopy.browser.capture

import org.junit.Assert.*
import org.junit.Test

class PageCaptureTest {
    @Test fun acceptsPracticalPage() { assertNull(CaptureLimits.validate(1080, 8000)) }
    @Test fun rejectsTallPageBeforeAllocation() { assertTrue(CaptureLimits.validate(1080, 12001)!!.contains("too tall")) }
    @Test fun rejectsExcessivePixelCount() { assertTrue(CaptureLimits.validate(2000, 11000)!!.contains("too large")) }
    @Test fun rejectsEmptyCapture() { assertNotNull(CaptureLimits.validate(0, 200)) }
}
