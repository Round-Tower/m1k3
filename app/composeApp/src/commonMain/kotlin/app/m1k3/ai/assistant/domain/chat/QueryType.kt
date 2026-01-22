package app.m1k3.ai.assistant.domain.chat

/**
 * Query Type
 *
 * Represents the type of user query for adaptive generation configuration.
 * Used by GenerationConfigService to adjust token limits and temperature.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 */
enum class QueryType {
    /**
     * Conversational queries - greetings, small talk
     *
     * Config: Lower token count (50% base), higher temperature (0.8)
     */
    CONVERSATIONAL,

    /**
     * Technical queries - code, math, debugging
     *
     * Config: Higher token count (120% base), lower temperature (0.3)
     */
    TECHNICAL,

    /**
     * Creative queries - stories, poems, generation
     *
     * Config: Highest token count (150% base), highest temperature (0.9)
     */
    CREATIVE,

    /**
     * Factual queries - question answering, explanations
     *
     * Config: Normal token count (100% base), moderate temperature (0.5)
     */
    FACTUAL;

    /**
     * Token multiplier for this query type
     */
    val tokenMultiplier: Float
        get() = when (this) {
            CONVERSATIONAL -> 0.5f
            TECHNICAL -> 1.2f
            CREATIVE -> 1.5f
            FACTUAL -> 1.0f
        }

    /**
     * Temperature value for this query type
     */
    val temperature: Float
        get() = when (this) {
            CONVERSATIONAL -> 0.8f
            TECHNICAL -> 0.3f
            CREATIVE -> 0.9f
            FACTUAL -> 0.5f
        }
}
