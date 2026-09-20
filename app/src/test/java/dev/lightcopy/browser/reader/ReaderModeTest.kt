package dev.lightcopy.browser.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderModeTest {
    @Test fun decodesReaderPayload() {
        val encoded = "\"{\\\"ok\\\":true,\\\"value\\\":\\\"{\\\\\\\"title\\\\\\\":\\\\\\\"Story\\\\\\\",\\\\\\\"byline\\\\\\\":\\\\\\\"Ada\\\\\\\",\\\\\\\"text\\\\\\\":\\\\\\\"First\\\\nSecond\\\\\\\"}\\\"}\""
        val content = ReaderResultDecoder.decode(encoded).content
        assertEquals("Story", content?.title)
        assertEquals("Ada", content?.byline)
        assertEquals("First\nSecond", content?.text)
    }

    @Test fun preservesGracefulScriptFailure() {
        val encoded = "\"{\\\"ok\\\":false,\\\"error\\\":\\\"Try Visible text instead.\\\"}\""
        assertEquals("Try Visible text instead.", ReaderResultDecoder.decode(encoded).error)
    }
}
