package app.m1k3.ai.assistant.history

import app.m1k3.ai.assistant.database.MaDatabase

/**
 * SearchRepository - Semantic Search Across Conversation History
 *
 * **Philosophy:**
 * Search is core to finding relevant information in your chat history.
 * This repository provides intelligent retrieval with:
 * - Keyword search (fast, exact matches)
 * - Semantic search with embeddings (coming in Phase 2.1)
 * - Flexible filtering (project, conversation, date range)
 * - Relevance-ranked results
 *
 * **Usage Example:**
 * ```kotlin
 * // Search all conversations
 * val results = repository.searchMessages(
 *     query = "machine learning models",
 *     projectId = null,
 *     limit = 10
 * )
 *
 * // Search within a project
 * val projectResults = repository.searchMessages(
 *     query = "database schema",
 *     projectId = "project_001",
 *     limit = 20
 * )
 *
 * // Search with date filter (last 7 days)
 * val recentResults = repository.searchMessages(
 *     query = "API design",
 *     startTimestamp = sevenDaysAgo,
 *     limit = 10
 * )
 * ```
 */
class SearchRepository(private val database: MaDatabase) {

    /**
     * Search messages across conversation history.
     *
     * Performs intelligent keyword search with optional filtering.
     * Future enhancement: Semantic search with embeddings (Phase 2.1).
     *
     * @param query Search query (keywords)
     * @param projectId Optional project filter
     * @param conversationId Optional conversation filter
     * @param startTimestamp Optional start date (Unix milliseconds)
     * @param endTimestamp Optional end date (Unix milliseconds)
     * @param limit Maximum results to return (default: 50)
     * @return List of SearchResult, ordered by relevance/recency
     */
    fun searchMessages(
        query: String,
        projectId: String? = null,
        conversationId: Long? = null,
        startTimestamp: Long? = null,
        endTimestamp: Long? = null,
        limit: Int = 50
    ): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        // Try FTS5 first (BM25-ranked, tokenized, prefix matching)
        val ftsQuery = toFtsQuery(query)
        if (ftsQuery.isNotBlank()) {
            try {
                val ftsResults = searchWithFts5(ftsQuery, projectId, conversationId, startTimestamp, endTimestamp, limit)
                if (ftsResults.isNotEmpty()) return ftsResults
            } catch (_: Exception) {
                // FTS5 failed (malformed query, missing table, etc.) — fall through to LIKE
            }
        }

