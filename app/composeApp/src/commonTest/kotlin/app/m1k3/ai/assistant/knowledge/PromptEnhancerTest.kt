package app.m1k3.ai.assistant.knowledge

import app.m1k3.ai.assistant.database.TriviaFact
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for PromptEnhancer - PHASE1.5-006
 *
 * Validates relevance guardrails and similarity score filtering.
 *
 * ⚠️ TEMPORARILY DISABLED: Depends on SemanticRetrievalService (unimplemented mocks)
 * TODO: Re-enable after PHASE1.5 semantic retrieval is complete
 */
@Ignore("Depends on semantic retrieval infrastructure - PHASE1.5 WIP")
class PromptEnhancerTest {

    // ============================================================
    // Basic Enhancement Tests
    // ============================================================

    @Test
    fun `enhancePrompt - empty facts returns original query`() {
        val result = PromptEnhancer.enhancePrompt(
            userQuery = "What is AI?",
            retrievedFacts = emptyList()
        )

        assertEquals("What is AI?", result.enhancedQuery)
        assertFalse(result.hasKnowledge)
        assertTrue(result.ragSources.isEmpty())
    }

    @Test
    fun `enhancePrompt - includes retrieved facts`() {
        val facts = listOf(
            createMockRetrievedFact("What is AI?", "AI is artificial intelligence")
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "Tell me about AI",
            retrievedFacts = facts
        )

        assertTrue(result.hasKnowledge)
        assertTrue(result.enhancedQuery.contains("What is AI?"))
        assertTrue(result.enhancedQuery.contains("AI is artificial intelligence"))
        assertEquals(1, result.ragSources.size)
    }

    // ============================================================
    // PHASE1.5: Similarity Filtering Tests
    // ============================================================

    @Test
    fun `enhancePrompt - filters out low similarity facts`() {
        val facts = listOf(
            createMockSemanticFact("What is AI?", similarity = 0.9f),  // High
            createMockSemanticFact("Online shopping", similarity = 0.3f), // Low - should be filtered
            createMockSemanticFact("Machine learning", similarity = 0.8f) // High
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "Tell me about AI",
            retrievedFacts = facts.map { it.toRetrievedFact() },
            minSimilarity = 0.6f
        )

        // Should only include 2 facts (0.9 and 0.8), not the 0.3 one
        assertEquals(2, result.knowledgePreview.size)
        assertFalse(result.enhancedQuery.contains("Online shopping"))
    }

    @Test
    fun `enhancePrompt - all facts below threshold returns empty`() {
        val facts = listOf(
            createMockSemanticFact("Irrelevant 1", similarity = 0.3f),
            createMockSemanticFact("Irrelevant 2", similarity = 0.4f),
            createMockSemanticFact("Irrelevant 3", similarity = 0.5f)
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "Tell me about AI",
            retrievedFacts = facts.map { it.toRetrievedFact() },
            minSimilarity = 0.6f
        )

        // All facts below 0.6 threshold - should return no knowledge
        assertFalse(result.hasKnowledge)
        assertTrue(result.ragSources.isEmpty())
        assertEquals("Tell me about AI", result.enhancedQuery)
    }

    // ============================================================
    // PHASE1.5: Similarity Score Display Tests
    // ============================================================

    @Test
    fun `enhancePrompt - shows similarity scores for semantic facts`() {
        val facts = listOf(
            createMockSemanticFact("What is AI?", similarity = 0.89f)
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "Tell me about AI",
            retrievedFacts = facts.map { it.toRetrievedFact() }
        )

        // Should show similarity as percentage (89%)
        assertTrue(
            result.enhancedQuery.contains("[Relevance: 89%]"),
            "Should show similarity score as percentage"
        )
    }

    @Test
    fun `enhancePrompt - no similarity scores for keyword facts`() {
        val facts = listOf(
            createMockRetrievedFact("What is AI?", "AI is artificial intelligence")
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "Tell me about AI",
            retrievedFacts = facts
        )

        // Should not show [Relevance: X%] for non-semantic facts
        assertFalse(
            result.enhancedQuery.contains("[Relevance:"),
            "Should not show similarity for keyword-based facts"
        )
    }

    // ============================================================
    // PHASE1.5: Relevance Guardrails Tests
    // ============================================================

    @Test
    fun `enhancePrompt - includes relevance guardrails in prompt`() {
        val facts = listOf(
            createMockSemanticFact("What is AI?", similarity = 0.8f)
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "Tell me about AI",
            retrievedFacts = facts.map { it.toRetrievedFact() }
        )

        // Check for key guardrail phrases
        assertTrue(result.enhancedQuery.contains("only if it is directly relevant"))
        assertTrue(result.enhancedQuery.contains("ignore it completely"))
        assertTrue(result.enhancedQuery.contains("prioritize answering the user's actual question"))
        assertTrue(result.enhancedQuery.contains("Focus on being helpful and accurate"))
    }

    @Test
    fun `enhancePrompt - guidelines allow ignoring irrelevant knowledge`() {
        val facts = listOf(
            createMockSemanticFact("What is AI?", similarity = 0.8f)
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "Tell me about AI",
            retrievedFacts = facts.map { it.toRetrievedFact() }
        )

        // Should explicitly tell model it can ignore irrelevant knowledge
        assertTrue(result.enhancedQuery.contains("If the knowledge is not relevant, ignore it completely"))
    }

    // ============================================================
    // KnowledgePreview Tests
    // ============================================================

