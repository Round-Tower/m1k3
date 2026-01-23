package app.m1k3.ai.domain.rag

/**
 * Knowledge Tier - Source quality classification
 *
 * Represents the quality tier of knowledge base content based on its source.
 * Used for tiered retrieval where curated content is prioritized over synthetic.
 *
 * **Tier Hierarchy:**
 * - CURATED: Hand-crafted expert knowledge (highest priority)
 * - VERIFIED: Community-verified or reviewed synthetic content
 * - SYNTHETIC: LLM-generated content (lowest priority, highest threshold)
 *
 * **Why Tiers Matter:**
 * Synthetic content often contains:
 * - Generic patterns ("Understanding X in Y")
 * - Semantically shallow text that matches query PATTERNS not CONTENT
 * - Lower factual accuracy
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 */
enum class KnowledgeTier(val priority: Int) {
    /**
     * Hand-crafted expert knowledge.
     * Examples: ai_ml_knowledge.json, curated FAQ
     * Priority: Highest (100)
     * Threshold: Lowest (0.5) - trust curated content
     */
    CURATED(priority = 100),

    /**
     * Community-verified or reviewed synthetic content.
     * Examples: Reviewed trivia, validated Q&A
     * Priority: Medium (50)
     * Threshold: Medium (0.6)
     */
    VERIFIED(priority = 50),

    /**
     * LLM-generated or bulk-imported content.
     * Examples: comprehensive_knowledge_base.json synthetic docs
     * Priority: Lowest (10)
     * Threshold: Highest (0.7) - don't trust synthetic content
     */
    SYNTHETIC(priority = 10);

    companion object {
        /**
         * Get tier from string, defaulting to SYNTHETIC for unknown values.
         */
        fun fromString(value: String): KnowledgeTier {
            return entries.find { it.name.equals(value, ignoreCase = true) }
                ?: SYNTHETIC
        }
    }
}

/**
 * Retrieved fact with tier information for tiered retrieval.
 *
 * Extends the base RetrievedFact concept with tier metadata for
 * tier-aware filtering and prioritization.
 */
data class TieredRetrievedFact(
    val content: String,
    val category: String,
    val similarity: Float,
    val tier: KnowledgeTier
) {
    /**
     * Convert to base RetrievedFact (loses tier info).
     */
    fun toRetrievedFact(): RetrievedFact {
        return RetrievedFact(
            content = content,
            category = category,
            similarity = similarity
        )
    }

    companion object {
        /**
         * Create from base RetrievedFact with tier.
         */
        fun fromRetrievedFact(fact: RetrievedFact, tier: KnowledgeTier): TieredRetrievedFact {
            return TieredRetrievedFact(
                content = fact.content,
                category = fact.category,
                similarity = fact.similarity,
                tier = tier
            )
        }
    }
}
