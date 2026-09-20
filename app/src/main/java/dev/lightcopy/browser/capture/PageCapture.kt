package dev.lightcopy.browser.capture

import android.graphics.Bitmap

const val MAX_CAPTURE_HEIGHT_PX = 12_000
const val MAX_CAPTURE_PIXELS = 20_000_000L

data class CaptureSize(val width: Int, val height: Int)

object CaptureLimits {
    fun validate(width: Int, height: Int): String? = when {
        width <= 0 || height <= 0 -> "The page has no capturable content."
        height > MAX_CAPTURE_HEIGHT_PX -> "Page is too tall to capture safely (limit: $MAX_CAPTURE_HEIGHT_PX px)."
        width.toLong() * height > MAX_CAPTURE_PIXELS -> "Page is too large to capture safely (limit: $MAX_CAPTURE_PIXELS pixels)."
        else -> null
    }
}

data class CaptureResult(val bitmap: Bitmap? = null, val error: String? = null)
