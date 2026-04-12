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
        // Matches JSON objects with "tool", "tool_id", or "name" key
        // Small models are creative with key names — accept all common variants
        private val JSON_TOOL_PATTERN = Regex(
            """\{\s*"(?:tool|tool_id|name)"\s*:\s*"([^"]+)"(?:\s*,\s*"args"\s*:\s*(\{[^}]*\}))?\s*\}"""
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

        // Normalize tokenization artifacts: small models insert spaces around
        // JSON structural chars. " tool " → "tool", " get _battery " → "get_battery"
        val normalized = normalizeTokenSpaces(output)

        val calls = mutableListOf<ToolCall>()

        // First, try to find XML-style tool calls
        XML_TOOL_PATTERN.findAll(normalized).forEach { match ->
            val innerJson = match.groupValues[1]
            parseJsonToolCall(innerJson)?.let { calls.add(it) }
        }

        // Then find standalone JSON tool calls (not inside XML tags)
        val cleanedOutput = XML_TOOL_PATTERN.replace(normalized, "")
        JSON_TOOL_PATTERN.findAll(cleanedOutput).forEach { match ->
            parseFromMatch(match)?.let { calls.add(it) }
        }

        return calls
    }

    override fun hasToolCalls(output: String): Boolean {
        if (output.isBlank()) return false
        val normalized = normalizeTokenSpaces(output)

        // Check for XML-style first
        if (XML_TOOL_PATTERN.containsMatchIn(normalized)) {
            val match = XML_TOOL_PATTERN.find(normalized)
            if (match != null) {
                val inner = match.groupValues[1]
                if (JSON_TOOL_PATTERN.containsMatchIn(inner)) return true
            }
        }

        // Check for standalone JSON
        return JSON_TOOL_PATTERN.containsMatchIn(normalized)
    }

    override fun extractPlainText(output: String): String {
        var result = normalizeTokenSpaces(output)

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
     * Normalize tokenization artifacts from small LLMs.
     *
     * Qwen and other small models tokenize with spaces around JSON chars:
     *   " tool " → "tool"
     *   " get _battery _level " → "get_battery_level"
     *   {" tool ": " id "} → {"tool": "id"}
     *
     * This normalizes within JSON/tool_call regions only.
     */
    private fun normalizeTokenSpaces(output: String): String {
        // Remove spaces around underscores: " get _battery _level " → "get_battery_level"
        var result = output.replace(Regex("""\s*_\s*"""), "_")
        // Remove spaces inside quoted strings adjacent to quotes: " tool " → "tool"
        result = result.replace(Regex(""""\s+"""), "\"")
        result = result.replace(Regex("""\s+""""), "\"")
        // Remove space between < and tag names: < tool_call > → <tool_call>
        result = result.replace(Regex("""<\s+"""), "<")
        result = result.replace(Regex("""\s+>"""), ">")
        // Fix dropped underscores in tool_call tags: <toolcall> → <tool_call>
        result = result.replace(Regex("""</?toolcall>""", RegexOption.IGNORE_CASE)) { match ->
            if (match.value.startsWith("</")) "</tool_call>" else "<tool_call>"
        }
        // Fix dropped underscores in known tool IDs within JSON strings
        result = normalizeToolIds(result)
        return result
    }

    /**
     * Fix dropped underscores in known tool ID patterns.
     *
     * Gemma and other models sometimes drop underscores:
     * "websearch" → "web_search", "getbattery" → "get_battery", etc.
     *
     * Uses a lookup map of known concatenated IDs to avoid false positives
     * on normal English words like "setting" or "toggle".
     */
    private fun normalizeToolIds(output: String): String {
        var result = output
        KNOWN_COLLAPSED_IDS.forEach { (collapsed, correct) ->
            result = result.replace("\"$collapsed\"", "\"$correct\"")
        }
        return result
    }

    /**
     * Map of known tool IDs with underscores removed → correct form.
     * Only exact matches inside quotes are replaced.
     */
    private val KNOWN_COLLAPSED_IDS = mapOf(
        "websearch" to "web_search",
        "getbattery" to "get_battery",
        "getbatterylevel" to "get_battery_level",
        "getcurrenttime" to "get_current_time",
        "gettime" to "get_current_time",
        "getvolume" to "get_volume",
        "setvolume" to "set_volume",
        "setalarm" to "set_alarm",
        "settimer" to "set_timer",
        "toggleflashlight" to "toggle_flashlight",
        "opencamera" to "open_camera",
        "openbrowser" to "open_browser",
        "opensettings" to "open_settings",
        "openmaps" to "open_maps",
        "getnotifications" to "get_notifications",
        "gethealth" to "get_health",
        "getscreentime" to "get_screen_time",
        "getscreenttime" to "get_screen_time"
    )

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
