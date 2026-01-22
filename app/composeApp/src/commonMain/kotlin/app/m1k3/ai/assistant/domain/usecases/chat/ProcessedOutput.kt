package app.m1k3.ai.assistant.domain.usecases.chat

import app.m1k3.ai.assistant.domain.tools.ToolResult

/**
 * Processed Output - Result of processing LLM output for tool calls
 *
 * Represents the outcome of parsing and executing any tool calls
 * found in an LLM's response.
 *
 * Two variants:
 * - TextOnly: No tool calls, just plain text response
 * - WithTools: Contains tool execution results alongside optional text
 */
sealed class ProcessedOutput {

    /**
     * Whether any tool calls were found and executed
     */
    abstract val hasToolResults: Boolean

    /**
     * Text-only response - no tool calls found
     *
     * @property text The plain text from the LLM response
     */
    data class TextOnly(
        val text: String
    ) : ProcessedOutput() {
        override val hasToolResults: Boolean = false
    }

    /**
     * Response with tool execution results
     *
     * @property plainText Any text that appeared alongside the tool calls
     * @property toolResults Results from executing each tool call (in order)
     */
    data class WithTools(
        val plainText: String,
        val toolResults: List<ToolResult>
    ) : ProcessedOutput() {
        override val hasToolResults: Boolean = true

        /**
         * Whether all tool executions succeeded
         */
        val allToolsSucceeded: Boolean
            get() = toolResults.all { it.isSuccess }

        /**
         * Whether any tool requires user confirmation
         */
        val hasPendingConfirmations: Boolean
            get() = toolResults.any { it.requiresConfirmation }

        /**
         * Get only the successful results
         */
        val successfulResults: List<ToolResult.Success>
            get() = toolResults.filterIsInstance<ToolResult.Success>()

        /**
         * Get only the failed results
         */
        val failedResults: List<ToolResult.Failure>
            get() = toolResults.filterIsInstance<ToolResult.Failure>()

        /**
         * Get results requiring confirmation
         */
        val pendingConfirmations: List<ToolResult.RequiresConfirmation>
            get() = toolResults.filterIsInstance<ToolResult.RequiresConfirmation>()

        /**
         * Format all tool results as a single string for display
         */
        fun formatResultsForDisplay(): String = buildString {
            if (plainText.isNotBlank()) {
                appendLine(plainText)
                appendLine()
            }

            toolResults.forEachIndexed { index, result ->
                when (result) {
                    is ToolResult.Success -> {
                        appendLine("✓ ${result.output}")
                    }
                    is ToolResult.Failure -> {
                        appendLine("✗ ${result.error.displayMessage}")
                    }
                    is ToolResult.RequiresConfirmation -> {
                        appendLine("? ${result.confirmationPrompt}")
                    }
                }
                if (index < toolResults.lastIndex) appendLine()
            }
        }.trim()

        /**
         * Format results for feeding back to LLM in multi-turn conversation
         */
        fun formatResultsForLlm(): String = buildString {
            appendLine("Tool execution results:")
            toolResults.forEach { result ->
                when (result) {
                    is ToolResult.Success -> {
                        appendLine("- ${result.toolId}: ${result.output}")
                    }
                    is ToolResult.Failure -> {
                        appendLine("- ${result.toolId}: ERROR - ${result.error.displayMessage}")
                    }
                    is ToolResult.RequiresConfirmation -> {
                        appendLine("- ${result.toolId}: PENDING CONFIRMATION")
                    }
                }
            }
        }.trim()
    }
}
