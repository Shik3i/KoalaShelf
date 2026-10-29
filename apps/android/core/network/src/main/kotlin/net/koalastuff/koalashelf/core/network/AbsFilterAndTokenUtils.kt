package net.koalastuff.koalashelf.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.util.Base64

const val MIN_SERVER_VERSION = "2.26.0"

enum class FilterGroup(val wireName: String) {
    PROGRESS("progress"),
    GENRES("genres"),
    NARRATORS("narrators"),
    TAGS("tags"),
    LANGUAGES("languages"),
    AUTHORS("authors"),
    SERIES("series"),
    PUBLISHERS("publishers"),
    PUBLISHED_DECADES("publishedDecades"),
}

private val jwtJson = Json { ignoreUnknownKeys = true }

/**
 * Trims whitespace and trailing slashes using a loop (matching `normalizeBaseUrl` in `client.ts`).
 */
fun normalizeBaseUrl(url: String): String {
    var s = url.trim()
    while (s.endsWith('/')) {
        s = s.substring(0, s.length - 1)
    }
    return s
}

/**
 * Checks if a host/URL points to a local loopback device (for dev or local testing).
 */
fun isLoopbackHost(hostOrUrl: String): Boolean {
    val clean = hostOrUrl.trim().lowercase().removePrefix("http://").removePrefix("https://").substringBefore('/').substringBefore(':')
    return clean == "localhost" || clean == "127.0.0.1" || clean == "::1" || clean == "10.0.2.2"
}

/**
 * Normalizes user input for server address: auto-prefixes https:// if no scheme is provided,
 * while allowing http:// only on local loopback. Matches web `normalizeServerInput`.
 */
fun normalizeServerInput(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""
    val hasScheme = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
    val withScheme = if (hasScheme) {
        trimmed
    } else {
        if (isLoopbackHost(trimmed)) "http://$trimmed" else "https://$trimmed"
    }
    return normalizeBaseUrl(withScheme)
}

/**
 * Extracts host label ("audio.example.org") from a server URL.
 */
fun serverLabel(baseUrl: String): String {
    val clean = normalizeBaseUrl(baseUrl)
    if (clean.isEmpty()) return ""
    return try {
        val uri = URI(clean)
        val host = uri.host ?: return clean
        if (uri.port != -1 && uri.port != 80 && uri.port != 443) "$host:${uri.port}" else host
    } catch (_: Exception) {
        clean
    }
}

/**
 * Semver comparison: returns true if `version` >= `min` (matching `isVersionAtLeast` in `client.ts`).
 */
fun isVersionAtLeast(version: String, min: String = MIN_SERVER_VERSION): Boolean {
    val a = version.removePrefix("v").removePrefix("V").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val b = min.removePrefix("v").removePrefix("V").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    for (i in 0 until 3) {
        val av = a.getOrElse(i) { 0 }
        val bv = b.getOrElse(i) { 0 }
        if (av != bv) return av > bv
    }
    return true
}

/**
 * Expiry (seconds since epoch) of a JWT access token, or null when it has none or is opaque.
 */
fun tokenExpiry(token: String): Long? {
    return try {
        val parts = token.split('.')
        if (parts.size < 2) return null
        val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
        val payloadStr = String(payloadBytes, Charsets.UTF_8)
        val obj = jwtJson.parseToJsonElement(payloadStr).jsonObject
        obj["exp"]?.jsonPrimitive?.longOrNull
    } catch (_: Exception) {
        null
    }
}

/**
 * Encodes an Audiobookshelf filter parameter: `<group>.<base64(utf8(value))>`
 * (matching `packages/abs-client/src/filter.ts`).
 */
fun encodeFilter(group: FilterGroup, value: String): String {
    val encoded = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    return "${group.wireName}.$encoded"
}
