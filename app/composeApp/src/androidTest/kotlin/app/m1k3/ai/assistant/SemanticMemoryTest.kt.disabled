package app.m1k3.ai.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.m1k3.ai.assistant.database.AndroidDatabaseFactory
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.embedding.EmbeddingTaskType
import app.m1k3.ai.assistant.embedding.MiniLmEmbeddingEngine
import app.m1k3.ai.assistant.memory.SemanticMemoryManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Semantic Memory System Integration Test
 *
 * Tests the end-to-end semantic memory pipeline:
 * 1. Embedding generation (placeholder mode)
 * 2. Memory creation from messages
 * 3. Semantic search
 * 4. Memory retrieval
 *
 * Note: Currently uses placeholder embeddings for testing.
 * Real ONNX inference will improve search quality.
 */
@RunWith(AndroidJUnit4::class)
class SemanticMemoryTest {

    private lateinit var context: Context
    private lateinit var database: MaDatabase
    private lateinit var embeddingEngine: MiniLmEmbeddingEngine
    private lateinit var memoryManager: SemanticMemoryManager
    private val testProjectId = "test-project-${System.currentTimeMillis()}"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()

        // Create in-memory database for testing
        val databaseFactory = AndroidDatabaseFactory(context)
        val driver = databaseFactory.createDriver("")
        database = MaDatabase(driver)

        // Initialize embedding engine
        embeddingEngine = MiniLmEmbeddingEngine(context)
        embeddingEngine.loadModel().getOrThrow()

