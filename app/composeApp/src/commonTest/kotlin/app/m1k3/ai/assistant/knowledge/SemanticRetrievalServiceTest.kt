package app.m1k3.ai.assistant.knowledge

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.database.TriviaFact
import app.m1k3.ai.assistant.embedding.EmbeddingEngine
import app.m1k3.ai.assistant.embedding.EmbeddingTaskType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for SemanticRetrievalService - PHASE1.5-005
 *
 * Validates semantic search fixes the RAG retrieval quality problem.
 */
class SemanticRetrievalServiceTest {

    // ============================================================
    // Core Retrieval Tests
    // ============================================================

    @Test
    fun `retrieve - AI query returns AI facts with high similarity`() = runTest {
        // Setup
        val database = createMockDatabase()
        val embeddingEngine = createMockEmbeddingEngine()
        val service = SemanticRetrievalService(database, embeddingEngine)

        // Execute
        val query = "Can you teach me about AI?"
        val results = service.retrieve(query, limit = 3, minSimilarity = 0.6f)

        // Verify
        assertTrue(results.isNotEmpty(), "Should retrieve facts for AI query")

        // All results should be AI-related
        results.forEach { result ->
            assertTrue(
                result.fact.category == "ai_ml",
                "Expected AI/ML category, got ${result.fact.category}"
            )

            // High similarity (>0.7 for relevant results)
            assertTrue(
                result.similarityScore >= 0.7f,
                "Expected high similarity (>0.7), got ${result.similarityScore}"
            )
        }
    }

    @Test
    fun `retrieve - filters out low similarity results`() = runTest {
        // Setup
        val database = createMockDatabase()
        val embeddingEngine = createMockEmbeddingEngine()
        val service = SemanticRetrievalService(database, embeddingEngine)

        // Execute with high threshold
        val query = "What is quantum physics?"
        val results = service.retrieve(query, limit = 10, minSimilarity = 0.8f)

        // Verify all results meet threshold
        results.forEach { result ->
            assertTrue(
                result.similarityScore >= 0.8f,
                "All results should meet 0.8 threshold, got ${result.similarityScore}"
            )
        }
    }

    @Test
    fun `retrieve - empty query returns empty list`() = runTest {
        val database = createMockDatabase()
        val embeddingEngine = createMockEmbeddingEngine()
        val service = SemanticRetrievalService(database, embeddingEngine)

        val results = service.retrieve("", limit = 3)
        assertTrue(results.isEmpty(), "Empty query should return no results")
    }

    @Test
    fun `retrieve - respects limit parameter`() = runTest {
        val database = createMockDatabase()
        val embeddingEngine = createMockEmbeddingEngine()
        val service = SemanticRetrievalService(database, embeddingEngine)

        val results = service.retrieve("AI", limit = 2, minSimilarity = 0.5f)
        assertTrue(
            results.size <= 2,
            "Should respect limit of 2, got ${results.size}"
        )
    }

    // ============================================================
    // Relevance Calculation Tests
    // ============================================================

    @Test
    fun `relevance combines similarity and importance correctly`() {
        // High similarity (0.9) + high importance (0.8)
        val relevance1 = (0.9f * 0.7) + (0.8 * 0.3)
        assertEquals(0.87, relevance1, 0.01, "High similarity + high importance")

        // Low similarity (0.6) + low importance (0.5)
        val relevance2 = (0.6f * 0.7) + (0.5 * 0.3)
        assertEquals(0.57, relevance2, 0.01, "Low similarity + low importance")

        // Similarity weighted more heavily than importance
        assertTrue(relevance1 > relevance2, "Higher similarity should increase relevance more")
    }

    // ============================================================
    // Debug Info Tests
    // ============================================================

