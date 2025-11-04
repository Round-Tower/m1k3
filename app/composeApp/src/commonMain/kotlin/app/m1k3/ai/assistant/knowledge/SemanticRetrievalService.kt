package app.m1k3.ai.assistant.knowledge

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.database.TriviaFact
import app.m1k3.ai.assistant.embedding.EmbeddingEngine
import app.m1k3.ai.assistant.embedding.EmbeddingTaskType

/**
 * SemanticRetrievalService - PHASE1.5-005
 *
 * Semantic search using embedding similarity (replaces keyword-based RAG).
 *
 * **Problem Solved:**
 * Current keyword-based retrieval fails spectacularly:
 * - Query: "Can you teach me about AI?"
 * - Retrieved: "problem-solving skills", "data analysis", "online shopping"
 * - Similarity: 0.2-0.4 (effectively random)
 *
 * **Solution:**
 * - Embed user query with MiniLM-L6 (384-dimensional)
 * - Search knowledge base embeddings with cosine similarity
 * - High threshold (0.6+) filters irrelevant results
 * - Result: "teach me about AI" → AI/ML facts (0.85+ similarity)
 *
 * **Architecture:**
 * - Embeddings: Phase 2 MiniLM-L6 (512-dim Matryoshka from 768)
 * - Search: Linear scan for now (HNSW optimization in Phase 2 completion)
 * - Threshold: 0.6 minimum similarity (configurable)
 * - Ranking: Combined similarity (0.7) + importance (0.3)
 *
 * Example:
 * ```kotlin
 * val service = SemanticRetrievalService(database, embeddingEngine)
 * val facts = service.retrieve("teach me about AI", limit = 3)
 * // Returns: [AI definition, ML basics, Neural networks] with 0.85+ similarity
 * ```
 */
