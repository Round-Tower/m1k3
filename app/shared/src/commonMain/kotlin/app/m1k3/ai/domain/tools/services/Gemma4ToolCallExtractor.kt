package app.m1k3.ai.domain.tools.services

/**
 * Native Gemma 4 tool call:
 *
 *     <|tool_call>call:NAME{key:value,key:<|"|>string<|"|>}<tool_call|>
 *
 * Gemma's own tokenizer emits `<|"|>` as the string quote delimiter,
 * and bare identifiers/numbers/booleans as-is. Same fallback role as
 * [QwenXmlToolCallExtractor] — llama.cpp's PEG parser under LENIENT
 * mode swallows the whole block into `content`, so we pull the tool
 * call out ourselves.
 *
 * MurphySig: kev+claude / confidence 0.8 / 2026-04-19
 * Rationale: Gemma's format differs enough from Qwen's XML that a
 * shared extractor would be brittle — keep them separate, wire both.
 */
object Gemma4ToolCallExtractor {
    private val blockRegex =
        Regex(
            "<\\|tool_call\\|?>\\s*call:\\s*([A-Za-z0-9_\\-]+)\\s*\\{(.*?)\\}\\s*<\\|?tool_call\\|?>",
            RegexOption.DOT_MATCHES_ALL,
        )

    // Matches key:value where value is either <|"|>…<|"|> or a bare literal up to , or end.
    private val argRegex =
        Regex(
            "([A-Za-z0-9_\\-]+)\\s*:\\s*(?:<\\|\"\\|>(.*?)<\\|\"\\|>|([^,}]+?))(?=\\s*,|\\s*$)",
            RegexOption.DOT_MATCHES_ALL,
        )

    fun extract(raw: String): List<ExtractedToolCall> {
        if (!raw.contains("<|tool_call")) return emptyList()

        val out = mutableListOf<ExtractedToolCall>()
        for (block in blockRegex.findAll(raw)) {
            val name = block.groupValues[1]
            if (name.isBlank()) continue
            val argsBlob = block.groupValues[2]

            val args = mutableMapOf<String, String>()
            for (arg in argRegex.findAll(argsBlob)) {
                val key = arg.groupValues[1]
                val quoted = arg.groupValues[2]
                val bare = arg.groupValues[3]
                args[key] = if (quoted.isNotEmpty()) quoted else bare.trim()
            }
            out += ExtractedToolCall(name = name, arguments = args)
        }
        return out
    }
}
