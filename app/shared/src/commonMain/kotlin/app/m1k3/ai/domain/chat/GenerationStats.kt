package app.m1k3.ai.domain.chat

/**
 * Generation Statistics
 *
 * Performance metrics for AI generation.
 * Used by ChatScreenViewModel and UI components.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * @property tokenCount Number of tokens generated
 * @property durationMs Generation duration in milliseconds
 * @property tokensPerSecond Tokens per second throughput
 * @property ragInfo RAG retrieval info (if RAG was used)
 * @property ragSources RAG sources (if RAG was used)
 * @property ragConfidence RAG confidence score (if RAG was used)
 */
data class GenerationStats(
    val tokenCount: Int,
    val durationMs: Long,
    val tokensPerSecond: Float,
    val ragInfo: String? = null,
    val ragSources: String? = null,
    val ragConfidence: Double? = null
) {
    /**
     * Format speed as tokens/second string.
     */
    fun formatSpeed(): String = "%.1f tok/s".format(tokensPerSecond)

    /**
     * Format duration for display.
     */
    fun formatDuration(): String = when {
        durationMs >= 1000 -> "%.1fs".format(durationMs / 1000.0)
        else -> "${durationMs}ms"
    }

    /**
     * Format full stats string for display.
     */
    fun formatFull(): String = "⚡ $tokenCount tokens in ${formatDuration()} (${formatSpeed()})"

    /**
     * Whether RAG was used in generation
     */
    val usedRAG: Boolean
        get() = ragInfo != null || ragSources != null

    /**
     * Whether this is a fast generation (>= 10 t/s)
     */
    val isFast: Boolean
        get() = tokensPerSecond >= 10f

    /**
     * Performance tier
     */
    val performanceTier: PerformanceTier
        get() = when {
            tokensPerSecond >= 20f -> PerformanceTier.EXCELLENT
            tokensPerSecond >= 10f -> PerformanceTier.GOOD
            tokensPerSecond >= 5f -> PerformanceTier.ACCEPTABLE
            else -> PerformanceTier.SLOW
        }
}

/**
 * Performance tier classification
 */
enum class PerformanceTier {
    EXCELLENT,   // >= 20 t/s
    GOOD,        // >= 10 t/s
    ACCEPTABLE,  // >= 5 t/s
    SLOW         // < 5 t/s
}
