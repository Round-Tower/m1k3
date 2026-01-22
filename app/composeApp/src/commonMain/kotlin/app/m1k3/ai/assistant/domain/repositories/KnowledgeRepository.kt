package app.m1k3.ai.assistant.domain.repositories

import app.m1k3.ai.assistant.domain.rag.RetrievedFact

/**
 * KnowledgeRepository - Domain contract for RAG knowledge retrieval
 *
 * Pure Kotlin interface with no platform dependencies.
 * Defines operations for semantic retrieval from knowledge base.
 *
 * **Philosophy:**
 * Knowledge as a service. Domain defines retrieval contract,
 * platform implements storage/search (SQLDelight + embeddings).
 *
 * **Responsibilities:**
 * - Retrieve facts by semantic similarity
 * - Rank facts by relevance
 * - Apply category filtering
 * - Support minimum similarity thresholds
 *
 * **Platform Implementations:**
 * - Android: SemanticRetrievalService (SQLDelight + embedding search)
 * - iOS: Core Data + Core ML embeddings (future)
 *
 * **Knowledge Sources:**
 * - ai_ml_facts: AI/ML educational content
 * - device_tech: Device troubleshooting
 * - security: Security best practices
 * - troubleshooting: General troubleshooting
 * - trivia: Fun facts
 * - math_facts: Mathematical concepts
 * - ... 15+ more categories
 *
 * **Usage:**
 * ```kotlin
 * val knowledgeRepo: KnowledgeRepository = get() // Koin injection
 *
 * // Retrieve relevant facts
 * knowledgeRepo.retrieve(
 *     query = "What is machine learning?",
 *     limit = 4,
 *     minSimilarity = 0.5f
 * ).onSuccess { facts ->
 *     facts.forEach { fact ->
 *         println("${fact.content} (${fact.similarity})")
 *     }
 * }
 * ```
 */
interface KnowledgeRepository {
    /**
     * Retrieve facts by semantic similarity to query
     *
     * Uses embedding-based semantic search to find relevant facts
     * from the knowledge base. Facts are ranked by similarity score.
     *
     * **Similarity Scoring:**
     * - 0.8-1.0: High relevance (excellent match)
     * - 0.6-0.8: Medium relevance (good match)
     * - 0.4-0.6: Low relevance (weak match)
     * - <0.4: Not relevant (filter out)
     *
     * **Performance:**
     * - ~50-200ms for typical queries (depends on KB size)
     * - Uses HNSW index for fast approximate search
     * - Returns top K results sorted by similarity
     *
     * @param query User's question or search query
     * @param limit Maximum number of facts to return (default: 5)
     * @param minSimilarity Minimum similarity threshold 0.0-1.0 (default: 0.5)
     * @return Result.success(List<RetrievedFact>) or Result.failure
     */
    suspend fun retrieve(
        query: String,
        limit: Int = 5,
        minSimilarity: Float = 0.5f
    ): Result<List<RetrievedFact>>
}
