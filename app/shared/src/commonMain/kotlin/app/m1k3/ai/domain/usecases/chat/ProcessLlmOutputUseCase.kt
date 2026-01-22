package app.m1k3.ai.domain.usecases.chat

import app.m1k3.ai.domain.tools.ToolResult
import app.m1k3.ai.domain.usecases.tools.ExecuteToolUseCase
import app.m1k3.ai.domain.usecases.tools.ParseToolCallUseCase

/**
 * Process LLM Output Use Case
 *
 * Orchestrates the parsing and execution of tool calls from LLM output.
 *
 * Flow:
 * 1. Parse LLM output for tool calls using ParseToolCallUseCase
 * 2. If no tool calls found, return TextOnly result
 * 3. Execute each tool call using ExecuteToolUseCase
 * 4. Return WithTools result containing all outcomes
 *
 * This use case is the primary integration point between the LLM generation
 * and the tool execution system. It handles:
 * - Parsing various tool call formats (JSON, XML-style)
 * - Sequential execution of multiple tool calls
 * - Aggregating results (success, failure, confirmation needed)
 * - Preserving plain text alongside tool results
 *
 * @property parseToolCallUseCase Parses tool calls from LLM output
 * @property executeToolUseCase Executes individual tool calls
 */
class ProcessLlmOutputUseCase(
    private val parseToolCallUseCase: ParseToolCallUseCase,
    private val executeToolUseCase: ExecuteToolUseCase
) {

    /**
     * Process LLM output for tool calls
     *
     * @param llmOutput Raw text output from the LLM
     * @param confirmedToolIds Set of tool IDs that have been confirmed by the user
     *                         (for tools requiring confirmation)
     * @return ProcessedOutput containing either text only or tool results
     */
    suspend fun execute(
        llmOutput: String,
        confirmedToolIds: Set<String> = emptySet()
    ): ProcessedOutput {
        // Step 1: Parse for tool calls
        val parseResult = parseToolCallUseCase.execute(llmOutput)

        // Step 2: If no tool calls, return text only
        if (parseResult.toolCalls.isEmpty()) {
            return ProcessedOutput.TextOnly(parseResult.plainText)
        }

        // Step 3: Execute each tool call
        val toolResults = mutableListOf<ToolResult>()

        for (toolCall in parseResult.toolCalls) {
            val isConfirmed = toolCall.toolId in confirmedToolIds
            val result = executeToolUseCase.execute(toolCall, confirmed = isConfirmed)
            toolResults.add(result)
        }

        // Step 4: Return combined result
        return ProcessedOutput.WithTools(
            plainText = parseResult.plainText,
            toolResults = toolResults
        )
    }

    /**
     * Check if LLM output contains any tool calls without executing them
     *
     * Useful for UI to determine if special handling is needed before
     * actually processing the output.
     *
     * @param llmOutput Raw text output from the LLM
     * @return true if tool calls are present
     */
    fun hasToolCalls(llmOutput: String): Boolean {
        return parseToolCallUseCase.execute(llmOutput).toolCalls.isNotEmpty()
    }

    /**
     * Extract just the plain text from LLM output (stripping tool calls)
     *
     * @param llmOutput Raw text output from the LLM
     * @return Plain text with tool call syntax removed
     */
    fun extractPlainText(llmOutput: String): String {
        return parseToolCallUseCase.execute(llmOutput).plainText
    }
}
