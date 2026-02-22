package app.m1k3.ai.domain.rag.services

import app.m1k3.ai.domain.rag.KnowledgeTier
import app.m1k3.ai.domain.rag.RetrievedFact
import app.m1k3.ai.domain.rag.TieredRetrievedFact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * KnowledgeFactFilter Tests - TDD Red Phase
 *
 * Tests for filtering out garbage categories and applying tiered retrieval.
 *
 * **Critical Fix:** The `explanation_request` category contains 90 synthetic
 * documents with generic patterns like "Understanding X in Y" that match
 * query PATTERNS ("What is X?") rather than actual content.
 *
 * **Success Criteria:**
 * - Filter out `explanation_request` category entirely
 * - Apply tier-based similarity thresholds
 * - Prioritize curated facts over synthetic
 * - Limit results per tier
 */
class KnowledgeFactFilterTest {

    private val filter = KnowledgeFactFilter()

    // ========================================
    // Blocked Category Tests (CRITICAL)
    // ========================================

    @Test
    fun `filters out explanation_request category entirely`() {
        // Given: Facts including garbage explanation_request category
        val facts = listOf(
            createFact("AI definition", "ai_ml_facts", 0.85f),
            createFact("Understanding data structures", "explanation_request", 0.90f), // Higher similarity but garbage
            createFact("Neural network basics", "ai_ml_facts", 0.80f)
        )

        // When: Filter facts
        val filtered = filter.filterFacts(facts)

        // Then: explanation_request is removed despite higher similarity
        assertEquals(2, filtered.size)
        assertTrue(filtered.none { it.category == "explanation_request" })
        assertTrue(filtered.all { it.category == "ai_ml_facts" })
    }

    @Test
    fun `filters out all blocked categories`() {
        // Given: Facts with various blocked categories
        val facts = listOf(
            createFact("Good fact", "science_facts", 0.85f),
            createFact("Bad fact 1", "explanation_request", 0.90f),
            createFact("Bad fact 2", "synthetic_padding", 0.88f),
            createFact("Bad fact 3", "generic_template", 0.87f)
        )

        // When: Filter facts
        val filtered = filter.filterFacts(facts)

        // Then: Only non-blocked facts remain
        assertEquals(1, filtered.size)
        assertEquals("science_facts", filtered.first().category)
    }

    @Test
    fun `returns empty list if all facts are blocked`() {
        // Given: Only garbage facts
        val facts = listOf(
            createFact("Bad 1", "explanation_request", 0.95f),
            createFact("Bad 2", "explanation_request", 0.90f)
        )

        // When: Filter facts
        val filtered = filter.filterFacts(facts)

        // Then: Empty list
        assertTrue(filtered.isEmpty())
    }

    // ========================================
    // Tier-Based Threshold Tests
    // ========================================

    @Test
    fun `applies higher threshold for synthetic tier`() {
        // Given: Facts with tier information
        val facts = listOf(
            createFactWithTier("Curated fact", "ai_ml_facts", 0.55f, KnowledgeTier.CURATED),
            createFactWithTier("Synthetic fact", "ai_ml_facts", 0.65f, KnowledgeTier.SYNTHETIC)
        )

        // When: Filter with tier-aware thresholds
        // Curated threshold: 0.5, Synthetic threshold: 0.7
        val filtered = filter.filterFactsWithTiers(
            facts,
            thresholds = mapOf<KnowledgeTier, Float>(
                KnowledgeTier.CURATED to 0.5f,
                KnowledgeTier.VERIFIED to 0.6f,
                KnowledgeTier.SYNTHETIC to 0.7f
            )
        )

        // Then: Curated passes (0.55 >= 0.5), Synthetic fails (0.65 < 0.7)
        assertEquals(1, filtered.size)
        assertEquals(KnowledgeTier.CURATED, filtered.first().tier)
    }

    @Test
    fun `curated tier has lower threshold than synthetic`() {
        // Given: Same similarity, different tiers
        val curated = createFactWithTier("Curated", "ai_ml_facts", 0.55f, KnowledgeTier.CURATED)
        val synthetic = createFactWithTier("Synthetic", "ai_ml_facts", 0.55f, KnowledgeTier.SYNTHETIC)

        // When: Filter with default thresholds
        val curatedResult = filter.filterFactsWithTiers(listOf(curated))
        val syntheticResult = filter.filterFactsWithTiers(listOf(synthetic))

        // Then: Curated passes, synthetic fails at same similarity
        assertEquals(1, curatedResult.size)
        assertTrue(syntheticResult.isEmpty())
    }

    // ========================================
    // Tier Priority Tests
    // ========================================

