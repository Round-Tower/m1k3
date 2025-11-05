package app.m1k3.ai.assistant.rag

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.embedding.EmbeddingEngine
import app.m1k3.ai.assistant.embedding.EmbeddingTaskType
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for RAGManager - RAG orchestration with intent-aware retrieval
 *
 * Validates prompt enrichment, metadata formatting, and end-to-end RAG flow.
 */
class RAGManagerTest {

    private lateinit var mockDatabase: MockMaDatabase
    private lateinit var mockEmbedding: MockEmbeddingEngine
    private lateinit var ragManager: RAGManager

    @BeforeTest
    fun setup() {
        mockDatabase = MockMaDatabase()
        mockEmbedding = MockEmbeddingEngine()
        ragManager = RAGManager(mockDatabase, mockEmbedding)
    }

    // ========================================
    // enrichPrompt() Tests
    // ========================================

    @Test
    fun `enrichPrompt with RAG disabled returns original prompt`() = runTest {
        val result = ragManager.enrichPrompt(
            userQuery = "Test query",
            systemPrompt = "You are an AI assistant",
            enableRAG = false
        )

        assertEquals("You are an AI assistant", result.enrichedPrompt)
        assertEquals(IntentClassifier.Intent.GENERAL, result.intent)
        assertEquals(0f, result.confidence)
        assertTrue(result.retrievedFacts.isEmpty())
        assertFalse(result.ragApplied)
    }

    @Test
    fun `enrichPrompt with empty query returns original prompt`() = runTest {
        val result = ragManager.enrichPrompt(
            userQuery = "",
            systemPrompt = "You are an AI assistant",
            enableRAG = true
        )

        assertEquals("You are an AI assistant", result.enrichedPrompt)
        assertFalse(result.ragApplied)
    }

    @Test
    fun `enrichPrompt with blank query returns original prompt`() = runTest {
        val result = ragManager.enrichPrompt(
            userQuery = "   ",
            systemPrompt = "You are an AI assistant",
            enableRAG = true
        )

        assertEquals("You are an AI assistant", result.enrichedPrompt)
        assertFalse(result.ragApplied)
    }

    @Test
    fun `enrichPrompt with conversational query skips retrieval`() = runTest {
        val result = ragManager.enrichPrompt(
            userQuery = "Hello, how are you?",
            systemPrompt = "You are an AI assistant",
            enableRAG = true
        )

        assertEquals(IntentClassifier.Intent.CONVERSATIONAL, result.intent)
        assertTrue(result.confidence > 0f)
        assertTrue(result.retrievedFacts.isEmpty())
        assertFalse(result.ragApplied)
    }

    @Test
    fun `enrichPrompt with general query skips retrieval`() = runTest {
        val result = ragManager.enrichPrompt(
            userQuery = "random text without keywords",
            systemPrompt = "You are an AI assistant",
            enableRAG = true
        )

        assertEquals(IntentClassifier.Intent.GENERAL, result.intent)
        assertTrue(result.retrievedFacts.isEmpty())
        assertFalse(result.ragApplied)
    }

    @Test
    fun `enrichPrompt with device query retrieves facts and enriches prompt`() = runTest {
        // Setup mock database with device facts
        mockDatabase.addFact(
            id = "fact1",
            category = "device_technology",
            question = "How to fix battery drain?",
            answer = "Check background apps, reduce brightness, disable location services",
            importance = 0.9
        )

        mockDatabase.addFact(
            id = "fact2",
            category = "device_technology",
            question = "How to prevent overheating?",
            answer = "Avoid direct sunlight, close unused apps, remove phone case",
            importance = 0.85
        )

        val result = ragManager.enrichPrompt(
            userQuery = "My phone battery drains too fast",
            systemPrompt = "You are a helpful AI assistant",
            enableRAG = true
        )

        assertEquals(IntentClassifier.Intent.DEVICE_TECH, result.intent)
        assertTrue(result.confidence > 0.7f, "Device query should have high confidence")
        assertFalse(result.retrievedFacts.isEmpty(), "Should retrieve device facts")
        assertTrue(result.ragApplied, "RAG should be applied")
        assertTrue(result.enrichedPrompt.contains("Relevant Knowledge"), "Should have knowledge section")
        assertTrue(result.enrichedPrompt.contains("You are a helpful AI assistant"), "Should include original system prompt")
    }

    @Test
    fun `enrichPrompt retrieves correct number of facts based on intent`() = runTest {
        // Add 10 trivia facts
        repeat(10) { i ->
            mockDatabase.addFact(
                id = "trivia_$i",
                category = "trivia_fun_facts",
                question = "Trivia question $i",
                answer = "Trivia answer $i",
                importance = 0.8
            )
        }

        val result = ragManager.enrichPrompt(
            userQuery = "Tell me an interesting fact",
            systemPrompt = "You are an AI",
            enableRAG = true
        )

        assertEquals(IntentClassifier.Intent.TRIVIA, result.intent)
        assertEquals(1, result.retrievedFacts.size, "Trivia should retrieve exactly 1 fact")
    }

    @Test
    fun `enrichPrompt with no relevant facts returns original prompt`() = runTest {
        // Database is empty, no facts to retrieve

        val result = ragManager.enrichPrompt(
            userQuery = "My phone battery drains quickly",
            systemPrompt = "You are an AI",
            enableRAG = true
        )

        assertEquals(IntentClassifier.Intent.DEVICE_TECH, result.intent)
        assertTrue(result.retrievedFacts.isEmpty())
        assertFalse(result.ragApplied)
        assertEquals("You are an AI", result.enrichedPrompt, "Should return original prompt when no facts found")
    }

