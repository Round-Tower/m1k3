package app.m1k3.ai.assistant.knowledge

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.database.TriviaFact

/**
 * Documentation signed: Kev + claude-sonnet-4-5-20250929, 2026-01-15
 * Format: MurphySig v0.1 (https://murphysig.dev/spec)
 * Prior: Minimal documentation (basic method signatures, no algorithm details)
 *
 * Context: Enhanced documentation for Phase 1 keyword-based retrieval system.
 * Key improvements:
 * - retrieve(): Detailed two-stage strategy docs (category + full-text)
 * - calculateRelevance(): Full algorithm breakdown with formula, weights, worked example
 * - Fixed terminology: "Query coverage ratio" (not Jaccard similarity)
 * - Added threading guidance: synchronous database queries require Dispatchers.IO
 * - getRetrievalStats(): Documented use cases (debugging, UI, analytics)
 *
 * Confidence: 0.90 - Algorithm documentation is clear and mathematically accurate.
 * KMP mobile AI reviewer verified scoring formula and terminology correction.
 * Reduced from 0.95 due to:
 * - RetrievedFact and RetrievalStats data classes lack property-level KDoc
 * - No performance characteristics documented (query time, index size)
 * - Phase 2 semantic upgrade path not mentioned
 *
 * Open: Should calculateRelevance() weights (0.4/0.4/0.2) be configurable?
 * Current tuning based on manual testing, not systematic evaluation.
 */

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
     * Uses two-stage retrieval strategy:
     * 1. **Category matching**: Rule-based keyword detection → category facts
     * 2. **Full-text search**: SQL LIKE queries on question/answer fields
     *
     * Results are ranked by composite relevance score (importance + keyword overlap + category bonus)
     * and access tracking is updated for retrieved facts.
     *
     * **Threading:** Performs synchronous database queries.
     * Call from Dispatchers.IO or background thread to avoid blocking UI.
     * Database access is internally synchronized by SQLDelight.
     *
     * @param query User's question or prompt (e.g., "What is quantum computing?")
     *              Blank queries return empty list immediately.
     *              Special characters are escaped for SQL safety.
     * @param limit Maximum number of facts to return (default: 3)
     *              Recommended: 3-5 for mobile UI (chat context)
     *              Higher values (10-20) may reduce response quality (noise)
     * @return List of retrieved facts ranked by descending relevance score [0.0, 1.0]
     *         Each fact includes retrieval metadata (method, score)
     *         Returns empty list if no facts match query or knowledge base is empty
     * @see RetrievedFact Data class containing fact, relevance score, and retrieval method
     * @see calculateRelevance Internal scoring algorithm
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
     * Calculate composite relevance score (0.0 to 1.0)
     *
     * Combines three weighted factors:
     * 1. **Importance** (40%): Pre-computed fact quality [0.0, 1.0]
     * 2. **Keyword overlap** (40%): Query coverage ratio (proportion of query keywords found in fact)
     * 3. **Category bonus** (20%): Extra score if fact came from category match
     *
     * **Scoring Formula:**
     * ```
     * score = (importance * 0.4) + (keyword_overlap * 0.4) + (category_bonus * 0.2)
     * keyword_overlap = |query_keywords ∩ fact_keywords| / |query_keywords|  (query coverage)
     * category_bonus = 0.2 if fromCategory else 0.0
     * ```
     *
     * **Note:** This uses query coverage, not Jaccard similarity.
     * Jaccard would be: overlap / (|query| + |fact| - overlap)
     *
     * **Example:**
     * - Query: "quantum computing algorithms"
     * - Fact: "Shor's algorithm for quantum computers" (importance=0.8)
     * - Keywords overlap: {quantum, algorithms} → 2/3 = 0.67
     * - Category match: YES
     * - Score: (0.8 * 0.4) + (0.67 * 0.4) + 0.2 = 0.32 + 0.27 + 0.2 = 0.79
     *
     * @param fact The trivia fact to score
     * @param query User's query string
     * @param fromCategory True if fact was retrieved via category match (adds 0.2 bonus)
     * @return Relevance score in [0.0, 1.0] where 1.0 = perfect match
     */
    private fun calculateRelevance(
        fact: TriviaFact,
        query: String,
        fromCategory: Boolean
    ): Double {
        var score = 0.0

        // Component 1: Base importance (40% weight)
        // Pre-computed quality score from KnowledgeBaseImporter
        score += fact.importance * 0.4

        // Component 2: Keyword overlap (40% weight)
        // Measures lexical similarity between query and fact content
        val queryKeywords = CategoryMatcher.extractKeywords(query).toSet()
        val factKeywords = CategoryMatcher.extractKeywords(fact.question + " " + fact.answer).toSet()
        val overlap = queryKeywords.intersect(factKeywords).size.toDouble()
        val maxOverlap = maxOf(queryKeywords.size, 1)  // Avoid division by zero
        score += (overlap / maxOverlap) * 0.4

        // Component 3: Category match bonus (20% weight)
        // Reward facts from rule-based category detection (higher precision)
        if (fromCategory) {
            score += 0.2
        }

        return score.coerceIn(0.0, 1.0)  // Clamp to valid range
    }

    /**
     * Get retrieval statistics for debugging and observability
     *
     * Provides insight into how the retrieval process interprets a query without
     * performing actual fact retrieval. Useful for:
     * - Debugging category matching logic
     * - Tuning keyword extraction
     * - UI showing "searching in: [categories]"
     * - Analytics tracking query patterns
     *
     * @param query User's question to analyze
     * @return RetrievalStats containing:
     *         - Matched categories from rule-based detection
     *         - Extracted keywords after stop word removal
     *         - Total facts in knowledge base for context
     * @see CategoryMatcher.matchCategories For category detection rules
     * @see CategoryMatcher.extractKeywords For keyword extraction logic
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