    @Test
    fun `prioritizes curated over verified over synthetic`() {
        // Given: Facts from different tiers with same similarity
        val facts = listOf(
            createFactWithTier("Synthetic", "ai_ml_facts", 0.80f, KnowledgeTier.SYNTHETIC),
            createFactWithTier("Curated", "ai_ml_facts", 0.80f, KnowledgeTier.CURATED),
            createFactWithTier("Verified", "ai_ml_facts", 0.80f, KnowledgeTier.VERIFIED)
        )

        // When: Sort by tier priority
        val sorted = filter.sortByTierPriority(facts)

        // Then: Curated first, then Verified, then Synthetic
        assertEquals(KnowledgeTier.CURATED, sorted[0].tier)
        assertEquals(KnowledgeTier.VERIFIED, sorted[1].tier)
        assertEquals(KnowledgeTier.SYNTHETIC, sorted[2].tier)
    }

    @Test
    fun `limits results per tier`() {
        // Given: Multiple facts per tier
        val facts = listOf(
            createFactWithTier("C1", "ai_ml_facts", 0.90f, KnowledgeTier.CURATED),
            createFactWithTier("C2", "ai_ml_facts", 0.85f, KnowledgeTier.CURATED),
            createFactWithTier("C3", "ai_ml_facts", 0.80f, KnowledgeTier.CURATED),
            createFactWithTier("C4", "ai_ml_facts", 0.75f, KnowledgeTier.CURATED),
            createFactWithTier("V1", "ai_ml_facts", 0.88f, KnowledgeTier.VERIFIED),
            createFactWithTier("V2", "ai_ml_facts", 0.82f, KnowledgeTier.VERIFIED),
            createFactWithTier("V3", "ai_ml_facts", 0.77f, KnowledgeTier.VERIFIED),
            createFactWithTier("S1", "ai_ml_facts", 0.92f, KnowledgeTier.SYNTHETIC),
            createFactWithTier("S2", "ai_ml_facts", 0.87f, KnowledgeTier.SYNTHETIC)
        )

        // When: Limit per tier (Curated: 3, Verified: 2, Synthetic: 1)
        val limited = filter.limitPerTier(
            facts,
            limits = mapOf<KnowledgeTier, Int>(
                KnowledgeTier.CURATED to 3,
                KnowledgeTier.VERIFIED to 2,
                KnowledgeTier.SYNTHETIC to 1
            )
        )

        // Then: 3 + 2 + 1 = 6 facts max
        assertEquals(6, limited.size)
        assertEquals(3, limited.count { it.tier == KnowledgeTier.CURATED })
        assertEquals(2, limited.count { it.tier == KnowledgeTier.VERIFIED })
        assertEquals(1, limited.count { it.tier == KnowledgeTier.SYNTHETIC })
    }

    // ========================================
    // Combined Filter Pipeline Tests
    // ========================================

    @Test
    fun `full pipeline filters blocks then applies tiers`() {
        // Given: Mixed facts including blocked categories
        val facts = listOf(
            createFactWithTier("Good curated", "ai_ml_facts", 0.75f, KnowledgeTier.CURATED),
            createFactWithTier("Bad explanation", "explanation_request", 0.95f, KnowledgeTier.SYNTHETIC),
            createFactWithTier("Good synthetic", "science_facts", 0.85f, KnowledgeTier.SYNTHETIC)
        )

        // When: Full pipeline
        val result = filter.applyFullPipeline(
            facts,
            tierThresholds = mapOf<KnowledgeTier, Float>(
                KnowledgeTier.CURATED to 0.5f,
                KnowledgeTier.SYNTHETIC to 0.7f
            ),
            tierLimits = mapOf<KnowledgeTier, Int>(
                KnowledgeTier.CURATED to 3,
                KnowledgeTier.SYNTHETIC to 1
            )
        )

        // Then: Blocked removed, thresholds applied, limits respected
        assertEquals(2, result.size)
        assertTrue(result.none { it.category == "explanation_request" })
    }

    // ========================================
    // Default Blocked Categories Tests
    // ========================================

    @Test
    fun `blockedCategories includes explanation_request`() {
        assertTrue(KnowledgeFactFilter.BLOCKED_CATEGORIES.contains("explanation_request"))
    }

    @Test
    fun `default thresholds favor curated content`() {
        val defaults = KnowledgeFactFilter.DEFAULT_TIER_THRESHOLDS
        assertTrue(defaults[KnowledgeTier.CURATED]!! < defaults[KnowledgeTier.SYNTHETIC]!!)
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createFact(
        content: String,
        category: String,
        similarity: Float
    ): RetrievedFact {
        return RetrievedFact(
            content = content,
            category = category,
            similarity = similarity
        )
    }

    private fun createFactWithTier(
        content: String,
        category: String,
        similarity: Float,
        tier: KnowledgeTier
    ): TieredRetrievedFact {
        return TieredRetrievedFact(
            content = content,
            category = category,
            similarity = similarity,
            tier = tier
        )
    }
}
