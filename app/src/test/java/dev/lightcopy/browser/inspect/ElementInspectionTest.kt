package dev.lightcopy.browser.inspect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementInspectionTest {
    @Test fun `decoder preserves inspection details`() {
        val callback = "\"{\\\"ok\\\":true,\\\"tag\\\":\\\"button\\\",\\\"id\\\":\\\"save\\\",\\\"classes\\\":\\\"primary wide\\\",\\\"selector\\\":\\\"#save\\\",\\\"html\\\":\\\"<button>Save</button>\\\"}\""

        val result = InspectionResultDecoder.decode(callback)

        assertEquals("button", result.inspection?.tag)
        assertEquals("save", result.inspection?.id)
        assertEquals("primary wide", result.inspection?.classes)
        assertEquals("#save", result.inspection?.selector)
        assertEquals("<button>Save</button>", result.inspection?.html)
    }

    @Test fun `selection script creates overlay and validates selector`() {
        val script = ElementInspectionScripts.selectAt(12.5f, 42f)
        assertTrue(script.contains("elementFromPoint(12.5,42.0)"))
        assertTrue(script.contains("querySelectorAll"))
        assertTrue(script.contains("__lightcopy_selection_overlay"))
        assertTrue(script.contains("pointer-events:none"))
        assertFalse(script.contains("addJavascriptInterface"))
    }
}