    // ========================================
    // formatRAGSources() Tests
    // ========================================

    @Test
    fun `formatRAGSources returns null for empty list`() {
        val result = ragManager.formatRAGSources(emptyList())
        assertNull(result)
    }

    @Test
    fun `formatRAGSources formats single fact correctly`() {
        val facts = listOf(
            RAGManager.RetrievedFact(
                content = "Some content",
                category = "Device Technology",
                similarity = 0.85f
            )
        )

        val result = ragManager.formatRAGSources(facts)

        assertEquals("Device Technology (85%)", result)
    }

    @Test
    fun `formatRAGSources formats multiple facts correctly`() {
        val facts = listOf(
            RAGManager.RetrievedFact("content1", "Device Technology", 0.85f),
            RAGManager.RetrievedFact("content2", "Security & Privacy", 0.72f),
            RAGManager.RetrievedFact("content3", "WiFi & Networking", 0.68f)
        )

        val result = ragManager.formatRAGSources(facts)

        assertEquals("Device Technology (85%); Security & Privacy (72%); WiFi & Networking (68%)", result)
    }

    @Test
    fun `formatRAGSources rounds similarity correctly`() {
        val facts = listOf(
            RAGManager.RetrievedFact("content", "Category", 0.8749f)  // Should round down to 87%
        )

        val result = ragManager.formatRAGSources(facts)

        assertEquals("Category (87%)", result)
    }

    // ========================================
    // calculateRAGConfidence() Tests
    // ========================================

    @Test
    fun `calculateRAGConfidence returns null for empty list`() {
        val result = ragManager.calculateRAGConfidence(emptyList())
        assertNull(result)
    }

    @Test
    fun `calculateRAGConfidence calculates average correctly for single fact`() {
        val facts = listOf(
            RAGManager.RetrievedFact("content", "category", 0.85f)
        )

        val result = ragManager.calculateRAGConfidence(facts)

        assertEquals(0.85, result!!, 0.001)
    }

    @Test
    fun `calculateRAGConfidence calculates average correctly for multiple facts`() {
        val facts = listOf(
            RAGManager.RetrievedFact("content1", "cat1", 0.90f),
            RAGManager.RetrievedFact("content2", "cat2", 0.80f),
            RAGManager.RetrievedFact("content3", "cat3", 0.70f)
        )

        val result = ragManager.calculateRAGConfidence(facts)

        assertEquals(0.80, result!!, 0.001, "Average of 0.90, 0.80, 0.70 should be 0.80")
    }

    // ========================================
    // testRAG() Tests
    // ========================================

    @Test
    fun `testRAG returns summary for each query`() = runTest {
        mockDatabase.addFact(
            id = "test_fact",
            category = "device_technology",
            question = "Test question",
            answer = "Test answer",
            importance = 0.9
        )

        val queries = listOf(
            "My phone battery dies fast",
            "Hello there",
            "Tell me a fact"
        )

        val results = ragManager.testRAG(queries)

        assertEquals(3, results.size)
        assertTrue(results.containsKey("My phone battery dies fast"))
        assertTrue(results.containsKey("Hello there"))
        assertTrue(results.containsKey("Tell me a fact"))

        // Check summary format
        val deviceSummary = results["My phone battery dies fast"]!!
        assertTrue(deviceSummary.contains("Intent:"))
        assertTrue(deviceSummary.contains("%"))
    }

    // ========================================
    // Mock Classes
    // ========================================

    /**
     * Mock MaDatabase for testing
     */
    private class MockMaDatabase : MaDatabase {
        private val facts = mutableListOf<MockFact>()

        data class MockFact(
            val id: String,
            val category: String,
            val question: String,
            val answer: String,
            val importance: Double,
            val embedding: FloatArray? = null
        )

        fun addFact(id: String, category: String, question: String, answer: String, importance: Double) {
            facts.add(MockFact(id, category, question, answer, importance, FloatArray(384) { 0.5f }))
        }

        override val triviaFactQueries: Any
            get() = object {
                fun getFactsWithEmbeddings(): Any = object {
                    fun executeAsList(): List<Any> {
                        return facts.map { fact ->
                            object {
                                val id = fact.id
                                val category = fact.category
                                val question = fact.question
                                val answer = fact.answer
                                val importance = fact.importance
                                val embedding: ByteArray? = fact.embedding?.let { floatArrayToBytes(it) }
                            }
                        }
                    }
                }
            }

        override val messageQueries: Any get() = TODO()
        override val conversationQueries: Any get() = TODO()
        override val ecoMetricsQueries: Any get() = TODO()
        override val projectQueries: Any get() = TODO()
        override val memoryMetadataQueries: Any get() = TODO()

        private fun floatArrayToBytes(floats: FloatArray): ByteArray {
            val bytes = ByteArray(floats.size * 4)
            for (i in floats.indices) {
                val bits = floats[i].toBits()
                bytes[i * 4] = (bits shr 24).toByte()
                bytes[i * 4 + 1] = (bits shr 16).toByte()
                bytes[i * 4 + 2] = (bits shr 8).toByte()
                bytes[i * 4 + 3] = bits.toByte()
            }
            return bytes
        }
    }

    /**
     * Mock EmbeddingEngine for testing
     */
    private class MockEmbeddingEngine : EmbeddingEngine {
        override val modelName: String = "MockEmbedding"
        override val embeddingDimension: Int = 384
        override val isReady: Boolean = true

        override suspend fun embed(text: String, taskType: EmbeddingTaskType): Result<FloatArray> {
            // Return mock embeddings (all 0.5)
            return Result.success(FloatArray(384) { 0.5f })
        }

        override fun close() {
            // No-op
        }
    }
}
