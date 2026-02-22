package app.m1k3.ai.domain.usecases.chat

/**
 * LLM Output Processor Interface - Abstraction for processing LLM output.
 *
 * Interface for parsing and executing tool calls from LLM output.
 * Allows for testing ChatWithToolsUseCase without full tool infrastructure.
 *
 * Domain interface - Pure Kotlin, no platform dependencies.
 *
 * @see ProcessLlmOutputUseCase for the default implementation
 * @see ProcessedOutput for the result structure
 */
interface LlmOutputProcessor {
    /**
     * Process LLM output for tool calls.
     *
     * @param llmOutput Raw text output from the LLM
     * @param confirmedToolIds Set of tool IDs that have been confirmed by user
     * @return ProcessedOutput containing either text only or tool results
     */
    suspend fun execute(llmOutput: String, confirmedToolIds: Set<String> = emptySet()): ProcessedOutput

    /**
     * Check if LLM output contains any tool calls without executing them.
     *
     * @param llmOutput Raw text output from the LLM
     * @return true if tool calls are present
     */
    fun hasToolCalls(llmOutput: String): Boolean

    /**
     * Extract just the plain text from LLM output (stripping tool calls).
     *
     * @param llmOutput Raw text output from the LLM
     * @return Plain text with tool call syntax removed
     */
    fun extractPlainText(llmOutput: String): String
}
