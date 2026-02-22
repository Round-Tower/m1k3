package app.m1k3.ai.domain.chat.services

import app.m1k3.ai.domain.chat.EnrichedContext

/**
 * Context Retriever Interface - Abstraction for context retrieval.
 *
 * High-level interface for retrieving context for AI generation.
 * Implementations coordinate RAG, memory, and conversation history retrieval.
 *
 * Domain interface - Pure Kotlin, no platform dependencies.
 *
 * **Usage:**
 * ```kotlin
 * val retriever: ContextRetrieverInterface = contextRetrievalUseCase
 * val context = retriever.retrieveContext("What is photosynthesis?")
 * // Returns EnrichedContext with RAG facts, memories, and history
 * ```
 *
 * @see EnrichedContext for the returned context structure
 */
interface ContextRetrieverInterface {

    /**
     * Retrieve context for a given prompt.
     *
     * Orchestrates context retrieval from multiple sources:
     * - RAG: Knowledge base facts based on intent
     * - Memory: Semantic memories from conversation history
     * - History: Recent conversation topics
     *
     * @param prompt The user's query
     * @return EnrichedContext containing all retrieved context
     */
    suspend fun retrieveContext(prompt: String): EnrichedContext

    /**
     * Check if RAG (Retrieval-Augmented Generation) is enabled.
     *
     * @return true if RAG retrieval is enabled
     */
    fun isRagEnabled(): Boolean
}
