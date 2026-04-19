package app.m1k3.ai.domain.tools.services

import app.m1k3.ai.domain.tools.ToolCall

/**
 * Default Tool Call Parser — extracts tool calls from LLM output.
 *
 * Input is expected to be well-formed: llama.cpp's lazy grammar sampler
 * (see [ToolCallGrammarBuilder]) constrains the model at the token level
 * so `<tool_call>...</tool_call>` blocks are guaranteed canonical JSON.
 *
 * No normalization, no quirk-fixing, no fallback repair — if the model
 * emits something the grammar didn't allow, the regex won't match and
 * we return an empty list. That's the contract.
 *
 * ## Supported formats
 * - XML-wrapped: `<tool_call>{"tool":"id","args":{...}}</tool_call>`
 * - Bare JSON:   `{"tool":"id","args":{...}}`
 */
class DefaultToolCallParser : ToolCallParser {
    companion object {
        private val JSON_TOOL_PATTERN =
            Regex(
                """\{\s*"tool"\s*:\s*"([^"]+)"(?:\s*,\s*"args"\s*:\s*(\{[^}]*\}))?\s*\}""",
            )

        private val XML_TOOL_PATTERN =
            Regex(
                """<tool_call>(.*?)</tool_call>""",
                RegexOption.DOT_MATCHES_ALL,
            )

        // Unquoted value: number (with optional sign + fractional part) or boolean.
        // Must mirror ToolCallGrammarBuilder's `number` / `boolean` rules — if that
        // grammar broadens, this pattern broadens with it.
        private val ARGS_PATTERN =
            Regex(
                """"([^"]+)"\s*:\s*(?:"([^"]*)"|(-?\d+(?:\.\d+)?|true|false))""",
            )
    }

    override fun parse(output: String): List<ToolCall> {
        if (output.isBlank()) return emptyList()

        val calls = mutableListOf<ToolCall>()

        XML_TOOL_PATTERN.findAll(output).forEach { match ->
            JSON_TOOL_PATTERN.find(match.groupValues[1])?.let { inner ->
                calls += toToolCall(inner)
            }
        }

        val outsideXml = XML_TOOL_PATTERN.replace(output, "")
        JSON_TOOL_PATTERN.findAll(outsideXml).forEach { match ->
            calls += toToolCall(match)
        }

        return calls
    }

    override fun hasToolCalls(output: String): Boolean {
        if (output.isBlank()) return false
        return JSON_TOOL_PATTERN.containsMatchIn(output)
    }

    override fun extractPlainText(output: String): String =
        XML_TOOL_PATTERN
            .replace(output, "")
            .let { JSON_TOOL_PATTERN.replace(it, "") }
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

    private fun toToolCall(match: MatchResult): ToolCall =
        ToolCall(
            toolId = match.groupValues[1],
            arguments = parseArgs(match.groupValues.getOrNull(2) ?: ""),
            rawText = match.value,
        )

    private fun parseArgs(argsJson: String): Map<String, String> {
        if (argsJson.isBlank()) return emptyMap()
        return ARGS_PATTERN.findAll(argsJson).associate { match ->
            match.groupValues[1] to match.groupValues[2].ifBlank { match.groupValues[3] }
        }
    }
}