    @Test
    fun `getRetrievalDebugInfo - provides similarity scores`() = runTest {
        val database = createMockDatabase()
        val embeddingEngine = createMockEmbeddingEngine()
        val service = SemanticRetrievalService(database, embeddingEngine)

        val debugInfo = service.getRetrievalDebugInfo("teach me about AI", topK = 5)

        assertTrue(debugInfo.topResults.isNotEmpty(), "Should have debug results")
        assertTrue(debugInfo.queryEmbeddingDimensions > 0, "Should show embedding dimensions")
        assertTrue(debugInfo.totalFactsSearched > 0, "Should show total facts searched")

        // Check top result has high similarity
        val topResult = debugInfo.topResults.first()
        assertTrue(
            topResult.similarity >= 0.7f,
            "Top result for AI query should have high similarity"
        )
    }

    @Test
    fun `getRetrievalDebugInfo - shows threshold pass/fail`() = runTest {
        val database = createMockDatabase()
        val embeddingEngine = createMockEmbeddingEngine()
        val service = SemanticRetrievalService(database, embeddingEngine)

        val debugInfo = service.getRetrievalDebugInfo("AI", topK = 10)

        // Some results should pass threshold, some should fail
        val passed = debugInfo.topResults.count { it.thresholdPassed }
        val failed = debugInfo.topResults.count { !it.thresholdPassed }

        assertTrue(passed > 0, "Some results should pass 0.6 threshold")
        // Note: failed count depends on mock data quality
    }

    @Test
    fun `getRetrievalDebugInfo - formatSummary is readable`() = runTest {
        val database = createMockDatabase()
        val embeddingEngine = createMockEmbeddingEngine()
        val service = SemanticRetrievalService(database, embeddingEngine)

        val debugInfo = service.getRetrievalDebugInfo("AI", topK = 3)
        val summary = debugInfo.formatSummary()

        // Check summary contains expected sections
        assertTrue(summary.contains("Query:"), "Summary should show query")
        assertTrue(summary.contains("Statistics:"), "Summary should show statistics")
        assertTrue(summary.contains("Top Results:"), "Summary should show top results")
        assertTrue(summary.contains("Similarity:"), "Summary should show similarity scores")
    }

    // ============================================================
    // Comparison to Keyword-Based Retrieval
    // ============================================================

    @Test
    fun `semantic retrieval outperforms keyword for AI query`() = runTest {
        // Setup both services
        val database = createMockDatabase()
        val embeddingEngine = createMockEmbeddingEngine()
        val semanticService = SemanticRetrievalService(database, embeddingEngine)
        val keywordService = KnowledgeRetrievalService(database)

        val query = "Can you teach me about artificial intelligence?"

        // Semantic retrieval
        val semanticResults = semanticService.retrieve(query, limit = 3)

        // Keyword retrieval
        val keywordResults = keywordService.retrieve(query, limit = 3)

        // Semantic should return AI-related facts
        val semanticAICount = semanticResults.count { it.fact.category == "ai_ml" }

        // Keyword might return irrelevant results
        val keywordAICount = keywordResults.count { it.fact.category == "ai_ml" }

        assertTrue(
            semanticAICount >= keywordAICount,
            "Semantic retrieval should find more AI facts than keyword-based"
        )

        // Semantic should have higher average similarity
        val semanticAvgSim = semanticResults.map { it.similarityScore }.average()
        assertTrue(
            semanticAvgSim >= 0.7,
            "Semantic retrieval should have high average similarity (>0.7)"
        )
    }

    // ============================================================
    // Real-World Scenario Tests
    // ============================================================

    @Test
    fun `real scenario - teach me about AI retrieves AI facts`() = runTest {
        val database = createMockDatabase()
        val embeddingEngine = createMockEmbeddingEngine()
        val service = SemanticRetrievalService(database, embeddingEngine)

        val results = service.retrieve("Can you teach me about AI?", limit = 3)

        // All results should be AI-related
        results.forEach { result ->
            assertTrue(
                result.fact.category == "ai_ml" || result.fact.question.contains("AI", ignoreCase = true),
                "Expected AI-related fact, got: ${result.fact.question}"
            )
        }

        // High similarity scores
        val avgSimilarity = results.map { it.similarityScore }.average()
        assertTrue(
            avgSimilarity >= 0.75,
            "Expected high average similarity (>0.75) for AI query, got $avgSimilarity"
        )
    }

