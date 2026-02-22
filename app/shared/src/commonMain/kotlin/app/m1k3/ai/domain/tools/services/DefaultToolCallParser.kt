package app.m1k3.ai.domain.tools.services

import app.m1k3.ai.domain.tools.ToolCall

/**
 * Default Tool Call Parser - Extracts tool calls from LLM output
 *
 * Pure Kotlin implementation with no external dependencies.
 * Uses regex-based parsing for tool call detection.
 *
 * **Supported Formats:**
 * 1. JSON: `{"tool": "id", "args": {"key": "value"}}`
 * 2. XML-style: `<tool_call>{"tool": "id"}</tool_call>`
 *
 * **Design Notes:**
 * - Handles both string and primitive values in args
 * - Gracefully handles malformed JSON
 * - Preserves raw text for debugging
 */
class DefaultToolCallParser : ToolCallParser {

    companion object {
        // Matches JSON objects with "tool" key
        // Captures the entire JSON object
        private val JSON_TOOL_PATTERN = Regex(
            """\{\s*"tool"\s*:\s*"([^"]+)"(?:\s*,\s*"args"\s*:\s*(\{[^}]*\}))?\s*\}"""
        )

        // Matches <tool_call>...</tool_call> tags
        private val XML_TOOL_PATTERN = Regex(
            """<tool_call>(.*?)</tool_call>""",
            RegexOption.DOT_MATCHES_ALL
        )

        // Matches key-value pairs in args object
        // Handles both "value" and value (unquoted for numbers/booleans)
        private val ARGS_PATTERN = Regex(
            """"([^"]+)"\s*:\s*(?:"([^"]*)"|(\w+))"""
        )
    }

    override fun parse(output: String): List<ToolCall> {
        if (output.isBlank()) return emptyList()

        val calls = mutableListOf<ToolCall>()

        // First, try to find XML-style tool calls
        XML_TOOL_PATTERN.findAll(output).forEach { match ->
            val innerJson = match.groupValues[1]
            parseJsonToolCall(innerJson)?.let { calls.add(it) }
        }

        // Then find standalone JSON tool calls (not inside XML tags)
        val cleanedOutput = XML_TOOL_PATTERN.replace(output, "")
        JSON_TOOL_PATTERN.findAll(cleanedOutput).forEach { match ->
            parseFromMatch(match)?.let { calls.add(it) }
        }

        return calls
    }

    override fun hasToolCalls(output: String): Boolean {
        if (output.isBlank()) return false

        // Check for XML-style first
        if (XML_TOOL_PATTERN.containsMatchIn(output)) {
            // Verify it contains valid tool JSON inside
            val match = XML_TOOL_PATTERN.find(output)
            if (match != null) {
                val inner = match.groupValues[1]
                if (JSON_TOOL_PATTERN.containsMatchIn(inner)) return true
            }
        }

        // Check for standalone JSON
        return JSON_TOOL_PATTERN.containsMatchIn(output)
    }

    override fun extractPlainText(output: String): String {
        var result = output

        // Remove XML-style tool calls
        result = XML_TOOL_PATTERN.replace(result, "")

        // Remove JSON tool calls
        result = JSON_TOOL_PATTERN.replace(result, "")

        // Clean up extra whitespace
        return result
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    /**
     * Parse a JSON tool call from regex match
     */
    private fun parseFromMatch(match: MatchResult): ToolCall? {
        val toolId = match.groupValues[1]
        val argsJson = match.groupValues.getOrNull(2) ?: ""

        val arguments = parseArgs(argsJson)

        return ToolCall(
            toolId = toolId,
            arguments = arguments,
            rawText = match.value
        )
    }

    /**
     * Parse a JSON tool call from raw JSON string
     */
    private fun parseJsonToolCall(json: String): ToolCall? {
        val match = JSON_TOOL_PATTERN.find(json) ?: return null
        return parseFromMatch(match)
    }

    /**
     * Parse arguments from args JSON object
     *
     * Handles:
     * - String values: "key": "value"
     * - Number values: "key": 123 → "123"
     * - Boolean values: "key": true → "true"
     */
    private fun parseArgs(argsJson: String): Map<String, String> {
        if (argsJson.isBlank()) return emptyMap()

        val args = mutableMapOf<String, String>()

        ARGS_PATTERN.findAll(argsJson).forEach { match ->
            val key = match.groupValues[1]
            // Group 2 is quoted string value, Group 3 is unquoted (number/boolean)
            val value = match.groupValues[2].ifBlank { match.groupValues[3] }
            args[key] = value
        }

        return args
    }
}
