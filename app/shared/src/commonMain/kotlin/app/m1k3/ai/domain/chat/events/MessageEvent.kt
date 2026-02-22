package app.m1k3.ai.domain.chat.events

import app.m1k3.ai.domain.chat.ChatError
import app.m1k3.ai.domain.chat.EnrichedContext
import app.m1k3.ai.domain.chat.GenerationStats

/**
 * Events emitted during message sending flow.
 *
 * The ViewModel collects these events and updates UI state accordingly.
 *
 * Domain sealed class - Pure Kotlin, no platform dependencies.
 *
 * **Usage:**
 * ```kotlin
 * useCase.execute("Hello").collect { event ->
 *     when (event) {
 *         is MessageEvent.Started -> showThinking()
 *         is MessageEvent.RetrievingContext -> showContextLoading()
 *         is MessageEvent.ContextRetrieved -> updateContextInfo(event.context)
 *         is MessageEvent.Streaming -> updateText(event.partialText)
 *         is MessageEvent.Complete -> showComplete(event.response)
 *         is MessageEvent.Failed -> showError(event.error)
 *     }
 * }
 * ```
 */
sealed class MessageEvent {
    /** Message flow started, show thinking indicator */
    data object Started : MessageEvent()

    /** Retrieving context from RAG/memory */
    data object RetrievingContext : MessageEvent()

    /** Context retrieved successfully */
    data class ContextRetrieved(val context: EnrichedContext) : MessageEvent()

    /** Token received during streaming */
    data class Streaming(
        val partialText: String,
        val tokenCount: Int
    ) : MessageEvent()

    /** Generation completed successfully */
    data class Complete(val response: GenerationResponse) : MessageEvent()

    /** Generation failed */
    data class Failed(val error: ChatError) : MessageEvent()
}

/**
 * Successful generation response.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * @property text The generated text
 * @property stats Generation statistics
 * @property context Retrieved context (null for simple generations)
 */
data class GenerationResponse(
    /** The generated text */
    val text: String,

    /** Generation statistics */
    val stats: GenerationStats,

    /** Retrieved context (null for simple generations) */
    val context: EnrichedContext?
) {
    /** Check if RAG was used */
    val usedRag: Boolean
        get() = context?.hasRagContext == true

    /** Check if memory was used */
    val usedMemory: Boolean
        get() = context?.hasMemoryContext == true
}
