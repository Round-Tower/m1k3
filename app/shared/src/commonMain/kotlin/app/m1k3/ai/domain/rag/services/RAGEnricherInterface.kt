package app.m1k3.ai.domain.rag.services

import app.m1k3.ai.domain.usecases.rag.RAGResult

/**
 * RAG Enricher Interface - Domain abstraction for RAG enrichment.
 *
 * High-level interface for RAG (Retrieval-Augmented Generation) enrichment.
 * Platform implementations coordinate intent classification, knowledge retrieval,
 * and prompt enhancement.
 *
 * Domain interface - Pure Kotlin, no platform dependencies.
 *
 * **Responsibilities:**
 * 1. Classify user intent
 * 2. Retrieve relevant knowledge from repository
 * 3. Build enriched prompt with knowledge context
 *
 * **Usage:**
 * ```kotlin
 * val enricher: RAGEnricherInterface = ragManager
 *
 * val result = enricher.enrichPrompt(
 *     userQuery = "What is machine learning?",
 *     systemPrompt = "You are an AI assistant.",
 *     enableRAG = true
 * )
 *
 * if (result.ragApplied) {
 *     // Use result.enrichedPrompt with knowledge context
 *     // Use result.formatSources() for UI display
 *     // Use result.calculateConfidence() for quality indicator
 * }
 * ```
 *
 * @see RAGResult for return type with enriched prompt and metadata
 * @see formatSources extension for source formatting
 * @see calculateConfidence extension for confidence calculation
 */
interface RAGEnricherInterface {

    /**
     * Enrich system prompt with RAG knowledge.
     *
     * Pipeline:
     * 1. Classify intent from user query
     * 2. Check if retrieval is needed (skip conversational queries)
     * 3. Retrieve relevant knowledge from repository
     * 4. Apply category boosting for intent-matching facts
     * 5. Build enriched prompt with knowledge section
     *
     * @param userQuery User's current question
     * @param systemPrompt Base system prompt
     * @param enableRAG Enable/disable RAG (default: true)
     * @return RAGResult with enriched prompt and metadata
     */
    suspend fun enrichPrompt(
        userQuery: String,
        systemPrompt: String,
        enableRAG: Boolean = true
    ): RAGResult
}
