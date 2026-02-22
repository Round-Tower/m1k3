package app.m1k3.ai.domain.chat.events

import app.m1k3.ai.domain.chat.ChatError
import app.m1k3.ai.domain.chat.EnrichedContext
import app.m1k3.ai.domain.chat.GenerationStats
import app.m1k3.ai.domain.tools.ToolResult

/**
 * Events emitted during chat flow with tool support.
 *
 * Domain sealed class - Pure Kotlin, no platform dependencies.
 *
 * **Usage:**
 * ```kotlin
 * useCase.execute("What's my battery?").collect { event ->
 *     when (event) {
 *         is ChatEvent.Started -> showThinking()
 *         is ChatEvent.RetrievingContext -> showContextLoading()
 *         is ChatEvent.ContextRetrieved -> updateContextInfo(event.context)
 *         is ChatEvent.Generating -> showGenerating()
 *         is ChatEvent.Streaming -> updateText(event.partialText)
 *         is ChatEvent.ToolsExecuted -> handleTools(event.results)
 *         is ChatEvent.Complete -> showComplete(event.response)
 *         is ChatEvent.Failed -> showError(event.error)
 *     }
 * }
 * ```
 */
sealed class ChatEvent {
    /** Chat flow started */
    data object Started : ChatEvent()

    /** Retrieving context from RAG/memory */
    data object RetrievingContext : ChatEvent()

    /** Context retrieved successfully */
    data class ContextRetrieved(val context: EnrichedContext) : ChatEvent()

    /** Generating AI response */
    data object Generating : ChatEvent()

    /** Streaming token received (for real-time streaming) */
    data class Streaming(
        val partialText: String,
        val tokenCount: Int
    ) : ChatEvent()

    /** Tools were detected and executed */
    data class ToolsExecuted(
        val results: List<ToolResult>,
        val hasPendingConfirmations: Boolean
    ) : ChatEvent() {
        val allSucceeded: Boolean
            get() = results.all { it.isSuccess }

        val successfulResults: List<ToolResult.Success>
            get() = results.filterIsInstance<ToolResult.Success>()

        val failedResults: List<ToolResult.Failure>
            get() = results.filterIsInstance<ToolResult.Failure>()

        val pendingConfirmations: List<ToolResult.RequiresConfirmation>
            get() = results.filterIsInstance<ToolResult.RequiresConfirmation>()
    }

    /** Chat completed successfully */
    data class Complete(val response: ChatResponse) : ChatEvent()

    /** Chat failed */
    data class Failed(val error: ChatError) : ChatEvent()
}

/**
 * Complete chat response including text, stats, and tool results.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * @property text The generated/processed text
 * @property stats Generation statistics
 * @property context Retrieved context
 * @property toolResults Tool execution results (null if no tools called)
 * @property toolResultsFormatted Formatted tool results for display
 */
data class ChatResponse(
    /** The generated/processed text */
    val text: String,

    /** Generation statistics */
    val stats: GenerationStats,

    /** Retrieved context */
    val context: EnrichedContext?,

    /** Tool execution results (null if no tools called) */
    val toolResults: List<ToolResult>?,

    /** Formatted tool results for display */
    val toolResultsFormatted: String? = null
) {
    /** Whether tools were executed */
    val hasToolResults: Boolean
        get() = toolResults?.isNotEmpty() == true

    /** Whether RAG was used */
    val usedRag: Boolean
        get() = context?.hasRagContext == true

    /** Whether memory was used */
    val usedMemory: Boolean
        get() = context?.hasMemoryContext == true

    /**
     * Get the full display text (text + tool results)
     */
    fun getDisplayText(): String = buildString {
        if (text.isNotBlank()) {
            append(text)
        }
        if (toolResultsFormatted != null) {
            if (isNotEmpty()) appendLine()
            append(toolResultsFormatted)
        }
    }
}