        // Initialize memory manager
        memoryManager = SemanticMemoryManager(
            context = context,
            database = database,
            embeddingEngine = embeddingEngine,
            projectId = testProjectId
        )
        memoryManager.initialize().getOrThrow()
    }

    @After
    fun teardown() = runBlocking {
        memoryManager.shutdown()
    }

    @Test
    fun testEmbeddingGeneration() = runBlocking {
        // Test single embedding
        val text = "The quick brown fox jumps over the lazy dog"
        val result = embeddingEngine.embed(text, EmbeddingTaskType.RETRIEVAL)

        assertTrue("Embedding should succeed", result.isSuccess)

        val embedding = result.getOrThrow()
        assertEquals("Embedding should have 384 dimensions", 384, embedding.size)

        // Check normalization (should be close to 1.0)
        val norm = kotlin.math.sqrt(embedding.sumOf { (it * it).toDouble() }.toFloat())
        assertTrue("Embedding should be normalized", norm > 0.99f && norm < 1.01f)
    }

    @Test
    fun testBatchEmbedding() = runBlocking {
        val texts = listOf(
            "First test sentence",
            "Second test sentence",
            "Third test sentence"
        )

        val result = embeddingEngine.embedBatch(texts, EmbeddingTaskType.RETRIEVAL)

        assertTrue("Batch embedding should succeed", result.isSuccess)

        val embeddings = result.getOrThrow()
        assertEquals("Should have 3 embeddings", 3, embeddings.size)

        embeddings.forEach { embedding ->
            assertEquals("Each embedding should have 384 dimensions", 384, embedding.size)
        }
    }

    @Test
    fun testMemoryCreation() = runBlocking {
        val messageId = "test-message-1"
        val content = "WiFi connectivity issues can be caused by interference from nearby networks"
        val importance = 0.8f

        val result = memoryManager.createMemoryFromMessage(
            messageId = messageId,
            content = content,
            importance = importance
        )

        assertTrue("Memory creation should succeed", result.isSuccess)

        val chunkCount = result.getOrThrow()
        assertTrue("Should create at least 1 memory chunk", chunkCount >= 1)

        // Verify memory was stored
        val stats = memoryManager.getMemoryStats().getOrThrow()
        assertTrue("Total memories should be > 0", stats.totalMemories > 0)
    }

    @Test
    fun testSemanticSearch() = runBlocking {
        // Create some test memories
        val memories = listOf(
            "WiFi connection keeps dropping every few minutes" to 0.9f,
            "My iPhone battery drains quickly when using GPS" to 0.8f,
            "The weather is nice today" to 0.3f,
            "Network router needs firmware update" to 0.85f
        )

        memories.forEachIndexed { index, (content, importance) ->
            memoryManager.createMemoryFromMessage(
                messageId = "msg-$index",
                content = content,
                importance = importance
            ).getOrThrow()
        }

        // Search for network-related memories
        val query = "wireless internet problems"
        val searchResult = memoryManager.searchMemories(
            query = query,
            topK = 3,
            minSimilarity = 0.0f  // Low threshold for placeholder embeddings
        )

        assertTrue("Search should succeed", searchResult.isSuccess)

        val results = searchResult.getOrThrow()
        assertTrue("Should return at least 1 result", results.isNotEmpty())

        // With placeholder embeddings, we can't guarantee semantic quality,
        // but we can verify the system works
        results.forEach { memory ->
            assertTrue("Similarity should be between 0 and 1",
                memory.similarity >= 0f && memory.similarity <= 1f)
            assertNotNull("Content should not be null", memory.content)
            assertTrue("Importance should be valid", memory.importance > 0f)
        }
    }

    @Test
    fun testHighImportanceRetrieval() = runBlocking {
        // Create memories with different importance levels
        listOf(
            "Critical system error" to 0.95f,
            "Important security update" to 0.85f,
            "Routine maintenance" to 0.5f,
            "Low priority task" to 0.3f
        ).forEachIndexed { index, (content, importance) ->
            memoryManager.createMemoryFromMessage(
                messageId = "msg-importance-$index",
                content = content,
                importance = importance
            ).getOrThrow()
        }

        // Get high-importance memories
        val result = memoryManager.getHighImportanceMemories(limit = 10)

        assertTrue("Should succeed", result.isSuccess)

        val memories = result.getOrThrow()
        assertTrue("Should return high-importance memories", memories.isNotEmpty())

        // Verify all returned memories have high importance
        memories.forEach { memory ->
            assertTrue("Importance should be >= 0.7", memory.importance >= 0.7f)
        }
    }

    @Test
    fun testRecentMemoriesRetrieval() = runBlocking {
        // Create several memories
        repeat(5) { index ->
            memoryManager.createMemoryFromMessage(
                messageId = "msg-recent-$index",
                content = "Test memory $index",
                importance = 0.7f
            ).getOrThrow()

            // Small delay to ensure different timestamps
            kotlinx.coroutines.delay(10)
        }

        // Get recent memories
        val result = memoryManager.getRecentMemories(limit = 3)

        assertTrue("Should succeed", result.isSuccess)

        val memories = result.getOrThrow()
        assertEquals("Should return 3 memories", 3, memories.size)

        // Verify memories are in reverse chronological order
        for (i in 0 until memories.size - 1) {
            assertTrue("Memories should be ordered by timestamp",
                memories[i].createdAt >= memories[i + 1].createdAt)
        }
    }

    @Test
    fun testMemoryDeletion() = runBlocking {
        val messageId = "msg-to-delete"

        // Create memory
        memoryManager.createMemoryFromMessage(
            messageId = messageId,
            content = "Test content for deletion",
            importance = 0.7f
        ).getOrThrow()

        // Verify it exists
        var stats = memoryManager.getMemoryStats().getOrThrow()
        val initialCount = stats.totalMemories

        // Delete it
        val deleteResult = memoryManager.deleteMemoriesForMessage(messageId)
        assertTrue("Deletion should succeed", deleteResult.isSuccess)

        // Verify it's gone
        stats = memoryManager.getMemoryStats().getOrThrow()
        assertTrue("Memory count should decrease", stats.totalMemories < initialCount)
    }

    @Test
    fun testMemoryStats() = runBlocking {
        // Create some test memories
        repeat(3) { index ->
            memoryManager.createMemoryFromMessage(
                messageId = "msg-stats-$index",
                content = "Test memory for stats $index",
                importance = 0.6f + (index * 0.1f)
            ).getOrThrow()
        }

        val result = memoryManager.getMemoryStats()

        assertTrue("Should succeed", result.isSuccess)

        val stats = result.getOrThrow()
        assertTrue("Total memories should be > 0", stats.totalMemories > 0)
        assertTrue("Average importance should be between 0 and 1",
            stats.averageImportance >= 0f && stats.averageImportance <= 1f)
        assertEquals("Embedding dimensions should be 384", 384, stats.embeddingDimensions)
    }

    @Test
    fun testEmbeddingDeterminism() = runBlocking {
        // Placeholder embeddings should be deterministic (same text = same embedding)
        val text = "Test text for determinism"

        val embedding1 = embeddingEngine.embed(text).getOrThrow()
        val embedding2 = embeddingEngine.embed(text).getOrThrow()

        assertArrayEquals(
            "Same text should produce same embedding",
            embedding1,
            embedding2,
            0.0001f
        )
    }
}