class SemanticRetrievalService(
    private val database: MaDatabase,
    private val embeddingEngine: EmbeddingEngine
) {

    /**
     * Retrieve relevant facts using semantic similarity search.
     *
     * @param query User's question/prompt
     * @param limit Maximum number of facts to return (default: 3)
     * @param minSimilarity Minimum cosine similarity threshold (default: 0.6)
     * @return List of semantically relevant facts ranked by relevance
     */
    suspend fun retrieve(
        query: String,
        limit: Int = 3,
        minSimilarity: Float = DEFAULT_MIN_SIMILARITY
    ): List<SemanticRetrievedFact> {
        if (query.isBlank()) return emptyList()

        // 1. Embed the user query
        val queryEmbeddingResult = embeddingEngine.embed(query, EmbeddingTaskType.QUERY)
        if (queryEmbeddingResult.isFailure) {
            // Fallback to keyword-based retrieval if embedding fails
            return fallbackToKeywordSearch(query, limit)
        }

        val queryEmbedding = queryEmbeddingResult.getOrThrow()

        // 2. Get all facts with embeddings from database
        // TODO: Replace with HNSW vector index when Phase 2 complete
        val allFacts = database.triviaFactQueries.getFactsWithEmbeddings().executeAsList()

        // 3. Calculate similarity for each fact
        val rankedFacts = mutableListOf<SemanticRetrievedFact>()

        for (fact in allFacts) {
            // PHASE1.5-005: Use actual embeddings stored in database
            // Get fact embedding from database or generate on-demand
            val factEmbedding = getOrGenerateFactEmbedding(fact)

            if (factEmbedding != null) {
                // Calculate actual cosine similarity using embeddings
                val similarity = embeddingEngine.cosineSimilarity(queryEmbedding, factEmbedding)

                // Apply threshold filter
                if (similarity >= minSimilarity) {
                    rankedFacts.add(
                        SemanticRetrievedFact(
                            fact = fact,
                            relevanceScore = calculateSemanticRelevance(similarity, fact.importance),
                            retrievalMethod = "semantic_embedding",
                            similarityScore = similarity
                        )
                    )
                }
            }
        }

        // 4. Rank by relevance and take top N
        val topFacts = rankedFacts
            .sortedByDescending { it.relevanceScore }
            .take(limit)

        // 5. Update access counts
        topFacts.forEach { retrieved ->
            database.triviaFactQueries.updateFactAccess(
                last_accessed_at = System.currentTimeMillis(),
                id = retrieved.fact.id
            )
        }

        return topFacts
    }

    /**
     * Get retrieval debug info showing why facts were selected.
     *
     * Useful for understanding retrieval quality and tuning thresholds.
     *
     * @param query User's question
     * @param topK Number of top results to analyze (default: 10)
     * @return Debug information with similarity scores
     */
    suspend fun getRetrievalDebugInfo(
        query: String,
        topK: Int = 10
    ): SemanticRetrievalDebugInfo {
        if (query.isBlank()) {
            return SemanticRetrievalDebugInfo(
                query = query,
                queryEmbeddingDimensions = 0,
                topResults = emptyList(),
                averageSimilarity = 0f,
                maxSimilarity = 0f,
                minSimilarity = 0f,
                factsAboveThreshold = 0,
                totalFactsSearched = 0
            )
        }

        // Embed query
        val queryEmbeddingResult = embeddingEngine.embed(query, EmbeddingTaskType.QUERY)
        if (queryEmbeddingResult.isFailure) {
            return SemanticRetrievalDebugInfo(
                query = query,
                queryEmbeddingDimensions = 0,
                topResults = emptyList(),
                averageSimilarity = 0f,
                maxSimilarity = 0f,
                minSimilarity = 0f,
                factsAboveThreshold = 0,
                totalFactsSearched = 0
            )
        }

        val queryEmbedding = queryEmbeddingResult.getOrThrow()
        val allFacts = database.triviaFactQueries.getFactsWithEmbeddings().executeAsList()

        // Calculate similarities for all facts
        val allSimilarities = mutableListOf<DebugResult>()

        for (fact in allFacts) {
            // PHASE1.5-005: Use actual embeddings for debug info
            val factEmbedding = getOrGenerateFactEmbedding(fact)
            val similarity = if (factEmbedding != null) {
                embeddingEngine.cosineSimilarity(queryEmbedding, factEmbedding)
            } else {
                0f // No embedding available
            }

            allSimilarities.add(
                DebugResult(
                    factId = fact.id,
                    question = fact.question,
                    category = fact.category,
                    similarity = similarity,
                    thresholdPassed = similarity >= DEFAULT_MIN_SIMILARITY
                )
            )
        }

        // Sort by similarity and take top K
        val topResults = allSimilarities
            .sortedByDescending { it.similarity }
            .take(topK)

        // Calculate statistics
        val similarities = allSimilarities.map { it.similarity }
        val avgSimilarity = if (similarities.isNotEmpty()) {
            similarities.average().toFloat()
        } else 0f

        return SemanticRetrievalDebugInfo(
            query = query,
            queryEmbeddingDimensions = queryEmbedding.size,
            topResults = topResults,
            averageSimilarity = avgSimilarity,
            maxSimilarity = similarities.maxOrNull() ?: 0f,
            minSimilarity = similarities.minOrNull() ?: 0f,
            factsAboveThreshold = allSimilarities.count { it.thresholdPassed },
            totalFactsSearched = allSimilarities.size
        )
    }

    /**
     * Calculate relevance score combining similarity and importance.
     *
     * Formula: (similarity * 0.7) + (importance * 0.3)
     * - Similarity weighted heavily (0.7) since embeddings capture meaning
     * - Importance moderately (0.3) to promote quality facts
     *
     * @param similarity Cosine similarity score (0.0 to 1.0)
     * @param importance Fact importance score (0.0 to 1.0)
     * @return Combined relevance score (0.0 to 1.0)
     */
    private fun calculateSemanticRelevance(similarity: Float, importance: Double): Double {
        return (similarity * SIMILARITY_WEIGHT) + (importance * IMPORTANCE_WEIGHT)
    }

    /**
     * Get or generate embedding for a fact.
     *
     * PHASE1.5-005: Implements on-demand embedding generation with caching.
     * - First checks database for stored embedding
     * - If not found, generates embedding and stores it
     * - Returns null if embedding generation fails
     *
     * @param fact Trivia fact to get embedding for
     * @return Embedding vector or null if unavailable
     */
    private suspend fun getOrGenerateFactEmbedding(fact: TriviaFact): FloatArray? {
        // Check if fact already has embedding stored
        if (fact.has_embedding == 1L) {
            // Try to retrieve from database
            val embeddingBytesWrapper = database.triviaFactQueries
                .getFactEmbeddingVector(fact.id)
                .executeAsOneOrNull()

            if (embeddingBytesWrapper != null && embeddingBytesWrapper.embedding_vector != null) {
                // Deserialize BLOB to FloatArray
                return deserializeEmbedding(embeddingBytesWrapper.embedding_vector)
            }
        }

        // Generate embedding on-demand
        val text = "${fact.question} ${fact.answer}".take(2048) // Limit to max tokens
        val embeddingResult = embeddingEngine.embed(text, EmbeddingTaskType.DOCUMENT)

        if (embeddingResult.isSuccess) {
            val embedding = embeddingResult.getOrThrow()

            // Store in database for future use
            val embeddingBytes = serializeEmbedding(embedding)
            database.triviaFactQueries.updateFactEmbeddingVector(
                embedding_vector = embeddingBytes,
                id = fact.id
            )

            return embedding
        }

        return null
    }

    /**
     * Serialize FloatArray to ByteArray for BLOB storage.
     *
     * Format: IEEE 754 single-precision (4 bytes per float)
     * 384 dimensions × 4 bytes = 1,536 bytes
     */
    private fun serializeEmbedding(embedding: FloatArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(embedding.size * 4)
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    /**
     * Deserialize ByteArray from BLOB storage to FloatArray.
     */
    private fun deserializeEmbedding(bytes: ByteArray): FloatArray {
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val embedding = FloatArray(bytes.size / 4)
        for (i in embedding.indices) {
            embedding[i] = buffer.getFloat()
        }
        return embedding
    }

    /**
     * Fallback to keyword-based search when embedding fails.
     *
     * Uses the old KnowledgeRetrievalService logic as backup.
     *
     * @param query User's question
     * @param limit Maximum results
     * @return Keyword-based retrieved facts (converted to semantic format)
     */
    private fun fallbackToKeywordSearch(query: String, limit: Int): List<SemanticRetrievedFact> {
        val categories = CategoryMatcher.matchCategories(query)
        val results = mutableListOf<SemanticRetrievedFact>()

        categories.forEach { category ->
            val facts = database.triviaFactQueries
                .getHighImportanceFactsByCategory(category, 0.5)
                .executeAsList()
                .take(limit)

            facts.forEach { fact ->
                results.add(
                    SemanticRetrievedFact(
                        fact = fact,
                        relevanceScore = fact.importance,
                        retrievalMethod = "keyword_fallback",
                        similarityScore = 0.5f  // Default similarity for fallback
                    )
                )
            }
        }

        return results
            .sortedByDescending { it.relevanceScore }
            .take(limit)
    }

    companion object {
        /**
         * Default minimum similarity threshold.
         *
         * 0.6 is chosen to filter out weak matches while keeping relevant results.
         * - Below 0.5: Likely irrelevant
         * - 0.5-0.6: Questionable relevance
         * - 0.6-0.7: Moderately relevant
         * - 0.7-0.8: Highly relevant
         * - 0.8+: Extremely relevant
         */
        const val DEFAULT_MIN_SIMILARITY = 0.6f

        /**
         * Weight for similarity in relevance calculation.
         */
        private const val SIMILARITY_WEIGHT = 0.7

        /**
         * Weight for importance in relevance calculation.
         */
        private const val IMPORTANCE_WEIGHT = 0.3
    }
}

/**
 * Semantically retrieved fact with similarity metadata.
 *
 * Extends RetrievedFact with similarity score for transparency and debugging.
 */
data class SemanticRetrievedFact(
    val fact: TriviaFact,
    val relevanceScore: Double,
    val retrievalMethod: String,
    val similarityScore: Float  // NEW: Cosine similarity (0.0 to 1.0)
) {
    /**
     * Convert to legacy RetrievedFact format for compatibility.
     */
    fun toRetrievedFact(): RetrievedFact {
        return RetrievedFact(
            fact = fact,
            relevanceScore = relevanceScore,
            retrievalMethod = retrievalMethod
        )
    }
}

/**
 * Debug information for semantic retrieval analysis.
 *
 * Helps understand why facts were or weren't retrieved.
 */
data class SemanticRetrievalDebugInfo(
    val query: String,
    val queryEmbeddingDimensions: Int,
    val topResults: List<DebugResult>,
    val averageSimilarity: Float,
    val maxSimilarity: Float,
    val minSimilarity: Float,
    val factsAboveThreshold: Int,
    val totalFactsSearched: Int
) {
    /**
     * Format as human-readable string for logging.
     */
    fun formatSummary(): String {
        return buildString {
            appendLine("🔍 Semantic Retrieval Debug Info")
            appendLine("Query: \"$query\"")
            appendLine("Embedding dimensions: $queryEmbeddingDimensions")
            appendLine()
            appendLine("📊 Statistics:")
            appendLine("  Total facts searched: $totalFactsSearched")
            appendLine("  Above threshold (0.6+): $factsAboveThreshold")
            appendLine("  Average similarity: ${"%.3f".format(averageSimilarity)}")
            appendLine("  Max similarity: ${"%.3f".format(maxSimilarity)}")
            appendLine("  Min similarity: ${"%.3f".format(minSimilarity)}")
            appendLine()
            appendLine("🎯 Top Results:")
            topResults.forEachIndexed { index, result ->
                val passIcon = if (result.thresholdPassed) "✅" else "❌"
                appendLine("  ${index + 1}. $passIcon ${result.question}")
                appendLine("      Similarity: ${"%.3f".format(result.similarity)} | Category: ${result.category}")
            }
        }
    }
}

/**
 * Debug result for a single fact.
 */
data class DebugResult(
    val factId: String,
    val question: String,
    val category: String,
    val similarity: Float,
    val thresholdPassed: Boolean
)
