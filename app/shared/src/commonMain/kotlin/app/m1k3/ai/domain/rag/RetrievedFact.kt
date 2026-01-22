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
        get() = similarity >= 0.8f

    /**
     * Whether this fact is medium quality (similarity >= 0.6)
     */
    val isMediumQuality: Boolean
        get() = similarity >= 0.6f

    /**
     * Quality tier for display purposes
     */
    val qualityTier: QualityTier
        get() = when {
            similarity >= 0.8f -> QualityTier.HIGH
            similarity >= 0.6f -> QualityTier.MEDIUM
            else -> QualityTier.LOW
        }
}

/**
 * Quality tier for retrieved facts
 */
enum class QualityTier {
    HIGH,    // >= 0.8 similarity
    MEDIUM,  // >= 0.6 similarity
    LOW      // < 0.6 similarity
}
