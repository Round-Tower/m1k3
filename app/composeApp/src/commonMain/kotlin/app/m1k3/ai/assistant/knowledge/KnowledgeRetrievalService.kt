package app.m1k3.ai.assistant.knowledge

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.database.TriviaFact

/**
 * KnowledgeRetrievalService - Fast keyword-based fact retrieval
 *
 * Retrieves relevant facts from the knowledge base using:
 * 1. Category matching (rule-based keywords)
 * 2. Full-text search (SQL LIKE queries)
 * 3. Importance ranking
 *
 * Phase 1 implementation: Simple keyword RAG (no semantic embeddings)
 */
class KnowledgeRetrievalService(private val database: MaDatabase) {

    /**
     * Retrieve relevant facts for a user query
     *
     * @param query User's question/prompt
     * @param limit Maximum number of facts to return (default: 3)
     * @return List of retrieved facts ranked by relevance
     */
    fun retrieve(query: String, limit: Int = 3): List<RetrievedFact> {
        if (query.isBlank()) return emptyList()

        val retrievedFacts = mutableListOf<RetrievedFact>()

        // Strategy 1: Category-based retrieval
        val categories = CategoryMatcher.matchCategories(query)
        if (categories.isNotEmpty()) {
            categories.forEach { category ->
                val categoryFacts = database.triviaFactQueries
                    .getHighImportanceFactsByCategory(category, 0.5)
                    .executeAsList()
                    .take(2) // Top 2 per category

                categoryFacts.forEach { fact ->
                    retrievedFacts.add(
                        RetrievedFact(
                            fact = fact,
                            relevanceScore = calculateRelevance(fact, query, fromCategory = true),
                            retrievalMethod = "category:$category"
                        )
                    )
                }
            }
        }

        // Strategy 2: Full-text search (fallback or supplement)
        val keywords = CategoryMatcher.extractKeywords(query)
        if (keywords.isNotEmpty()) {
            val searchQuery = keywords.joinToString(" ")

            val searchResults = database.triviaFactQueries
                .searchFactsFullText(searchQuery, searchQuery, limit.toLong())
                .executeAsList()

            searchResults.forEach { fact ->
                // Avoid duplicates
                if (retrievedFacts.none { it.fact.id == fact.id }) {
                    retrievedFacts.add(
                        RetrievedFact(
                            fact = fact,
                            relevanceScore = calculateRelevance(fact, query, fromCategory = false),
                            retrievalMethod = "full_text"
                        )
                    )
                }
            }
        }

        // Rank by relevance and take top N
        val rankedFacts = retrievedFacts
            .sortedByDescending { it.relevanceScore }
            .take(limit)

        // Update access counts for tracking
        rankedFacts.forEach { retrieved ->
            database.triviaFactQueries.updateFactAccess(
                last_accessed_at = System.currentTimeMillis(),
                id = retrieved.fact.id
            )
        }

        return rankedFacts
    }

    /**
     * Calculate relevance score (0.0 to 1.0)
     *
     * Factors:
     * - Keyword overlap
     * - Importance rating
     * - Category match bonus
     */
    private fun calculateRelevance(
        fact: TriviaFact,
        query: String,
        fromCategory: Boolean
    ): Double {
        var score = 0.0

        // Base importance (0.0 to 1.0)
        score += fact.importance * 0.4

        // Keyword overlap bonus
        val queryKeywords = CategoryMatcher.extractKeywords(query).toSet()
        val factKeywords = CategoryMatcher.extractKeywords(fact.question + " " + fact.answer).toSet()
        val overlap = queryKeywords.intersect(factKeywords).size.toDouble()
        val maxOverlap = maxOf(queryKeywords.size, 1)
        score += (overlap / maxOverlap) * 0.4

        // Category match bonus
        if (fromCategory) {
            score += 0.2
        }

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * Get stats about retrieval for debugging
     */
    fun getRetrievalStats(query: String): RetrievalStats {
        val categories = CategoryMatcher.matchCategories(query)
        val keywords = CategoryMatcher.extractKeywords(query)
        val totalFacts = database.triviaFactQueries.getTotalFactCount().executeAsOne()

        return RetrievalStats(
            query = query,
            matchedCategories = categories,
            extractedKeywords = keywords,
            totalFactsAvailable = totalFacts
        )
    }
}

/**
 * Retrieved fact with relevance metadata
 */
data class RetrievedFact(
    val fact: TriviaFact,
    val relevanceScore: Double,
    val retrievalMethod: String
)

/**
 * Retrieval statistics for debugging
 */
data class RetrievalStats(
    val query: String,
    val matchedCategories: List<String>,
    val extractedKeywords: List<String>,
    val totalFactsAvailable: Long
)
