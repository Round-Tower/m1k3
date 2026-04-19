package app.m1k3.ai.domain.tools.services

/**
 * Native Qwen-style XML tool call:
 *
 *     <tool_call>
 *     <function=NAME>
 *     <parameter=KEY>
 *     VALUE
 *     </parameter>
 *     </function>
 *     </tool_call>
 *
 * Fallback for when `common_chat_parse` misses (e.g. under LENIENT PEG
 * mode the content tag can eat the whole output). Models trained on this
 * format — Qwen3.x, parts of the Mistral family — emit it naturally.
 *
 * MurphySig: kev+claude / confidence 0.85 / 2026-04-19
 * Rationale: the model is already emitting the format correctly; we just
 * need a tolerant extractor so Path C is useful today.
 */
data class ExtractedToolCall(
    val name: String,
    val arguments: Map<String, String>,
)

object QwenXmlToolCallExtractor {
    // Tolerant of extra whitespace around `function` / `parameter` keywords
    // that sub-word tokenizers sometimes introduce.
    private val blockRegex =
        Regex(
            "<tool_call>\\s*(.*?)\\s*</tool_call>",
            RegexOption.DOT_MATCHES_ALL,
        )

    private val functionRegex =
        Regex(
            "<\\s*function\\s*=\\s*([A-Za-z0-9_\\-]+)\\s*>\\s*(.*?)\\s*<\\s*/\\s*function\\s*>",
            RegexOption.DOT_MATCHES_ALL,
        )

    private val parameterRegex =
        Regex(
            "<\\s*parameter\\s*=\\s*([A-Za-z0-9_\\-]+)\\s*>\\s*(.*?)\\s*<\\s*/\\s*parameter\\s*>",
            RegexOption.DOT_MATCHES_ALL,
        )

    fun extract(raw: String): List<ExtractedToolCall> {
        if (!raw.contains("<tool_call>")) return emptyList()

        val out = mutableListOf<ExtractedToolCall>()
        for (blockMatch in blockRegex.findAll(raw)) {
            val inner = blockMatch.groupValues[1]
            val fn = functionRegex.find(inner) ?: continue
            val name = fn.groupValues[1]
            if (name.isBlank()) continue

            val body = fn.groupValues[2]
            val args = mutableMapOf<String, String>()
            for (p in parameterRegex.findAll(body)) {
                args[p.groupValues[1]] = p.groupValues[2].trim()
            }
            out += ExtractedToolCall(name = name, arguments = args)
        }
        return out
    }
}
