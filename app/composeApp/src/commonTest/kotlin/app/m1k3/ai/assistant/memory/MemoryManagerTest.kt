package app.m1k3.ai.assistant.memory

import app.m1k3.ai.assistant.database.MemoryMetadata
import app.m1k3.ai.assistant.domain.memory.ConversationContext
import app.m1k3.ai.assistant.domain.memory.ImportanceCalculator
import app.m1k3.ai.assistant.test.TestDatabaseFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for MemoryManager
 *
 * Validates orchestration of chunking, embedding, storage, and retrieval.
 */
class MemoryManagerTest {

    private lateinit var database: app.m1k3.ai.assistant.database.MaDatabase
    private lateinit var repository: MemoryRepository
    private lateinit var chunker: SemanticChunker
    private lateinit var importanceCalculator: ImportanceCalculator
    private lateinit var contextAssembler: ContextAssembler
    private lateinit var memoryManager: MemoryManager
    private lateinit var mockEmbeddingEngine: MockEmbeddingEngine
    private lateinit var mockVectorSearch: MockVectorSearchEngine

    @BeforeTest
    fun setup() {
        database = TestDatabaseFactory.createInMemoryDatabase()
        repository = MemoryRepository(database)
        chunker = SemanticChunker(SimpleTokenCounter())
        importanceCalculator = ImportanceCalculator()
        contextAssembler = ContextAssembler(maxContextTokens = 1000)

        memoryManager = MemoryManager(
            chunker = chunker,
            repository = repository,
            importanceCalculator = importanceCalculator,
            contextAssembler = contextAssembler,
            projectId = "test-project",
            minImportanceThreshold = 0.3f
        )

        mockEmbeddingEngine = MockEmbeddingEngine()
        mockVectorSearch = MockVectorSearchEngine()

        memoryManager.embeddingEngine = mockEmbeddingEngine
        memoryManager.vectorSearch = mockVectorSearch

        // Create test project
        val now = System.currentTimeMillis()
        database.projectQueries.insertProject(
            id = "test-project",
            name = "Test Project",
            description = "Test project for memory manager",
            created_at = now,
            updated_at = now,
            is_archived = 0,
            color = null,
            icon = null,
            message_count = 0,
            total_tokens = 0
        )
    }

    @AfterTest
    fun teardown() {
        // Note: Test database doesn't need explicit close for in-memory DB
    }

    @Test
    fun `createMemoriesFromMessage chunks and stores high-importance content`() = runTest {
        val content = "a".repeat(800) + "."  // ~200 tokens, will be chunked

        val context = ConversationContext(
            triviaWasShared = false,
            isCurrentConversation = true
        )

        val result = memoryManager.createMemoriesFromMessage(
            messageId = "msg-1",
            content = content,
            role = "user",
            conversationContext = context
        )

        assertTrue(result.isSuccess)
        val count = result.getOrNull()!!
        assertTrue(count > 0, "Should create at least one memory")

        // Verify memories were stored
        val memories = repository.getMemoriesForMessage("msg-1")
        assertEquals(count, memories.size)

        // Verify vectors were added
        assertTrue(mockVectorSearch.vectors.isNotEmpty())
    }

