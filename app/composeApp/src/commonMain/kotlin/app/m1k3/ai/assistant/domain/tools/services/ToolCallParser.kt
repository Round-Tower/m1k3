package app.m1k3.ai.assistant.domain.tools.services

import app.m1k3.ai.assistant.domain.tools.ToolCall

/**
 * Tool Call Parser - Extracts tool calls from LLM output
 *
 * Domain service interface - Pure Kotlin, no platform dependencies.
 *
 * Parses LLM output text to find and extract tool invocations.
 * Supports multiple formats:
 * - JSON: `{"tool": "id", "args": {...}}`
 * - XML-style: `<tool_call>{"tool": "id"}</tool_call>`
 *
 * **Usage:**
 * ```kotlin
 * val parser: ToolCallParser = DefaultToolCallParser()
 * val output = "Let me help. {\"tool\": \"get_battery\"}"
 *
 * if (parser.hasToolCalls(output)) {
 *     val calls = parser.parse(output)
 *     val plainText = parser.extractPlainText(output)
 * }
 * ```
 */
interface ToolCallParser {
    /**
     * Parse tool calls from LLM output text
     *
     * Extracts all tool invocations from the output, supporting
     * both JSON and XML-style formats.
     *
     * @param output Raw LLM output
     * @return List of parsed tool calls (may be empty if none found)
     */
    fun parse(output: String): List<ToolCall>

    /**
     * Check if output contains any tool calls
     *
     * Quick check without full parsing. Useful for branching logic.
     *
     * @param output Raw LLM output
     * @return true if at least one tool call is present
     */
    fun hasToolCalls(output: String): Boolean

    /**
     * Extract plain text from output (remove tool call markup)
     *
     * Returns the output with all tool call JSON/XML removed,
     * preserving any natural language content.
     *
     * @param output Raw LLM output
     * @return Text content without tool call markup
     */
    fun extractPlainText(output: String): String
}
