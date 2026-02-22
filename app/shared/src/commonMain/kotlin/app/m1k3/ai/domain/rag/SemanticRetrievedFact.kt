package app.m1k3.ai.domain.rag

/**
 * Semantic Retrieved Fact
 *
 * Domain entity representing a knowledge base fact retrieved via semantic search.
 * Extends the basic RetrievedFact concept with tier and relevance metadata.
 *
 * **Use Cases:**
 * - Tiered retrieval: CURATED facts prioritized over SYNTHETIC
 * - Relevance ranking: Combined similarity + importance scoring
 * - Debug/transparency: Track retrieval method and scores
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * @property id Unique identifier for the fact
 * @property question The question or prompt text
 * @property answer The answer or content text
 * @property category Knowledge category (e.g., "ai_ml", "science_facts")
 * @property tier Knowledge tier (CURATED > VERIFIED > SYNTHETIC)
 * @property similarityScore Cosine similarity to query (0.0-1.0)
 * @property relevanceScore Combined relevance score (similarity * 0.7 + importance * 0.3)
 * @property retrievalMethod How this fact was retrieved (e.g., "tiered_semantic", "keyword_fallback")
 */
data class SemanticRetrievedFact(
    val id: String,
    val question: String,
    val answer: String,
    val category: String,
    val tier: KnowledgeTier,
    val similarityScore: Float,
    val relevanceScore: Double,
    val retrievalMethod: String
) {
    /**
     * Convert to basic RetrievedFact for backward compatibility.
     */
    fun toRetrievedFact(): RetrievedFact {
        return RetrievedFact(
            content = "$question\n$answer",
            category = category,
            similarity = similarityScore
        )
    }

    /**
     * Whether this fact is high quality (similarity >= 0.8)
     */
    val isHighQuality: Boolean
        get() = QualityTier.isHighQuality(similarityScore)

    /**
     * Quality tier based on similarity score
     */
    val qualityTier: QualityTier
        get() = QualityTier.fromSimilarity(similarityScore)
}
