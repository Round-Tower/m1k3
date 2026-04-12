package app.m1k3.ai.domain.tools.services

import app.m1k3.ai.domain.tools.Tool
import app.m1k3.ai.domain.tools.ToolCategory

/**
 * Tool Filter - Filters tools by query relevance for small models
 *
 * Domain service - Pure Kotlin, no platform dependencies.
 *
 * **Problem:** Small models (Gemma 270M) drown in context bloat when all tools
 * are injected (~150 tokens). This prevents the model from focusing on the actual query.
 *
 * **Solution:** Filter tools to 0-3 most relevant based on keyword and category matching.
 * Saves 67-100% of tool injection tokens (150 → 0-50 tokens).
 *
 * **Algorithm:**
 * - Extract keywords from tool ID (snake_case) and description
 * - Score relevance (0.0-1.0):
 *   - Category trigger: +0.4 (e.g., "open" → APPS category)
 *   - Keyword match: +0.2 per match, max +0.6
 * - Return top N tools sorted by score (filter out score < 0.3)
 *
 * **Examples:**
 * - "What time is it?" → get_current_time (0.8 score)
 * - "Teach me about Ireland" → NO TOOLS (0.0 score)
 * - "Open camera" → open_camera (0.8 score)
 *
 * @see ToolRegistry.getRelevantTools
 */
class ToolFilter {

    companion object {
        /**
         * Common stopwords to filter from keyword extraction.
         * These add no semantic value for tool matching.
         */
        private val STOPWORDS = setOf(
            "the", "a", "an", "to", "for", "of", "in", "on", "at",
            "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did",
            "will", "would", "should", "could", "may", "might",
            "and", "or", "but", "if", "as", "it", "this", "that"
        )

        /**
         * Minimum keyword length to consider (filters single/two-letter words)
         */
        private const val MIN_KEYWORD_LENGTH = 3

        /**
         * Minimum score threshold for including a tool in results.
         * Tools scoring below this are filtered out to prevent weak matches.
         */
        private const val MIN_SCORE_THRESHOLD = 0.3f

        /**
         * Pre-compiled regexes for performance (avoids recompiling on every query)
         */
        private val WORD_SPLIT_REGEX = Regex("\\W+")
        private val APOSTROPHE_REGEX = Regex("[''']")

        // Category trigger regexes (word boundaries)
        private val APPS_TRIGGER_REGEX = Regex("\\b(open|launch|start)\\b")
        private val DEVICE_INFO_TRIGGER_REGEX = Regex("\\b(what|current|get|show|tell)\\b")
        private val SYSTEM_TRIGGER_REGEX = Regex("\\b(set|change|turn|toggle|enable|disable|don|dont)\\b")
        private val KNOWLEDGE_TRIGGER_REGEX = Regex("\\b(search|find|look|lookup|weather|news|who|google|latest|forecast|recipe|directions|nearby)\\b")
    }

    /**
     * Filter tools by relevance to query.
     *
     * Uses keyword and category matching optimized for small models.
     * Similar pattern to IntentClassifier.
     *
     * @param query User's query (will be normalized to lowercase)
     * @param tools All available tools
     * @param maxTools Maximum tools to return (default: 3)
     * @return List of (Tool, score) pairs sorted by relevance, score >= MIN_SCORE_THRESHOLD only
     */
    fun filterByRelevance(
        query: String,
        tools: List<Tool>,
        maxTools: Int = 3
    ): List<Pair<Tool, Float>> {
        if (tools.isEmpty() || query.isBlank()) return emptyList()

        val normalizedQuery = query.lowercase().trim()

        // Score all tools
        val scored = tools.map { tool ->
            val keywords = extractToolKeywords(tool)
            val score = scoreTool(normalizedQuery, tool, keywords)
            tool to score
        }

        // Filter by minimum threshold, sort by score descending, take maxTools
        return scored
            .filter { it.second >= MIN_SCORE_THRESHOLD }
            .sortedByDescending { it.second }
            .take(maxTools)
    }

