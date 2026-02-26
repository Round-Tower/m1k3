package app.m1k3.ai.domain.activation

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * DeepLinkParser - Parse m1k3:// URIs into ActivationContext.
 *
 * Domain service - Pure Kotlin, no platform dependencies.
 *
 * Handles the `m1k3://activate` URI scheme used across all platforms
 * (iOS deep links, Android intents, widget taps, share extension routing).
 *
 * **Supported URI format:**
 * ```
 * m1k3://activate?source=widget&mode=reading&input=Hello&session=abc&mime=text%2Fplain
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val parser = DeepLinkParser()
 * val context = parser.parse("m1k3://activate?source=widget&mode=reading")
 * // context.source == ActivationSource.Widget
 * // context.mode == M1K3Mode.Reading
 * ```
 *
 * @see ActivationContext for the resulting activation payload
 */
class DeepLinkParser {

    companion object {
        private const val SCHEME = "m1k3"
        private const val HOST = "activate"
    }

    /**
     * Parse a m1k3:// URI into an ActivationContext.
     *
     * @param uri The full URI string (e.g. "m1k3://activate?source=widget")
     * @return ActivationContext if URI is valid, null if scheme or host is wrong
     */
    @OptIn(ExperimentalUuidApi::class)
    fun parse(uri: String): ActivationContext? {
        // Split scheme from rest: "m1k3" : "//activate?..."
        val schemeEnd = uri.indexOf("://")
        if (schemeEnd < 0) return null

        val scheme = uri.substring(0, schemeEnd)
        if (!scheme.equals(SCHEME, ignoreCase = true)) return null

        // Extract host and query
        val afterScheme = uri.substring(schemeEnd + 3) // skip "://"
        val queryStart = afterScheme.indexOf('?')
        val host = if (queryStart >= 0) afterScheme.substring(0, queryStart) else afterScheme

        if (!host.equals(HOST, ignoreCase = true)) return null

        // Parse query parameters
        val params = if (queryStart >= 0) {
            parseQueryParams(afterScheme.substring(queryStart + 1))
        } else {
            emptyMap()
        }

        return ActivationContext(
            source = ActivationSource.from(params["source"] ?: ""),
            mode = M1K3Mode.from(params["mode"] ?: ""),
            input = params["input"]?.urlDecode(),
            mimeType = params["mime"]?.urlDecode(),
            sessionId = params["session"] ?: Uuid.random().toString()
        )
    }

    /**
     * Parse query string into key-value pairs.
     *
     * Handles: "key1=value1&key2=value2"
     */
    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()

        return query.split("&")
            .mapNotNull { param ->
                val eqIdx = param.indexOf('=')
                if (eqIdx > 0) {
                    val key = param.substring(0, eqIdx)
                    val value = param.substring(eqIdx + 1)
                    key to value
                } else {
                    null
                }
            }
            .toMap()
    }

    /**
     * URL-decode a string.
     *
     * Handles %XX hex escapes and + as space.
     * Pure Kotlin implementation (no java.net.URLDecoder).
     */
    private fun String.urlDecode(): String {
        val result = StringBuilder(length)
        var i = 0
        while (i < length) {
            when (val c = this[i]) {
                '%' -> {
                    if (i + 2 < length) {
                        val hex = substring(i + 1, i + 3)
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            result.append(code.toChar())
                            i += 3
                            continue
                        }
                    }
                    result.append(c)
                    i++
                }
                '+' -> {
                    result.append(' ')
                    i++
                }
                else -> {
                    result.append(c)
                    i++
                }
            }
        }
        return result.toString()
    }
}
