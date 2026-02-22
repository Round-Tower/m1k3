package app.m1k3.ai.domain.rag.services

import app.m1k3.ai.domain.rag.KnowledgeTier
import app.m1k3.ai.domain.rag.SemanticRetrievedFact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PromptEnhancer Tests
 *
 * Tests for RAG prompt enhancement with retrieved knowledge injection.
 */
class PromptEnhancerTest {

    // ========================================
    // enhancePrompt Tests
    // ========================================

    @Test
    fun `returns original query when no facts provided`() {
        val result = PromptEnhancer.enhancePrompt(
            userQuery = "What is AI?",
            retrievedFacts = emptyList()
        )

        assertEquals("What is AI?", result.enhancedQuery)
        assertFalse(result.hasKnowledge)
        assertTrue(result.ragSources.isEmpty())
    }

    @Test
    fun `returns original query when all facts below threshold`() {
        val lowSimilarityFacts = listOf(
            createFact("Low relevance", 0.3f)
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "What is AI?",
            retrievedFacts = lowSimilarityFacts,
            minSimilarity = 0.6f
        )

        assertEquals("What is AI?", result.enhancedQuery)
        assertFalse(result.hasKnowledge)
    }

    @Test
    fun `injects knowledge when facts above threshold`() {
        val facts = listOf(
            createFact("AI basics", 0.85f)
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "What is AI?",
            retrievedFacts = facts
        )

        assertTrue(result.hasKnowledge)
        assertTrue(result.enhancedQuery.contains("Retrieved knowledge"))
        assertTrue(result.enhancedQuery.contains("AI basics"))
        assertTrue(result.enhancedQuery.contains("What is AI?"))
    }

    @Test
    fun `includes similarity percentage in enhanced prompt`() {
        val facts = listOf(
            createFact("Fact", 0.85f)
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "Question",
            retrievedFacts = facts
        )

        assertTrue(result.enhancedQuery.contains("[Relevance: 85%]"))
    }

    @Test
    fun `filters facts by custom threshold`() {
        val facts = listOf(
            createFact("High", 0.9f),
            createFact("Medium", 0.7f),
            createFact("Low", 0.5f)
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "Test",
            retrievedFacts = facts,
            minSimilarity = 0.8f
        )

        assertEquals(1, result.ragSources.size)
        assertTrue(result.enhancedQuery.contains("High"))
        assertFalse(result.enhancedQuery.contains("Medium"))
        assertFalse(result.enhancedQuery.contains("Low"))
    }

    @Test
    fun `returns up to 3 knowledge previews`() {
        val facts = List(5) { i ->
            createFact("Fact $i", 0.9f, id = "id-$i")
        }

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "Test",
            retrievedFacts = facts
        )

        assertEquals(3, result.knowledgePreview.size)
    }

    // ========================================
    // formatKnowledgeSummary Tests
    // ========================================

    @Test
    fun `formats empty facts as no knowledge found`() {
        val summary = PromptEnhancer.formatKnowledgeSummary(emptyList())
        assertEquals("No knowledge found", summary)
    }

    @Test
    fun `formats single fact correctly`() {
        val facts = listOf(createFact("Test", 0.8f, category = "science_facts"))

        val summary = PromptEnhancer.formatKnowledgeSummary(facts)

        assertTrue(summary.contains("1 fact"))
        assertTrue(summary.contains("Science Facts"))
    }

    @Test
    fun `formats multiple facts with plural`() {
        val facts = listOf(
            createFact("A", 0.8f),
            createFact("B", 0.8f)
        )

        val summary = PromptEnhancer.formatKnowledgeSummary(facts)

        assertTrue(summary.contains("2 facts"))
    }

    // ========================================
    // formatCategory Tests
    // ========================================

    @Test
    fun `formats snake_case to Title Case`() {
        assertEquals("Science Facts", PromptEnhancer.formatCategory("science_facts"))
        assertEquals("Ai Ml", PromptEnhancer.formatCategory("ai_ml"))
        assertEquals("Device Technology", PromptEnhancer.formatCategory("device_technology"))
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createFact(
        question: String,
        similarity: Float,
        id: String = "test-id",
        category: String = "test_category"
    ): SemanticRetrievedFact {
        return SemanticRetrievedFact(
            id = id,
            question = question,
            answer = "Answer for $question",
            category = category,
            tier = KnowledgeTier.CURATED,
            similarityScore = similarity,
            relevanceScore = similarity.toDouble(),
            retrievalMethod = "test"
        )
    }
}
