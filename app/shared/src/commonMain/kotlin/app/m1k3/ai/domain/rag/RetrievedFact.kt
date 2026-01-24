package app.m1k3.ai.domain.rag

/**
 * Retrieved Fact
 *
 * Represents a knowledge base fact retrieved via RAG (Retrieval-Augmented Generation).
 * Used by KnowledgeRepository and EnrichPromptWithRAGUseCase.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * @property content The text content of the retrieved fact
 * @property category Knowledge category (e.g., "ai_ml_facts", "science_facts")
 * @property similarity Cosine similarity to query (0.0-1.0)
 */
data class RetrievedFact(
    val content: String,
    val category: String,
    val similarity: Float
) {
    /**
     * Whether this fact is high quality (similarity >= 0.8)
     */
    val isHighQuality: Boolean
        get() = QualityTier.isHighQuality(similarity)

    /**
     * Whether this fact is medium quality (similarity >= 0.6)
     */
    val isMediumQuality: Boolean
        get() = QualityTier.isMediumQuality(similarity)

    /**
     * Quality tier for display purposes
     */
    val qualityTier: QualityTier
        get() = QualityTier.fromSimilarity(similarity)
}

/**
 * Quality tier for retrieved facts based on similarity score.
 *
 * Provides centralized quality classification logic used by
 * RetrievedFact, SemanticRetrievedFact, and other fact types.
 */
enum class QualityTier {
    HIGH,    // >= 0.8 similarity
    MEDIUM,  // >= 0.6 similarity
    LOW;     // < 0.6 similarity

    companion object {
        private const val HIGH_THRESHOLD = 0.8f
        private const val MEDIUM_THRESHOLD = 0.6f

        /**
         * Determine quality tier from similarity score.
         */
        fun fromSimilarity(similarity: Float): QualityTier = when {
            similarity >= HIGH_THRESHOLD -> HIGH
            similarity >= MEDIUM_THRESHOLD -> MEDIUM
            else -> LOW
        }

        /**
         * Check if similarity qualifies as high quality (>= 0.8)
         */
        fun isHighQuality(similarity: Float): Boolean =
            similarity >= HIGH_THRESHOLD

        /**
         * Check if similarity qualifies as medium quality or better (>= 0.6)
         */
        fun isMediumQuality(similarity: Float): Boolean =
            similarity >= MEDIUM_THRESHOLD
    }
}
