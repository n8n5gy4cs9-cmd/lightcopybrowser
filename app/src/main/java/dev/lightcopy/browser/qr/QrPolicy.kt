package dev.lightcopy.browser.qr

object QrPolicy {
    fun acceptedUrl(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.length !in 1..4096) return null
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
        return value.takeIf { uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() }
    }
}