    @Test
    fun `knowledgePreview - includes similarity score for semantic facts`() {
        val facts = listOf(
            createMockSemanticFact("What is AI?", similarity = 0.85f)
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "Tell me about AI",
            retrievedFacts = facts.map { it.toRetrievedFact() }
        )

        val preview = result.knowledgePreview.first()
        assertEquals(0.85f, preview.similarityScore!!, 0.01f)
    }

    @Test
    fun `knowledgePreview - similarity is null for keyword facts`() {
        val facts = listOf(
            createMockRetrievedFact("What is AI?", "AI is artificial intelligence")
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "Tell me about AI",
            retrievedFacts = facts
        )

        val preview = result.knowledgePreview.first()
        assertEquals(null, preview.similarityScore)
    }

    // ============================================================
    // formatKnowledgeSummary Tests
    // ============================================================

    @Test
    fun `formatKnowledgeSummary - shows fact count and categories`() {
        val facts = listOf(
            createMockRetrievedFact("AI fact", "answer", category = "ai_ml"),
            createMockRetrievedFact("Science fact", "answer", category = "science")
        )

        val summary = PromptEnhancer.formatKnowledgeSummary(facts)

        assertTrue(summary.contains("2 facts"))
        assertTrue(summary.contains("Ai Ml"))
        assertTrue(summary.contains("Science"))
    }

    @Test
    fun `formatKnowledgeSummary - handles empty facts`() {
        val summary = PromptEnhancer.formatKnowledgeSummary(emptyList())
        assertEquals("No knowledge found", summary)
    }

    // ============================================================
    // Real-World Scenario Tests
    // ============================================================

    @Test
    fun `real scenario - AI query with mixed similarity facts`() {
        val facts = listOf(
            createMockSemanticFact("What is AI?", similarity = 0.92f),           // Relevant
            createMockSemanticFact("Online shopping tips", similarity = 0.35f),  // Irrelevant - filtered
            createMockSemanticFact("Machine learning", similarity = 0.87f),      // Relevant
            createMockSemanticFact("Problem solving", similarity = 0.45f)        // Marginal - filtered
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "Can you teach me about artificial intelligence?",
            retrievedFacts = facts.map { it.toRetrievedFact() },
            minSimilarity = 0.6f
        )

        // Should only include 2 high-similarity facts
        assertEquals(2, result.knowledgePreview.size)
        assertTrue(result.hasKnowledge)

        // Should NOT include low-similarity facts
        assertFalse(result.enhancedQuery.contains("Online shopping"))
        assertFalse(result.enhancedQuery.contains("Problem solving"))

        // Should include high-similarity facts
        assertTrue(result.enhancedQuery.contains("What is AI"))
        assertTrue(result.enhancedQuery.contains("Machine learning"))

        // Should show similarity scores
        assertTrue(result.enhancedQuery.contains("[Relevance: 92%]"))
        assertTrue(result.enhancedQuery.contains("[Relevance: 87%]"))
    }

    @Test
    fun `real scenario - backward compatible with keyword retrieval`() {
        // Non-semantic facts (legacy keyword retrieval)
        val facts = listOf(
            createMockRetrievedFact("What is AI?", "AI is artificial intelligence"),
            createMockRetrievedFact("Machine learning", "ML is a subset of AI")
        )

        val result = PromptEnhancer.enhancePrompt(
            userQuery = "Tell me about AI",
            retrievedFacts = facts
        )

        // Should work with legacy keyword facts (no filtering)
        assertTrue(result.hasKnowledge)
        assertEquals(2, result.knowledgePreview.size)

        // Should not show similarity scores (not available)
        assertFalse(result.enhancedQuery.contains("[Relevance:"))
    }

    // ============================================================
    // Helper Functions
    // ============================================================

    private fun createMockRetrievedFact(
        question: String,
        answer: String,
        category: String = "ai_ml"
    ): RetrievedFact {
        val now = System.currentTimeMillis()
        return RetrievedFact(
            fact = TriviaFact(
                id = "fact_${question.hashCode()}",
                category = category,
                question = question,
                answer = answer,
                question_variants = null,
                importance = 0.8,
                confidence = 1.0,
                access_count = 0,
                last_accessed_at = null,
                embedding_id = null,
                has_embedding = 0,
                embedding_vector = null,
                source = "test",
                created_at = now,
                updated_at = now
            ),
            relevanceScore = 0.8,
            retrievalMethod = "keyword"
        )
    }

    private fun createMockSemanticFact(
        question: String,
        similarity: Float,
        category: String = "ai_ml"
    ): SemanticRetrievedFact {
        val now = System.currentTimeMillis()
        // Mock embedding as byte array (384-dim float32 = 1536 bytes)
        val mockEmbedding = ByteArray(1536) { 0 }
        return SemanticRetrievedFact(
            fact = TriviaFact(
                id = "fact_${question.hashCode()}",
                category = category,
                question = question,
                answer = "Answer to $question",
                question_variants = null,
                importance = 0.8,
                confidence = 1.0,
                access_count = 0,
                last_accessed_at = null,
                embedding_id = "emb_${question.hashCode()}",
                has_embedding = 1,
                embedding_vector = mockEmbedding,
                source = "test",
                created_at = now,
                updated_at = now
            ),
            relevanceScore = (similarity * 0.7 + 0.8 * 0.3), // Combined score
            retrievalMethod = "semantic_embedding",
            similarityScore = similarity
        )
    }
}
