package app.m1k3.ai.domain.tools.services

/**
 * Parses DuckDuckGo HTML search results (https://html.duckduckgo.com/html/?q=...).
 *
 * Used as the fallback path in WebSearchExecutor when the Instant Answer API
 * returns no abstract — DDG's Instant Answer only covers knowledge-graph style
 * queries (Wikipedia entities), so weather/news/current-events queries get
 * nothing. The HTML endpoint returns real organic results for everything.
 *
 * Ads are dropped (their URLs resolve to `duckduckgo.com/y.js`); only organic
 * entries reach the LLM so it doesn't paraphrase a promoted link as truth.
 */
object DuckDuckGoHtmlParser {
    data class SearchResult(
        val title: String,
        val url: String,
        val snippet: String,
    )

    private val titleRegex =
        Regex(
            """<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL,
        )
    private val snippetRegex =
        Regex(
            """<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL,
        )
    private val uddgRegex = Regex("""[?&]uddg=([^&]+)""")

    fun parse(
        html: String,
        maxResults: Int,
    ): List<SearchResult> {
        if (html.isBlank()) return emptyList()

        val snippets =
            snippetRegex
                .findAll(html)
                .map { cleanText(it.groupValues[1]) }
                .toList()

        val results = mutableListOf<SearchResult>()
        var index = 0
        for (match in titleRegex.findAll(html)) {
            val rawHref = match.groupValues[1]
            val decodedUrl = extractUddgUrl(rawHref) ?: continue
            if (isAdUrl(decodedUrl)) continue

            val title = cleanText(match.groupValues[2])
            if (title.isEmpty()) continue

            val snippet = snippets.getOrNull(index) ?: ""
            results += SearchResult(title = title, url = decodedUrl, snippet = snippet)
            index++
            if (results.size >= maxResults) break
        }
        return results
    }

    /**
     * Render results as a compact block for the LLM. Numbered list with title,
     * URL, and snippet per entry — shape chosen so small models can cite a
     * specific hit ("per result 2, ...") without us parsing it back out.
     */
    fun format(
        results: List<SearchResult>,
        query: String,
    ): String =
        buildString {
            append("Search results for \"")
            append(query)
            append("\":")
            results.forEachIndexed { i, r ->
                append('\n')
                append(i + 1)
                append(". ")
                append(r.title)
                append('\n')
                append("   ")
                append(r.url)
                if (r.snippet.isNotEmpty()) {
                    append('\n')
                    append("   ")
                    append(r.snippet)
                }
            }
        }

    private fun extractUddgUrl(href: String): String? {
        val match = uddgRegex.find(href) ?: return null
        val encoded = match.groupValues[1]
        return runCatching { percentDecode(encoded) }.getOrNull()
    }

    private fun isAdUrl(url: String): Boolean =
        url.contains("duckduckgo.com/y.js") ||
            url.contains("ad_provider=") ||
            url.contains("ad_domain=")

    private fun cleanText(raw: String): String =
        decodeHtmlEntities(
            raw.replace(Regex("<[^>]+>"), "").trim(),
        )

    private fun decodeHtmlEntities(s: String): String {
        if (!s.contains('&')) return s
        val numeric = Regex("""&#(x?)([0-9a-fA-F]+);""")
        val named =
            mapOf(
                "&amp;" to "&",
                "&quot;" to "\"",
                "&apos;" to "'",
                "&lt;" to "<",
                "&gt;" to ">",
                "&nbsp;" to " ",
            )
        var out = s
        for ((entity, replacement) in named) {
            out = out.replace(entity, replacement)
        }
        return numeric.replace(out) { m ->
            val hex = m.groupValues[1] == "x"
            val digits = m.groupValues[2]
            val code = digits.toIntOrNull(if (hex) 16 else 10) ?: return@replace m.value
            if (code in 0..0x10FFFF) {
                val codeInt = code
                buildString {
                    if (codeInt <= 0xFFFF) {
                        append(codeInt.toChar())
                    } else {
                        val offset = codeInt - 0x10000
                        append((0xD800 + (offset shr 10)).toChar())
                        append((0xDC00 + (offset and 0x3FF)).toChar())
                    }
                }
            } else {
                m.value
            }
        }
    }

    /**
     * Decode `%XX` escapes and `+` spaces as UTF-8. Kept here instead of
     * pulling in java.net.URLDecoder so the parser stays commonMain-safe.
     */
    private fun percentDecode(encoded: String): String {
        if (!encoded.contains('%') && !encoded.contains('+')) return encoded
        val out = mutableListOf<Byte>()
        var i = 0
        while (i < encoded.length) {
            val c = encoded[i]
            when {
                c == '+' -> {
                    out += ' '.code.toByte()
                    i++
                }

                c == '%' && i + 2 < encoded.length -> {
                    val hex = encoded.substring(i + 1, i + 3)
                    val byte = hex.toIntOrNull(16) ?: error("bad escape at $i: $hex")
                    out += byte.toByte()
                    i += 3
                }

                else -> {
                    val codePoint = c.code
                    when {
                        codePoint < 0x80 -> {
                            out += codePoint.toByte()
                        }

                        codePoint < 0x800 -> {
                            out += (0xC0 or (codePoint shr 6)).toByte()
                            out += (0x80 or (codePoint and 0x3F)).toByte()
                        }

                        else -> {
                            out += (0xE0 or (codePoint shr 12)).toByte()
                            out += (0x80 or ((codePoint shr 6) and 0x3F)).toByte()
                            out += (0x80 or (codePoint and 0x3F)).toByte()
                        }
                    }
                    i++
                }
            }
        }
        return out.toByteArray().decodeToString()
    }
}
