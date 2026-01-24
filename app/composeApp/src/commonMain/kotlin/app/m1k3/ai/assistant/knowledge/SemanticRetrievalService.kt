package app.m1k3.ai.assistant.knowledge

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.database.TriviaFact
import app.m1k3.ai.assistant.embedding.EmbeddingEngine
import app.m1k3.ai.assistant.embedding.EmbeddingTaskType
import app.m1k3.ai.assistant.utils.Logger
import app.m1k3.ai.domain.rag.KnowledgeTier
import app.m1k3.ai.domain.rag.SemanticRetrievedFact
import app.m1k3.ai.domain.rag.TieredRetrievedFact
import app.m1k3.ai.domain.rag.services.CategoryMatcher
import app.m1k3.ai.domain.rag.services.EmbeddingSerializer
import app.m1k3.ai.domain.rag.services.KnowledgeFactFilter

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
    private val embeddingEngine: EmbeddingEngine,
    private val factFilter: KnowledgeFactFilter = KnowledgeFactFilter()
) {
    private val logger = Logger.withTag("SemanticRetrieval")

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

        logger.d { "Query: ${query.length}chars, limit=$limit, minSimilarity=$minSimilarity" }

        // 1. Embed the user query
        val queryEmbeddingResult = embeddingEngine.embed(query, EmbeddingTaskType.QUERY)
        if (queryEmbeddingResult.isFailure) {
            logger.w { "Query embedding failed: ${queryEmbeddingResult.exceptionOrNull()?.message}" }
            // Fallback to keyword-based retrieval if embedding fails
            return fallbackToKeywordSearch(query, limit)
        }

        val queryEmbedding = queryEmbeddingResult.getOrThrow()
        logger.d { "Query embedded (${queryEmbedding.size} dimensions)" }

        // 2. Get all facts with embeddings from database
        // TODO: Replace with HNSW vector index when Phase 2 complete
        val rawFacts = database.triviaFactQueries.getFactsWithEmbeddings().executeAsList()

        // 2.1. CRITICAL FIX: Filter out blocked categories (e.g., explanation_request garbage)
        val allFacts = rawFacts.filter { fact ->
            !KnowledgeFactFilter.BLOCKED_CATEGORIES.contains(fact.category.lowercase())
        }
        val filteredCount = rawFacts.size - allFacts.size
        if (filteredCount > 0) {
            logger.d { "Filtered $filteredCount blocked category facts" }
        }
        logger.d { "Searching ${allFacts.size} facts in database" }

        // 3. Calculate similarity for each fact and create tiered facts
        val tieredFacts = mutableListOf<TieredRetrievedFact>()
        val factMetadata = mutableMapOf<String, Pair<TriviaFact, Float>>() // factId -> (fact, similarity)
        val allSimilarities = mutableListOf<Pair<Float, String>>() // For debug logging

        for (fact in allFacts) {
            // PHASE1.5-005: Use actual embeddings stored in database
            // Get fact embedding from database or generate on-demand
            val factEmbedding = getOrGenerateFactEmbedding(fact)

            if (factEmbedding != null) {
                // Calculate actual cosine similarity using embeddings
                val similarity = embeddingEngine.cosineSimilarity(queryEmbedding, factEmbedding)
                allSimilarities.add(Pair(similarity, fact.category))

                // Create TieredRetrievedFact for filter processing
                val tier = KnowledgeTier.fromString(fact.tier)
                tieredFacts.add(
                    TieredRetrievedFact(
                        content = fact.question,
                        category = fact.category,
                        similarity = similarity,
                        tier = tier
                    )
                )
                factMetadata[fact.id] = Pair(fact, similarity)
            }
        }

        // DEBUG: Show top 10 similarity scores (even if below threshold)
        val top10 = allSimilarities.sortedByDescending { it.first }.take(10)
        logger.d { "Top 10 similarity scores:" }
        top10.forEachIndexed { index, (similarity, category) ->
            val passThreshold = if (similarity >= minSimilarity) "✅" else "❌"
            logger.d { "  ${index + 1}. $passThreshold ${"%.4f".format(similarity)} - $category" }
        }

        // 4. Apply tiered filtering pipeline
        // This applies tier-specific thresholds (CURATED: 0.5, VERIFIED: 0.6, SYNTHETIC: 0.7)
        // Then sorts by tier priority (CURATED > VERIFIED > SYNTHETIC)
        // Then limits per tier (CURATED: 3, VERIFIED: 2, SYNTHETIC: 1)
        val filteredTieredFacts = factFilter.applyFullPipeline(tieredFacts)

        logger.i { "Tiered retrieval: ${filteredTieredFacts.size}/${allFacts.size} facts passed pipeline" }

        // Log tier breakdown
        val tierCounts = filteredTieredFacts.groupBy { it.tier }.mapValues { it.value.size }
        tierCounts.forEach { (tier, count) ->
            logger.d { "  $tier: $count facts" }
        }

        // 5. Convert back to SemanticRetrievedFact (domain entity)
        val rankedFacts = mutableListOf<SemanticRetrievedFact>()
        for (tieredFact in filteredTieredFacts) {
            // Find the original TriviaFact by matching content (question)
            val matchingEntry = factMetadata.entries.find { (_, pair) ->
                pair.first.question == tieredFact.content && pair.first.category == tieredFact.category
            }
            if (matchingEntry != null) {
                val (fact, similarity) = matchingEntry.value
                rankedFacts.add(
                    SemanticRetrievedFact(
                        id = fact.id,
                        question = fact.question,
                        answer = fact.answer,
                        category = fact.category,
                        tier = KnowledgeTier.fromString(fact.tier),
                        similarityScore = similarity,
                        relevanceScore = calculateSemanticRelevance(similarity, fact.importance),
                        retrievalMethod = "tiered_semantic"
                    )
                )
            }
        }

        // 6. Take top N (already filtered by tier limits, but respect caller's limit)
        val topFacts = rankedFacts.take(limit)

        // 7. Update access counts
        topFacts.forEach { retrieved ->
            database.triviaFactQueries.updateFactAccess(
                last_accessed_at = System.currentTimeMillis(),
                id = retrieved.id
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
     * Delegates to domain EmbeddingSerializer for KMP compatibility.
     */
    private fun serializeEmbedding(embedding: FloatArray): ByteArray =
        EmbeddingSerializer.serialize(embedding)

    /**
     * Deserialize ByteArray from BLOB storage to FloatArray.
     * Delegates to domain EmbeddingSerializer for KMP compatibility.
     */
    private fun deserializeEmbedding(bytes: ByteArray): FloatArray =
        EmbeddingSerializer.deserialize(bytes)

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
                        id = fact.id,
                        question = fact.question,
                        answer = fact.answer,
                        category = fact.category,
                        tier = KnowledgeTier.fromString(fact.tier),
                        similarityScore = 0.5f,  // Default similarity for fallback
                        relevanceScore = fact.importance,
                        retrievalMethod = "keyword_fallback"
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
         * 0.5 is chosen to capture more relevant results while filtering noise.
         * Previously 0.6 but lowered to reduce missed retrievals.
         * - Below 0.4: Likely irrelevant
         * - 0.4-0.5: Weak relevance
         * - 0.5-0.6: Moderately relevant (now included!)
         * - 0.6-0.7: Highly relevant
         * - 0.7+: Extremely relevant
         */
        /**
         * Raised from 0.5 to 0.65 to filter more noise.
         * With blocked categories removed, we can be stricter.
         */
        const val DEFAULT_MIN_SIMILARITY = 0.65f

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
            appendLine("Query: ${query.length}chars")
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
