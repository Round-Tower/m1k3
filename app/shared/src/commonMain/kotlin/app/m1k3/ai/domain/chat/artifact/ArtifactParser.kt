package app.m1k3.ai.domain.chat.artifact

/**
 * ArtifactParser — extracts `<artifact>` blocks from model responses.
 *
 * The model uses `<artifact id="..." type="html">...</artifact>` to wrap
 * self-contained HTML documents. This parser splits a response into
 * interleaved text parts and artifact blocks.
 *
 * Usage:
 * ```kotlin
 * val result = ArtifactParser.parse(responseText)
 * // result.textParts[0] = "Here's your timer:\n\n"
 * // result.artifacts[0] = ArtifactData(id="timer", html="...")
 * // result.textParts[1] = "\n\nLet me know if you need changes."
 * ```
 *
 * Text parts always have one more element than artifacts:
 *   textParts.size == artifacts.size + 1
 */
object ArtifactParser {

    // Raw string: <artifact...>...</artifact> with lazy inner match
    private val ARTIFACT_REGEX = Regex(
        "<artifact([^>]*)>([\\s\\S]*?)</artifact>",
        RegexOption.IGNORE_CASE
    )
    // Attribute extractors — escaped regular strings
    private val ATTR_ID_REGEX = Regex("id=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val ATTR_TYPE_REGEX = Regex("type=\"([^\"]*)\"", RegexOption.IGNORE_CASE)

    data class ParseResult(
        /** Text segments between/around artifacts. Always size == artifacts.size + 1. */
        val textParts: List<String>,
        val artifacts: List<ArtifactData>
    )

    fun parse(text: String): ParseResult {
        val matches = ARTIFACT_REGEX.findAll(text).toList()
        if (matches.isEmpty()) return ParseResult(listOf(text), emptyList())

        val textParts = mutableListOf<String>()
        val artifacts = mutableListOf<ArtifactData>()
        var cursor = 0

        matches.forEach { match ->
            textParts.add(text.substring(cursor, match.range.first))
            cursor = match.range.last + 1

            val attrs = match.groupValues[1]
            val html = match.groupValues[2].trim()
            val id = ATTR_ID_REGEX.find(attrs)?.groupValues?.get(1)
                ?: "artifact-${artifacts.size}"
            val type = ATTR_TYPE_REGEX.find(attrs)?.groupValues?.get(1) ?: "html"

            artifacts.add(ArtifactData(id = id, type = type, html = html))
        }
        textParts.add(text.substring(cursor))

        return ParseResult(textParts, artifacts)
    }

    /** Quick check without full parse — use to gate rendering. */
    fun hasArtifacts(text: String): Boolean = ARTIFACT_REGEX.containsMatchIn(text)
}
