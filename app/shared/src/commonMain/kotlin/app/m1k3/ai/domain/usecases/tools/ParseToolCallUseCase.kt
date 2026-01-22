package app.m1k3.ai.domain.usecases.tools

import app.m1k3.ai.domain.tools.ToolCall
import app.m1k3.ai.domain.tools.services.ToolCallParser

/**
 * Parse Tool Call Use Case - Extracts tool calls from LLM output
 *
 * Domain use case - Pure Kotlin, no platform dependencies.
 *
 * Wraps the ToolCallParser to provide a clean use case interface
 * that returns both parsed tool calls and remaining plain text.
 *
 * **Usage:**
 * ```kotlin
 * val useCase = ParseToolCallUseCase(parser)
 *
 * val output = "Let me check. {\"tool\": \"get_battery\"} Here's the result."
 * val result = useCase.execute(output)
 *
 * if (result.hasToolCalls) {
 *     result.toolCalls.forEach { call ->
 *         // Execute each tool call
 *     }
 *     // Show remaining text to user
 *     println(result.plainText)
 * }
 * ```
 */
class ParseToolCallUseCase(
    private val parser: ToolCallParser
) {
    /**
     * Result of parsing LLM output
     *
     * @property toolCalls List of extracted tool calls (may be empty)
     * @property plainText Output with tool calls removed
     * @property hasToolCalls Whether any tool calls were found
     */
    data class ParseResult(
        val toolCalls: List<ToolCall>,
        val plainText: String,
        val hasToolCalls: Boolean
    )

    /**
     * Parse LLM output for tool calls
     *
     * @param llmOutput Raw output from the LLM
     * @return ParseResult with tool calls and plain text
     */
    fun execute(llmOutput: String): ParseResult {
        val calls = parser.parse(llmOutput)
        val plainText = parser.extractPlainText(llmOutput)

        return ParseResult(
            toolCalls = calls,
            plainText = plainText,
            hasToolCalls = calls.isNotEmpty()
        )
    }
}
