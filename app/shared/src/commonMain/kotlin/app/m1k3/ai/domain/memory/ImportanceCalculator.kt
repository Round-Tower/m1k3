package app.m1k3.ai.domain.memory

import kotlin.math.abs

/**
 * Importance Calculator - Heuristic-Based Memory Scoring
 *
 * Calculates importance scores (0.0-1.0) for content based on:
 * - Content type (question, statement, code)
 * - Knowledge markers (learn, remember, important)
 * - Personal information
 * - Technical content
 * - Length and complexity
 * - Conversation context
 *
 * Higher scores = more likely to be remembered and retrieved
 *
 * Phase 2 Ticket: PHASE2-007
 * Architecture: Domain layer, pure Kotlin (no platform dependencies)
 */
open class ImportanceCalculator {

    companion object {
        // Base importance
        private const val BASELINE_SCORE = 0.5f

        // Question detection
        private const val QUESTION_BONUS = 0.15f
        private const val COMPLEX_QUESTION_BONUS = 0.1f
        private const val COMPLEX_QUESTION_THRESHOLD = 15 // words

        // Content type bonuses
        private const val CODE_BLOCK_BONUS = 0.2f
        private const val KNOWLEDGE_MARKER_BONUS = 0.15f
        private const val PERSONAL_INFO_BONUS = 0.2f
        private const val TECHNICAL_CONTENT_BONUS = 0.1f

        // Context bonuses
        private const val CURRENT_CONVERSATION_BONUS = 0.05f
        private const val TRIVIA_SHARED_BONUS = 0.1f

        // Length-based adjustments
        private const val LENGTH_VERY_SHORT = 50
        private const val LENGTH_NORMAL = 200
        private const val LENGTH_DETAILED = 500

        private const val VERY_SHORT_PENALTY = -0.1f
        private const val DETAILED_BONUS = 0.1f
        private const val VERY_DETAILED_BONUS = 0.15f

        // Question markers
        private val QUESTION_STARTERS = listOf(
            "how", "what", "why", "when", "where", "who", "which",
            "can", "could", "would", "should", "is", "are", "do", "does"
        )

        // Knowledge markers
        private val KNOWLEDGE_INDICATORS = listOf(
            "learn", "understand", "realize", "discover",
            "important", "remember", "don't forget", "do not forget",
            "key", "critical", "crucial", "essential",
            "always", "never",
            "tip", "trick", "secret", "fact", "note"
        )

        // Personal information markers
        private val PERSONAL_INDICATORS = listOf(
            "my name", "i am", "i'm", "i work", "i live",
            "i like", "i hate", "i love", "i enjoy",
            "my favorite", "my favourite",
            "years old", "year old", "born in", "from"
        )

        // Technical terms
        private val TECHNICAL_TERMS = listOf(
            "cpu", "gpu", "ram", "memory", "battery",
            "android", "ios", "linux", "windows",
            "snapdragon", "exynos", "tensor", "bionic",
            "api", "sdk", "debug", "performance",
            "storage", "algorithm", "function", "class",
            "method", "variable", "database", "cache",
            "thread", "process", "compiler", "runtime"
        )
    }

    /**
     * Calculate importance score for content
     *
     * @param content Text content to score
     * @param context Conversation context for additional signals
     * @return Importance score (0.0 to 1.0)
     */
    open fun calculateImportance(
        content: String,
        context: ConversationContext = ConversationContext()
    ): Float {
        // Handle empty/whitespace
        if (content.isBlank()) {
            return 0.1f
        }

        var score = BASELINE_SCORE

        val lowerContent = content.lowercase()

        // 1. Question detection
        if (isQuestion(content, lowerContent)) {
            score += QUESTION_BONUS

            // Complex questions get additional bonus
            if (isComplexQuestion(content)) {
                score += COMPLEX_QUESTION_BONUS
            }
        }

        // 2. Code block detection
        if (hasCodeBlock(content)) {
            score += CODE_BLOCK_BONUS
        }

        // 3. Knowledge markers
        if (hasKnowledgeMarkers(lowerContent)) {
            score += KNOWLEDGE_MARKER_BONUS
        }

        // 4. Personal information
        if (hasPersonalInfo(lowerContent)) {
            score += PERSONAL_INFO_BONUS
        }

        // 5. Technical content
        if (hasTechnicalContent(lowerContent)) {
            score += TECHNICAL_CONTENT_BONUS
        }

        // 6. Length-based adjustment
        score += calculateLengthBonus(content.length)

        // 7. Conversation context
        if (context.isCurrentConversation) {
            score += CURRENT_CONVERSATION_BONUS
        }

        if (context.triviaWasShared) {
            score += TRIVIA_SHARED_BONUS
        }

        // Clamp to [0, 1]
        return score.coerceIn(0f, 1f)
    }

    /**
     * Detect if content is a question
     */
    private fun isQuestion(content: String, lowerContent: String): Boolean {
        // Explicit question mark
        if (content.contains("?")) {
            return true
        }

        // Question starter words
        val firstWord = lowerContent.trim().split(Regex("\\s+")).firstOrNull() ?: ""
        return QUESTION_STARTERS.any { starter ->
            firstWord == starter
        }
    }

    /**
     * Detect complex questions (long, multi-clause)
     */
    private fun isComplexQuestion(content: String): Boolean {
        val wordCount = content.split(Regex("\\s+")).size
        return wordCount > COMPLEX_QUESTION_THRESHOLD
    }

    /**
     * Detect code blocks (```...```)
     */
    private fun hasCodeBlock(content: String): Boolean {
        return content.contains("```")
    }

    /**
     * Detect knowledge marker words
     */
    private fun hasKnowledgeMarkers(lowerContent: String): Boolean {
        return KNOWLEDGE_INDICATORS.any { indicator ->
            lowerContent.contains(indicator)
        }
    }

    /**
     * Detect personal information markers
     */
    private fun hasPersonalInfo(lowerContent: String): Boolean {
        return PERSONAL_INDICATORS.any { indicator ->
            lowerContent.contains(indicator)
        }
    }

    /**
     * Detect technical content
     */
    private fun hasTechnicalContent(lowerContent: String): Boolean {
        return TECHNICAL_TERMS.any { term ->
            lowerContent.contains(term)
        }
    }

    /**
     * Calculate length-based bonus/penalty
     */
    private fun calculateLengthBonus(length: Int): Float {
        return when {
            length < LENGTH_VERY_SHORT -> VERY_SHORT_PENALTY
            length < LENGTH_NORMAL -> 0f
            length < LENGTH_DETAILED -> DETAILED_BONUS
            else -> VERY_DETAILED_BONUS
        }
    }
}

/**
 * Conversation context for importance calculation
 *
 * Provides additional signals beyond content analysis:
 * - Current conversation flag (recency bias)
 * - Trivia sharing (educational content)
 * - Emotional state (Phase 5 integration)
 */
data class ConversationContext(
    val triviaWasShared: Boolean = false,
    val isCurrentConversation: Boolean = false,
    val emotionalState: EmotionalState? = null
)

/**
 * Emotional state placeholder (Phase 5)
 */
data class EmotionalState(
    val valence: Float = 0f,  // -1 (negative) to 1 (positive)
    val arousal: Float = 0f   // 0 (calm) to 1 (excited)
)