    @Test
    fun `createMemoriesFromMessage filters low-importance chunks`() = runTest {
        // Short, low-importance content
        val content = "ok"  // Very short, low importance

        val context = ConversationContext(
            triviaWasShared = false,
            isCurrentConversation = true
        )

        val result = memoryManager.createMemoriesFromMessage(
            messageId = "msg-1",
            content = content,
            role = "user",
            conversationContext = context
        )

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull(), "Low importance content should be filtered")
    }

    @Test
    fun `retrieveRelevantMemories returns ranked context`() = runTest {
        // First create some memories
        val content1 = "a".repeat(800) + "."  // High importance content
        val context = ConversationContext(
            triviaWasShared = false,
            isCurrentConversation = true
        )

        memoryManager.createMemoriesFromMessage(
            messageId = "msg-1",
            content = content1,
            role = "user",
            conversationContext = context
        )

        // Now retrieve
        val retrieveResult = memoryManager.retrieveRelevantMemories(
            queryText = "test query"
        )

        assertTrue(retrieveResult.isSuccess)
        val contextResult = retrieveResult.getOrNull()!!

        assertFalse(contextResult.isEmpty())
        assertTrue(contextResult.totalTokens > 0)
        assertTrue(contextResult.selectedMemories.isNotEmpty())
    }

    @Test
    fun `retrieveRelevantMemories updates access tracking`() = runTest {
        // Create memory
        val content = "a".repeat(800) + "."
        val context = ConversationContext(
            triviaWasShared = false,
            isCurrentConversation = true
        )

        memoryManager.createMemoriesFromMessage(
            messageId = "msg-1",
            content = content,
            role = "user",
            conversationContext = context
        )

        val memoriesBefore = repository.getMemoriesForMessage("msg-1")
        val initialAccessCount = memoriesBefore.first().access_count

        // Retrieve
        memoryManager.retrieveRelevantMemories("test query")

        // Check access count increased
        val memoriesAfter = repository.getMemoriesForMessage("msg-1")
        val finalAccessCount = memoriesAfter.first().access_count

        assertTrue(finalAccessCount > initialAccessCount)
    }

    @Test
    fun `getRecentMemories returns temporal context`() = runTest {
        // Create multiple memories
        repeat(5) { i ->
            val content = "a".repeat(800) + "."
            val context = ConversationContext(
                triviaWasShared = false,
                isCurrentConversation = true
            )

            memoryManager.createMemoriesFromMessage(
                messageId = "msg-$i",
                content = content,
                role = "user",
                conversationContext = context
            )
        }

        val recentMemories = memoryManager.getRecentMemories(limit = 3)

        assertTrue(recentMemories.size <= 3)
    }

    @Test
    fun `deleteMemoriesForMessage removes from repository and vector index`() = runTest {
        // Create memory
        val content = "a".repeat(800) + "."
        val context = ConversationContext(
            triviaWasShared = false,
            isCurrentConversation = true
        )

        memoryManager.createMemoriesFromMessage(
            messageId = "msg-1",
            content = content,
            role = "user",
            conversationContext = context
        )

        val memoriesBefore = repository.getMemoriesForMessage("msg-1")
        assertTrue(memoriesBefore.isNotEmpty())

        val vectorCountBefore = mockVectorSearch.vectors.size

        // Delete
        val result = memoryManager.deleteMemoriesForMessage("msg-1")

        assertTrue(result.isSuccess)

        val memoriesAfter = repository.getMemoriesForMessage("msg-1")
        assertTrue(memoriesAfter.isEmpty())

        val vectorCountAfter = mockVectorSearch.vectors.size
        assertTrue(vectorCountAfter < vectorCountBefore)
    }

    @Test
    fun `cleanupLowImportanceMemories removes low quality content`() = runTest {
        // Create memory with manually set low importance
        repository.createMemory(
            id = "mem-low",
            messageId = "msg-1",
            projectId = "test-project",
            content = "Low importance",
            importance = 0.2f,
            createdAt = System.currentTimeMillis(),
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-low"
        )

        mockVectorSearch.addVector("emb-low", FloatArray(384) { 0.1f })

        val countBefore = repository.getMemoryCount("test-project")
        assertEquals(1, countBefore)

        // Cleanup
        val result = memoryManager.cleanupLowImportanceMemories(importanceThreshold = 0.3f)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        val countAfter = repository.getMemoryCount("test-project")
        assertEquals(0, countAfter)
    }

    @Test
    fun `getMemoryStats returns statistics`() = runTest {
        // Create some memories
        val content = "a".repeat(800) + "."
        val context = ConversationContext(
            triviaWasShared = false,
            isCurrentConversation = true
        )

        memoryManager.createMemoriesFromMessage(
            messageId = "msg-1",
            content = content,
            role = "user",
            conversationContext = context
        )

        val stats = memoryManager.getMemoryStats()

        assertNotNull(stats)
        assertTrue(stats.totalMemories > 0)
    }

    @Test
    fun `pinMemory prevents deletion during cleanup`() = runTest {
        // Create low-importance memory
        repository.createMemory(
            id = "mem-low",
            messageId = "msg-1",
            projectId = "test-project",
            content = "Low importance but pinned",
            importance = 0.2f,
            createdAt = System.currentTimeMillis(),
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-low"
        )

        mockVectorSearch.addVector("emb-low", FloatArray(384) { 0.1f })

        // Pin it
        memoryManager.pinMemory("mem-low")

        // Try cleanup
        val result = memoryManager.cleanupLowImportanceMemories(importanceThreshold = 0.3f)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull(), "Pinned memory should not be deleted")

        // Verify still exists
        assertNotNull(repository.getMemoryById("mem-low"))
    }

    // Mock implementations

    private class MockEmbeddingEngine : EmbeddingEngine {
        override val dimensions: Int = 384

        override suspend fun embed(texts: List<String>): Result<List<FloatArray>> {
            // Return dummy embeddings (all 0.5)
            val embeddings = texts.map { FloatArray(dimensions) { 0.5f } }
            return Result.success(embeddings)
        }
    }

    private class MockVectorSearchEngine : VectorSearchEngine {
        val vectors = mutableMapOf<String, FloatArray>()

        override suspend fun addVector(id: String, vector: FloatArray): Result<Unit> {
            vectors[id] = vector
            return Result.success(Unit)
        }

        override suspend fun search(queryVector: FloatArray, k: Int): Result<List<SearchResult>> {
            // Return all vectors as search results (sorted by ID for determinism)
            val results = vectors.keys.sorted().take(k).map { id ->
                SearchResult(id, 0.9f)  // Dummy high similarity
            }
            return Result.success(results)
        }

        override suspend fun removeVector(id: String): Result<Unit> {
            vectors.remove(id)
            return Result.success(Unit)
        }
    }
}
