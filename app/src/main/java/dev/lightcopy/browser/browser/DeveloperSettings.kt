package dev.lightcopy.browser.browser

enum class UserAgentMode(val label: String) { Mobile("Mobile"), Desktop("Desktop"), Custom("Custom") }

data class DeveloperSettings(
    val javaScriptEnabled: Boolean = true,
    val adBlockingEnabled: Boolean = false,
    val imagesEnabled: Boolean = true,
    val userAgentMode: UserAgentMode = UserAgentMode.Mobile,
    val customUserAgent: String = "",
)

object UserAgentValidator {
    const val maxLength = 512
    fun error(value: String): String? = when {
        value.isBlank() -> "Enter a custom user agent."
        value.length > maxLength -> "User agent must be $maxLength characters or fewer."
        value.any { it.code < 0x20 || it.code == 0x7f } -> "User agent cannot contain control characters."
        else -> null
    }
}

const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
