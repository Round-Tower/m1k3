package app.m1k3.ai.domain.rag.services

import app.m1k3.ai.domain.rag.KnowledgeTier
import app.m1k3.ai.domain.rag.RetrievedFact
import app.m1k3.ai.domain.rag.TieredRetrievedFact

/**
 * KnowledgeFactFilter - Domain service for filtering knowledge base facts
 *
 * **Purpose:**
 * Filters out garbage categories and applies tiered retrieval logic to
 * ensure high-quality facts are prioritized over synthetic noise.
 *
 * **Critical Fix:**
 * The `explanation_request` category contains 90 synthetic documents with
 * generic patterns like "Understanding X in Y" that match query PATTERNS
 * ("What is X?") rather than actual content. This filter removes them.
 *
 * **Tiered Retrieval:**
 * - CURATED facts have lower similarity threshold (0.5) - trust expert content
 * - SYNTHETIC facts have higher threshold (0.7) - don't trust LLM-generated
 * - Results are prioritized: Curated > Verified > Synthetic
 *
 * Domain service - Pure Kotlin, no platform dependencies.
 */
class KnowledgeFactFilter {

    /**
     * Filter facts by removing blocked categories.
     *
     * @param facts List of retrieved facts
     * @return Filtered list with blocked categories removed
     */
    fun filterFacts(facts: List<RetrievedFact>): List<RetrievedFact> {
        return facts.filter { fact ->
            !BLOCKED_CATEGORIES.contains(fact.category.lowercase())
        }
    }

    /**
     * Filter tiered facts with tier-specific thresholds.
     *
     * @param facts List of tiered facts
     * @param thresholds Map of tier to minimum similarity threshold
     * @return Filtered list passing tier-specific thresholds
     */
    fun filterFactsWithTiers(
        facts: List<TieredRetrievedFact>,
        thresholds: Map<KnowledgeTier, Float> = DEFAULT_TIER_THRESHOLDS
    ): List<TieredRetrievedFact> {
        return facts.filter { fact ->
            // First check if category is blocked
            if (BLOCKED_CATEGORIES.contains(fact.category.lowercase())) {
                return@filter false
            }

            // Then apply tier-specific threshold
            val threshold = thresholds[fact.tier] ?: DEFAULT_TIER_THRESHOLDS[fact.tier] ?: 0.5f
            fact.similarity >= threshold
        }
    }

    /**
     * Sort facts by tier priority (CURATED > VERIFIED > SYNTHETIC).
     *
     * Within same tier, sort by similarity descending.
     *
     * @param facts List of tiered facts
     * @return Sorted list with highest priority tiers first
     */
    fun sortByTierPriority(facts: List<TieredRetrievedFact>): List<TieredRetrievedFact> {
        return facts.sortedWith(
            compareByDescending<TieredRetrievedFact> { it.tier.priority }
                .thenByDescending { it.similarity }
        )
    }

    /**
     * Limit results per tier.
     *
     * @param facts List of tiered facts (should be sorted by tier priority)
     * @param limits Map of tier to max results for that tier
     * @return Limited list respecting per-tier limits
     */
    fun limitPerTier(
        facts: List<TieredRetrievedFact>,
        limits: Map<KnowledgeTier, Int> = DEFAULT_TIER_LIMITS
    ): List<TieredRetrievedFact> {
        val result = mutableListOf<TieredRetrievedFact>()
        val countByTier = mutableMapOf<KnowledgeTier, Int>()

        // Initialize counts
        KnowledgeTier.entries.forEach { tier ->
            countByTier[tier] = 0
        }

        // Sort by tier priority first, then by similarity within tier
        val sorted = sortByTierPriority(facts)

        for (fact in sorted) {
            val currentCount = countByTier[fact.tier] ?: 0
            val limit = limits[fact.tier] ?: DEFAULT_TIER_LIMITS[fact.tier] ?: 3

            if (currentCount < limit) {
                result.add(fact)
                countByTier[fact.tier] = currentCount + 1
            }
        }

        return result
    }

    /**
     * Apply full filtering pipeline:
     * 1. Remove blocked categories
     * 2. Apply tier-specific thresholds
     * 3. Sort by tier priority
     * 4. Limit per tier
     *
     * @param facts List of tiered facts
     * @param tierThresholds Tier-specific similarity thresholds
     * @param tierLimits Tier-specific result limits
     * @return Fully filtered and limited facts
     */
    fun applyFullPipeline(
        facts: List<TieredRetrievedFact>,
        tierThresholds: Map<KnowledgeTier, Float> = DEFAULT_TIER_THRESHOLDS,
        tierLimits: Map<KnowledgeTier, Int> = DEFAULT_TIER_LIMITS
    ): List<TieredRetrievedFact> {
        // Step 1 & 2: Filter blocked categories and apply thresholds
        val filtered = filterFactsWithTiers(facts, tierThresholds)

        // Step 3 & 4: Sort and limit
        return limitPerTier(filtered, tierLimits)
    }

    companion object {
        /**
         * Categories that should be completely blocked from retrieval.
         *
         * These categories contain synthetic content that matches query PATTERNS
         * rather than actual CONTENT, causing garbage results.
         */
        val BLOCKED_CATEGORIES: Set<String> = setOf(
            "explanation_request",  // 90 docs of "Understanding X in Y" garbage
            "synthetic_padding",    // Filler content
            "generic_template",     // Template-based generation
            "placeholder"           // Placeholder content
        )

        /**
         * Default similarity thresholds per tier.
         *
         * Lower threshold = more trust in that tier's content.
         * Higher threshold = less trust, needs stronger match.
         */
        val DEFAULT_TIER_THRESHOLDS: Map<KnowledgeTier, Float> = mapOf(
            KnowledgeTier.CURATED to 0.5f,   // Trust curated content
            KnowledgeTier.VERIFIED to 0.6f,  // Moderate trust
            KnowledgeTier.SYNTHETIC to 0.7f  // Don't trust synthetic - needs high similarity
        )

        /**
         * Default maximum results per tier.
         *
         * Curated gets more slots since it's higher quality.
         */
        val DEFAULT_TIER_LIMITS: Map<KnowledgeTier, Int> = mapOf(
            KnowledgeTier.CURATED to 3,   // Up to 3 curated facts
            KnowledgeTier.VERIFIED to 2,  // Up to 2 verified facts
            KnowledgeTier.SYNTHETIC to 1  // Only 1 synthetic fact (if exceptional)
        )
    }
}
