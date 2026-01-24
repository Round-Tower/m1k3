package app.m1k3.ai.domain.rag.services

import app.m1k3.ai.domain.rag.Intent
import app.m1k3.ai.domain.rag.RetrievedFact
import app.m1k3.ai.domain.usecases.rag.RAGResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for RAGEnricherInterface contract and utilities.
 */
class RAGEnricherInterfaceTest {

    // ===== Interface Contract Tests =====

    @Test
    fun `RAGEnricherInterface can be implemented`() = runTest {
        val mockEnricher = object : RAGEnricherInterface {
            override suspend fun enrichPrompt(
                userQuery: String,
                systemPrompt: String,
                enableRAG: Boolean
            ): RAGResult {
                return RAGResult(
                    enrichedPrompt = systemPrompt,
                    intent = Intent.GENERAL,
                    confidence = 0.5f,
                    retrievedFacts = emptyList(),
                    ragApplied = false
                )
            }
        }

        val result = mockEnricher.enrichPrompt("test", "system", true)
        assertEquals(Intent.GENERAL, result.intent)
        assertFalse(result.ragApplied)
    }

    // ===== RAGResult Utility Tests =====

    @Test
    fun `formatSources returns null for empty facts`() {
        val result = RAGResult(
            enrichedPrompt = "test",
            intent = Intent.GENERAL,
            confidence = 0.5f,
            retrievedFacts = emptyList(),
            ragApplied = false
        )
        assertNull(result.formatSources())
    }

    @Test
    fun `formatSources formats facts with preview and similarity`() {
        val facts = listOf(
            RetrievedFact(
                content = "Machine learning is a subset of AI",
                category = "ai_ml_facts",
                similarity = 0.85f
            ),
            RetrievedFact(
                content = "Neural networks use layers",
                category = "ai_ml_facts",
                similarity = 0.72f
            )
        )
        val result = RAGResult(
            enrichedPrompt = "test",
            intent = Intent.AI_ML,
            confidence = 0.9f,
            retrievedFacts = facts,
            ragApplied = true
        )

        val formatted = result.formatSources()

        assertTrue(formatted != null)
        assertTrue(formatted!!.contains("1."))
        assertTrue(formatted.contains("2."))
        assertTrue(formatted.contains("85%"))
        assertTrue(formatted.contains("72%"))
    }

    @Test
    fun `formatSources truncates long content`() {
        val longContent = "A".repeat(100)
        val facts = listOf(
            RetrievedFact(
                content = longContent,
                category = "test",
                similarity = 0.8f
            )
        )
        val result = RAGResult(
            enrichedPrompt = "test",
            intent = Intent.GENERAL,
            confidence = 0.9f,
            retrievedFacts = facts,
            ragApplied = true
        )

        val formatted = result.formatSources()!!

        assertTrue(formatted.contains("..."))
        assertTrue(formatted.length < longContent.length + 50) // Should be truncated
    }

    @Test
    fun `calculateConfidence returns null for empty facts`() {
        val result = RAGResult(
            enrichedPrompt = "test",
            intent = Intent.GENERAL,
            confidence = 0.5f,
            retrievedFacts = emptyList(),
            ragApplied = false
        )
        assertNull(result.calculateConfidence())
    }

    @Test
    fun `calculateConfidence returns average similarity`() {
        val facts = listOf(
            RetrievedFact(content = "fact1", category = "test", similarity = 0.8f),
            RetrievedFact(content = "fact2", category = "test", similarity = 0.6f)
        )
        val result = RAGResult(
            enrichedPrompt = "test",
            intent = Intent.GENERAL,
            confidence = 0.9f,
            retrievedFacts = facts,
            ragApplied = true
        )

        val confidence = result.calculateConfidence()

        assertEquals(0.7, confidence!!, 0.001)
    }

    @Test
    fun `calculateConfidence handles single fact`() {
        val facts = listOf(
            RetrievedFact(content = "single", category = "test", similarity = 0.9f)
        )
        val result = RAGResult(
            enrichedPrompt = "test",
            intent = Intent.GENERAL,
            confidence = 0.9f,
            retrievedFacts = facts,
            ragApplied = true
        )

        assertEquals(0.9, result.calculateConfidence()!!, 0.001)
    }

    // ===== List<RetrievedFact> Extension Tests =====

    @Test
    fun `formatRAGSources extension returns null for empty list`() {
        val facts = emptyList<RetrievedFact>()
        assertNull(facts.formatRAGSources())
    }

    @Test
    fun `formatRAGSources extension formats list correctly`() {
        val facts = listOf(
            RetrievedFact(content = "Fact one", category = "cat1", similarity = 0.8f),
            RetrievedFact(content = "Fact two", category = "cat2", similarity = 0.7f)
        )

        val formatted = facts.formatRAGSources()!!

        assertTrue(formatted.contains("Fact one"))
        assertTrue(formatted.contains("80%"))
        assertTrue(formatted.contains("Fact two"))
        assertTrue(formatted.contains("70%"))
    }

    @Test
    fun `calculateRAGConfidence extension returns null for empty list`() {
        val facts = emptyList<RetrievedFact>()
        assertNull(facts.calculateRAGConfidence())
    }

    @Test
    fun `calculateRAGConfidence extension returns average`() {
        val facts = listOf(
            RetrievedFact(content = "a", category = "c", similarity = 0.9f),
            RetrievedFact(content = "b", category = "c", similarity = 0.7f),
            RetrievedFact(content = "c", category = "c", similarity = 0.5f)
        )

        val confidence = facts.calculateRAGConfidence()

        assertEquals(0.7, confidence!!, 0.001) // (0.9 + 0.7 + 0.5) / 3 = 0.7
    }
}
