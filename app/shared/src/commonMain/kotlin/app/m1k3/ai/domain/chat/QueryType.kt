package app.m1k3.ai.domain.chat

/**
 * Query Type - Unified query classification for adaptive generation.
 *
 * Represents the type of user query for generation configuration.
 * Maps RAG intent categories to token limits and temperature settings.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * **Usage:**
 * ```kotlin
 * val queryType = QueryType.fromIntentCategory("SCIENCE") // Returns EDUCATIONAL
 * val temp = queryType.temperature // 0.3f for focused responses
 * ```
 */
enum class QueryType {
    /**
     * Educational queries - history, science, geography, learning.
     * Requires longer, detailed responses.
     *
     * Config: Highest token count (150% base), focused temperature (0.3)
     */
    EDUCATIONAL,

    /**
     * Technical queries - code, math, debugging.
     * Requires precise, structured responses.
     *
     * Config: Higher token count (120% base), focused temperature (0.3)
     */
    TECHNICAL,

    /**
     * Factual queries - device tech, trivia, troubleshooting.
     * Requires accurate, concise responses.
     *
     * Config: Normal token count (100% base), moderate temperature (0.5)
     */
    FACTUAL,

    /**
     * Conversational queries - greetings, casual chat.
     * Allows more creative, natural responses.
     *
     * Config: Lower token count (70% base), creative temperature (0.7)
     */
    CONVERSATIONAL,

    /**
     * Creative queries - stories, poems, imaginative content.
     * Maximizes creativity and length.
     *
     * Config: Highest token count (150% base), highest temperature (0.9)
     */
    CREATIVE;

    /**
     * Token multiplier for this query type.
     * Applied to base token limit for device tier.
     */
    val tokenMultiplier: Float
        get() = when (this) {
            EDUCATIONAL -> 1.5f
            TECHNICAL -> 1.2f
            FACTUAL -> 1.0f
            CONVERSATIONAL -> 0.7f
            CREATIVE -> 1.5f
        }

    /**
     * Default temperature for this query type.
     */
    val temperature: Float
        get() = when (this) {
            EDUCATIONAL -> 0.3f
            TECHNICAL -> 0.3f
            FACTUAL -> 0.5f
            CONVERSATIONAL -> 0.7f
            CREATIVE -> 0.9f
        }

    companion object {
        /**
         * Map RAG intent category to QueryType.
         *
         * Intent categories from IntentClassifier are mapped to
         * simplified query types for configuration purposes.
         *
         * @param category Intent category string (e.g., "SCIENCE", "CODE_DEBUG")
         * @return Corresponding QueryType
         */
        fun fromIntentCategory(category: String): QueryType {
            val normalized = category.uppercase().trim()

            return when {
                normalized in EDUCATIONAL_INTENTS -> EDUCATIONAL
                normalized in TECHNICAL_INTENTS -> TECHNICAL
                normalized in FACTUAL_INTENTS -> FACTUAL
                normalized in CREATIVE_INTENTS -> CREATIVE
                else -> CONVERSATIONAL
            }
        }

        // Intent category mappings (from IntentClassifier)
        private val EDUCATIONAL_INTENTS = setOf(
            "HISTORY", "SCIENCE", "GEOGRAPHY",
            "EDUCATION", "AI_ML", "AI & ML",
            "MOVIES_TV", "MUSIC", "SPORTS",
            "FOOD", "LIFESTYLE"
        )

        private val TECHNICAL_INTENTS = setOf(
            "MATH", "CODE_DEBUG", "CODE DEBUG",
            "TECHNICAL_EXPLANATION", "TECHNICAL EXPLANATION",
            "SYSTEM"
        )

        private val FACTUAL_INTENTS = setOf(
            "DEVICE_TECH", "DEVICE TECH",
            "WIFI_NETWORK", "WIFI NETWORK",
            "SECURITY", "SECURITY_PRIVACY", "SECURITY & PRIVACY",
            "TROUBLESHOOTING", "DIAGNOSTIC",
            "TRIVIA", "TECHNOLOGY"
        )

        private val CREATIVE_INTENTS = setOf(
            "CREATIVE", "STORYTELLING", "POETRY",
            "FICTION", "IMAGINATION"
        )
    }
}