    /**
     * Extract keywords from tool for matching.
     *
     * Parses:
     * - Snake_case ID: "get_battery_level" → ["get", "battery", "level"]
     * - Description words: "Returns battery..." → ["returns", "battery", ...]
     * - Filters stopwords and short words (< 3 chars)
     *
     * @param tool The tool to extract keywords from
     * @return List of normalized keywords
     */
    fun extractToolKeywords(tool: Tool): List<String> {
        val keywords = mutableSetOf<String>()

        // Parse snake_case ID: "get_battery_level" → ["get", "battery", "level"]
        tool.id.split("_")
            .filter { it.length >= MIN_KEYWORD_LENGTH }
            .forEach { keywords.add(it.lowercase()) }

        // Extract description words (use pre-compiled regex)
        tool.description.lowercase()
            .split(WORD_SPLIT_REGEX)
            .filter { word ->
                word.length >= MIN_KEYWORD_LENGTH &&
                word !in STOPWORDS
            }
            .forEach { keywords.add(it) }

        return keywords.toList()
    }

    /**
     * Score tool relevance to query.
     *
     * Scoring algorithm:
     * - Category trigger: +0.4 points
     *   - APPS: "open|launch|start"
     *   - DEVICE_INFO: "what|current|get|show|tell"
     *   - SYSTEM: "set|change|turn|toggle|enable|disable|don't"
     * - Keyword match: +0.2 per matching keyword, max +0.6
     * - Minimum threshold: 0.3 (filters weak matches)
     * - Total: Capped at 1.0
     *
     * @param query Normalized query (lowercase)
     * @param tool Tool to score
     * @param keywords Extracted keywords for the tool
     * @return Relevance score (0.0-1.0)
     */
    fun scoreTool(query: String, tool: Tool, keywords: List<String>): Float {
        val normalizedQuery = query.lowercase()

        var score = 0.0f

        // 1. Category trigger scoring (+0.4)
        // Strip apostrophes BEFORE splitting to handle contractions properly
        // "What's the time" → "Whats the time" → ["whats", "the", "time"]
        // "don't disturb" → "dont disturb" → ["dont", "disturb"]
        val queryWithoutApostrophes = normalizedQuery.replace(APOSTROPHE_REGEX, "")
        val queryWords = queryWithoutApostrophes.split(WORD_SPLIT_REGEX)
            .filter { it.isNotEmpty() }

        score += when (tool.category) {
            ToolCategory.APPS -> {
                if (queryWords.any { it in listOf("open", "launch", "start") }) 0.4f else 0.0f
            }
            ToolCategory.DEVICE_INFO -> {
                if (queryWords.any { it in listOf("what", "whats", "current", "get", "show", "tell") }) 0.4f else 0.0f
            }
            ToolCategory.SYSTEM -> {
                if (queryWords.any { it in listOf("set", "change", "turn", "toggle", "enable", "disable", "dont") }) 0.4f else 0.0f
            }
            ToolCategory.KNOWLEDGE -> {
                if (queryWords.any { it in listOf("search", "find", "look", "lookup", "weather", "news", "who", "google", "latest", "forecast", "recipe", "directions", "nearby") }) 0.4f else 0.0f
            }
            else -> 0.0f
        }

        // 2. Keyword matching (+0.2 per match, max +0.6)
        // Use original queryWords (with MIN_LENGTH filter)
        val queryWordsFiltered = normalizedQuery.split(WORD_SPLIT_REGEX)
            .filter { it.length >= MIN_KEYWORD_LENGTH }

        val matchCount = keywords.count { keyword ->
            queryWordsFiltered.any { word ->
                // Exact match or query word starts with keyword
                // Removed bidirectional prefix matching to fix false positives
                // ("timer" no longer matches "time")
                word == keyword || word.startsWith(keyword)
            }
        }

        score += (matchCount * 0.2f).coerceAtMost(0.6f)

        // 3. Cap at 1.0
        return score.coerceAtMost(1.0f)
    }
}
