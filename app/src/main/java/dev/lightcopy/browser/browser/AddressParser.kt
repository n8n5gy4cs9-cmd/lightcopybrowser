package dev.lightcopy.browser.browser

import java.net.IDN
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import dev.lightcopy.browser.settings.SearchEngine

object AddressParser {
    fun parse(rawValue: String, searchEngine: SearchEngine = SearchEngine.Google): String {
        val value = rawValue.trim()
        if (value.isEmpty()) return "about:blank"
        val explicit = runCatching { URI(value) }.getOrNull()
        if (explicit?.scheme.equals("http", true) || explicit?.scheme.equals("https", true)) {
            return normalizeHttpUri(explicit!!, searchEngine)
        }
        if (looksLikeHost(value)) return normalizeHttpUri(URI("https://$value"), searchEngine)
        return searchEngine.searchUrl + URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    private fun looksLikeHost(value: String): Boolean {
        if (value.any(Char::isWhitespace) || value.contains("://")) return false
        val host = value.substringBefore('/').substringBefore(':')
        return host.equals("localhost", true) || host.contains('.') ||
            host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))
    }

    private fun normalizeHttpUri(uri: URI, searchEngine: SearchEngine): String {
        val host = uri.host ?: uri.rawAuthority
            ?.substringAfterLast('@')
            ?.substringBeforeLast(':')
            ?.takeIf(String::isNotBlank)
            ?: return searchEngine.searchUrl + URLEncoder.encode(uri.toString(), StandardCharsets.UTF_8.name())
        return URI(uri.scheme.lowercase(), uri.userInfo, IDN.toASCII(host), uri.port,
            uri.path.ifEmpty { null }, uri.query, uri.fragment).toASCIIString()
    }
}