    @Test
    fun `real scenario - irrelevant facts filtered out`() = runTest {
        val database = createMockDatabase()
        val embeddingEngine = createMockEmbeddingEngine()
        val service = SemanticRetrievalService(database, embeddingEngine)

        // Query about AI should NOT return facts about online shopping
        val results = service.retrieve("What is artificial intelligence?", limit = 10)

        val shoppingFacts = results.count {
            it.fact.category == "digital_life" && it.fact.answer.contains("shopping", ignoreCase = true)
        }

        assertEquals(
            0,
            shoppingFacts,
            "AI query should not retrieve online shopping facts"
        )
    }

    // ============================================================
    // Edge Cases
    // ============================================================

    @Test
    fun `handles facts without embeddings gracefully`() = runTest {
        val database = createMockDatabase(includeUnembeddedFacts = true)
        val embeddingEngine = createMockEmbeddingEngine()
        val service = SemanticRetrievalService(database, embeddingEngine)

        // Should not crash even with null embeddings
        val results = service.retrieve("AI", limit = 3)

        // All returned facts should have valid embeddings (null ones skipped)
        results.forEach { result ->
            assertTrue(
                result.similarityScore > 0f,
                "Returned facts should have valid similarity scores"
            )
        }
    }

    @Test
    fun `fallback to keyword search when embedding fails`() = runTest {
        val database = createMockDatabase()
        val embeddingEngine = createFailingEmbeddingEngine()
        val service = SemanticRetrievalService(database, embeddingEngine)

        // Should fallback to keyword search gracefully
        val results = service.retrieve("AI", limit = 3)

        // Should still return some results via fallback
        assertTrue(results.isNotEmpty(), "Fallback should return results")

        // Check retrieval method indicates fallback
        results.forEach { result ->
            assertEquals(
                "keyword_fallback",
                result.retrievalMethod,
                "Should indicate keyword fallback"
            )
        }
    }

    // ============================================================
    // Helper Functions
    // ============================================================

    private fun createMockDatabase(includeUnembeddedFacts: Boolean = false): MaDatabase {
        // TODO: Implement mock database with AI/ML facts
        // For now, this is a placeholder
        throw NotImplementedError("Mock database not yet implemented")
    }

    private fun createMockEmbeddingEngine(): EmbeddingEngine {
        return object : EmbeddingEngine {
            override val modelName = "mock-minilm-l6"
            override val embeddingDimensions = 384
            override val maxTokens = 512
            override val isLoaded = true

            override suspend fun loadModel(): Result<Unit> = Result.success(Unit)
            override suspend fun unloadModel() {}

            override suspend fun embed(text: String, taskType: EmbeddingTaskType): Result<FloatArray> {
                // Simple mock: AI-related text gets high values, others get low
                val isAIRelated = text.contains("AI", ignoreCase = true) ||
                                  text.contains("artificial intelligence", ignoreCase = true) ||
                                  text.contains("machine learning", ignoreCase = true)

                val mockEmbedding = FloatArray(embeddingDimensions) {
                    if (isAIRelated) 0.8f else 0.2f
                }

                return Result.success(mockEmbedding)
            }

            override suspend fun embedBatch(
                texts: List<String>,
                taskType: EmbeddingTaskType
            ): Result<List<FloatArray>> {
                return Result.success(texts.map { text ->
                    embed(text, taskType).getOrThrow()
                })
            }
        }
    }

    private fun createFailingEmbeddingEngine(): EmbeddingEngine {
        return object : EmbeddingEngine {
            override val modelName = "failing-engine"
            override val embeddingDimensions = 384
            override val maxTokens = 512
            override val isLoaded = false

            override suspend fun loadModel(): Result<Unit> = Result.failure(Exception("Mock failure"))
            override suspend fun unloadModel() {}

            override suspend fun embed(text: String, taskType: EmbeddingTaskType): Result<FloatArray> {
                return Result.failure(Exception("Mock embedding failure"))
            }

            override suspend fun embedBatch(
                texts: List<String>,
                taskType: EmbeddingTaskType
            ): Result<List<FloatArray>> {
                return Result.failure(Exception("Mock batch embedding failure"))
            }
        }
    }
}