        // Fallback: LIKE search (always works, O(n) scan)
        return searchWithLike(query, projectId, conversationId, startTimestamp, endTimestamp, limit)
    }

    /**
     * FTS5 search with BM25 ranking.
     * Porter stemmer handles word variants: "running" matches "run".
     * Prefix matching: "mach" matches "machine", "machining", etc.
     */
    private fun searchWithFts5(
        ftsQuery: String,
        projectId: String?,
        conversationId: Long?,
        startTimestamp: Long?,
        endTimestamp: Long?,
        limit: Int
    ): List<SearchResult> {
        val messages = when {
            conversationId != null -> {
                database.messageQueries.ftsSearchInConversation(
                    MessageFts = ftsQuery,
                    conversation_id = conversationId,
                    value_ = limit.toLong()
                ).executeAsList()
            }
            projectId != null && startTimestamp != null && endTimestamp != null -> {
                database.messageQueries.ftsSearchInProjectDateRange(
                    MessageFts = ftsQuery,
                    project_id = projectId,
                    timestamp = startTimestamp,
                    timestamp_ = endTimestamp,
                    value_ = limit.toLong()
                ).executeAsList()
            }
            projectId != null -> {
                database.messageQueries.ftsSearchInProject(
                    MessageFts = ftsQuery,
                    project_id = projectId,
                    value_ = limit.toLong()
                ).executeAsList()
            }
            startTimestamp != null && endTimestamp != null -> {
                database.messageQueries.ftsSearchInDateRange(
                    MessageFts = ftsQuery,
                    timestamp = startTimestamp,
                    timestamp_ = endTimestamp,
                    value_ = limit.toLong()
                ).executeAsList()
            }
            startTimestamp != null -> {
                // FTS5 doesn't have a start-only variant — use date range with far-future end
                database.messageQueries.ftsSearchInDateRange(
                    MessageFts = ftsQuery,
                    timestamp = startTimestamp,
                    timestamp_ = Long.MAX_VALUE,
                    value_ = limit.toLong()
                ).executeAsList()
            }
            else -> {
                database.messageQueries.ftsSearchAllProjects(
                    MessageFts = ftsQuery,
                    value_ = limit.toLong()
                ).executeAsList()
            }
        }

        // BM25 ranking from FTS5 — results already ordered by relevance
        return messages.mapIndexed { index, message ->
            SearchResult(
                id = message.id,
                projectId = message.project_id,
                conversationId = message.conversation_id,
                role = message.role,
                content = message.content,
                timestamp = message.timestamp,
                tokens = message.tokens?.toInt(),
                // FTS5 BM25 gives best ordering — assign descending scores
                relevanceScore = 1.0f - (index.toFloat() / messages.size.coerceAtLeast(1))
            )
        }
    }

    /**
     * Fallback LIKE search (original O(n) implementation).
     */
    private fun searchWithLike(
        query: String,
        projectId: String?,
        conversationId: Long?,
        startTimestamp: Long?,
        endTimestamp: Long?,
        limit: Int
    ): List<SearchResult> {
        val messages = when {
            conversationId != null -> {
                database.messageQueries.searchMessagesInConversation(
                    conversation_id = conversationId,
                    value_ = query,
                    value__ = limit.toLong()
                ).executeAsList()
            }
            projectId != null && startTimestamp != null && endTimestamp != null -> {
                database.messageQueries.searchMessagesInProjectInDateRange(
                    project_id = projectId,
                    value_ = query,
                    timestamp = startTimestamp,
                    timestamp_ = endTimestamp,
                    value__ = limit.toLong()
                ).executeAsList()
            }
            projectId != null && startTimestamp != null -> {
                database.messageQueries.searchMessagesInProjectAfterTimestamp(
                    project_id = projectId,
                    value_ = query,
                    timestamp = startTimestamp,
                    value__ = limit.toLong()
                ).executeAsList()
            }
            projectId != null -> {
                database.messageQueries.searchMessagesInProject(
                    project_id = projectId,
                    value_ = query,
                    value__ = limit.toLong()
                ).executeAsList()
            }
            startTimestamp != null && endTimestamp != null -> {
                database.messageQueries.searchMessagesInDateRange(
                    value_ = query,
                    timestamp = startTimestamp,
                    timestamp_ = endTimestamp,
                    value__ = limit.toLong()
                ).executeAsList()
            }
            startTimestamp != null -> {
                database.messageQueries.searchMessagesAfterTimestamp(
                    value_ = query,
                    timestamp = startTimestamp,
                    value__ = limit.toLong()
                ).executeAsList()
            }
            else -> {
                database.messageQueries.searchMessagesAllProjects(
                    value_ = query,
                    value__ = limit.toLong()
                ).executeAsList()
            }
        }

        return messages.map { message ->
            SearchResult(
                id = message.id,
                projectId = message.project_id,
                conversationId = message.conversation_id,
                role = message.role,
                content = message.content,
                timestamp = message.timestamp,
                tokens = message.tokens?.toInt(),
                relevanceScore = calculateRelevanceScore(query, message.content)
            )
        }
            .sortedByDescending { it.relevanceScore }
            .take(limit)
    }

    /**
     * Convert user query to FTS5 syntax.
     * Strips special chars, adds prefix matching for each word.
     * "machine learning" -> "machine* OR learning*"
     */
    private fun toFtsQuery(query: String): String {
        val sanitized = query.trim().replace(Regex("[\"*():<>{}\\[\\]^~!@#\$%&]"), "")
        val words = sanitized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return ""
        val fts5Reserved = setOf("AND", "OR", "NOT", "NEAR")
        return words.joinToString(" OR ") { word ->
            if (word.uppercase() in fts5Reserved) "\"$word\"" else "$word*"
        }
    }

    /**
     * Calculate relevance score for a search result.
     *
     * Current implementation: Simple keyword matching.
     * Future enhancement: Semantic similarity with embeddings (Phase 2.1).
     *
     * @param query Search query
     * @param content Message content
     * @return Relevance score (0.0 to 1.0)
     */
    private fun calculateRelevanceScore(query: String, content: String): Float {
        val queryLower = query.lowercase()
        val contentLower = content.lowercase()

        // Exact match = highest score
        if (contentLower == queryLower) {
            return 1.0f
        }

        // Contains full query = high score
        if (contentLower.contains(queryLower)) {
            // Bonus for query appearing near start of content
            val position = contentLower.indexOf(queryLower)
            val positionScore = 1.0f - (position.toFloat() / content.length.toFloat())
            return 0.8f + (0.2f * positionScore)
        }

        // Keyword matching = medium score
        val queryWords = queryLower.split(Regex("\\s+"))
        val matchedWords = queryWords.count { word ->
            contentLower.contains(word)
        }

        val wordMatchRatio = matchedWords.toFloat() / queryWords.size.toFloat()
        return wordMatchRatio * 0.6f // Max 0.6 for partial matches
    }

    /**
     * Get search suggestions based on recent queries.
     *
     * Future enhancement (Phase 2.1).
     *
     * @param prefix Query prefix
     * @param limit Maximum suggestions
     * @return List of suggested queries
     */
    fun getSearchSuggestions(prefix: String, limit: Int = 5): List<String> {
        // TODO: Implement in Phase 2.1
        // - Track recent search queries
        // - Return most common queries matching prefix
        return emptyList()
    }

    /**
     * Get search history (recent queries).
     *
     * Future enhancement (Phase 2.1).
     *
     * @param limit Maximum history items
     * @return List of recent queries
     */
    fun getSearchHistory(limit: Int = 10): List<SearchHistoryItem> {
        // TODO: Implement in Phase 2.1
        // - Store search queries in SearchHistory table
        // - Return most recent queries
        return emptyList()
    }
}

// ==================== Data Classes ====================

/**
 * Search result with relevance scoring.
 */
data class SearchResult(
    val id: String,
    val projectId: String,
    val conversationId: Long?,
    val role: String,
    val content: String,
    val timestamp: Long,
    val tokens: Int?,
    val relevanceScore: Float
)

/**
 * Search history item (for future implementation).
 */
data class SearchHistoryItem(
    val query: String,
    val timestamp: Long,
    val resultsCount: Int
)
