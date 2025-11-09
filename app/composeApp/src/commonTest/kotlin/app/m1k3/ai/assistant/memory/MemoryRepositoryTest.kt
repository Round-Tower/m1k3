package app.m1k3.ai.assistant.memory

import app.m1k3.ai.assistant.test.TestDatabaseFactory
import kotlin.test.*

/**
 * Tests for MemoryRepository
 *
 * Validates CRUD operations, filtering, and statistics.
 */
class MemoryRepositoryTest {

    private lateinit var database: app.m1k3.ai.assistant.database.MaDatabase
    private lateinit var repository: MemoryRepository

    @BeforeTest
    fun setup() {
        database = TestDatabaseFactory.createInMemoryDatabase()
        repository = MemoryRepository(database)

        // Create test project
        val now = System.currentTimeMillis()
        database.projectQueries.insertProject(
            id = "test-project",
            name = "Test Project",
            description = "Test project for memory repository",
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
    fun `create and retrieve memory by ID`() {
        val memoryId = "mem-1"
        val now = System.currentTimeMillis()

        repository.createMemory(
            id = memoryId,
            messageId = "msg-1",
            projectId = "test-project",
            content = "Test memory content",
            importance = 0.8f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-1"
        )

        val memory = repository.getMemoryById(memoryId)

        assertNotNull(memory)
        assertEquals(memoryId, memory.id)
        assertEquals("msg-1", memory.message_id)
        assertEquals("test-project", memory.project_id)
        assertEquals("Test memory content", memory.content)
        assertEquals(0.8, memory.importance, 0.01)
        assertEquals(now, memory.created_at)
        assertEquals(0L, memory.chunk_index)
        assertEquals(1L, memory.chunk_total)
        assertEquals(50L, memory.chunk_tokens)
        assertEquals("emb-1", memory.embedding_id)
        assertEquals("all-MiniLM-L6-v2", memory.embedding_model)
        assertEquals(0L, memory.access_count)
        assertNull(memory.last_accessed_at)
        assertEquals(1.0, memory.decay_factor, 0.01)
        assertEquals(0L, memory.is_pinned)
    }

    @Test
    fun `get memory by embedding ID`() {
        repository.createMemory(
            id = "mem-1",
            messageId = "msg-1",
            projectId = "test-project",
            content = "Test content",
            importance = 0.5f,
            createdAt = System.currentTimeMillis(),
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-unique-123"
        )

        val memory = repository.getMemoryByEmbeddingId("emb-unique-123")

        assertNotNull(memory)
        assertEquals("mem-1", memory.id)
        assertEquals("emb-unique-123", memory.embedding_id)
    }

    @Test
    fun `get all memories for project sorted by importance`() {
        val now = System.currentTimeMillis()

        repository.createMemory(
            id = "mem-1",
            messageId = "msg-1",
            projectId = "test-project",
            content = "Low importance",
            importance = 0.3f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-1"
        )

        repository.createMemory(
            id = "mem-2",
            messageId = "msg-2",
            projectId = "test-project",
            content = "High importance",
            importance = 0.9f,
            createdAt = now + 1000,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 60,
            embeddingId = "emb-2"
        )

        repository.createMemory(
            id = "mem-3",
            messageId = "msg-3",
            projectId = "test-project",
            content = "Medium importance",
            importance = 0.6f,
            createdAt = now + 2000,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 55,
            embeddingId = "emb-3"
        )

        val memories = repository.getMemoriesForProject("test-project")

        assertEquals(3, memories.size)
        // Should be sorted by importance DESC
        assertEquals("mem-2", memories[0].id) // 0.9
        assertEquals("mem-3", memories[1].id) // 0.6
        assertEquals("mem-1", memories[2].id) // 0.3
    }

    @Test
    fun `get high importance memories above threshold`() {
        val now = System.currentTimeMillis()

        repository.createMemory(
            id = "mem-1",
            messageId = "msg-1",
            projectId = "test-project",
            content = "Low",
            importance = 0.3f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-1"
        )

        repository.createMemory(
            id = "mem-2",
            messageId = "msg-2",
            projectId = "test-project",
            content = "High",
            importance = 0.9f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-2"
        )

        repository.createMemory(
            id = "mem-3",
            messageId = "msg-3",
            projectId = "test-project",
            content = "High too",
            importance = 0.8f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-3"
        )

        val highMemories = repository.getHighImportanceMemories("test-project", 0.7f)

        assertEquals(2, highMemories.size)
        assertTrue(highMemories.all { it.importance >= 0.7 })
    }

    @Test
    fun `get recent memories with limit`() {
        val now = System.currentTimeMillis()

        repeat(15) { i ->
            repository.createMemory(
                id = "mem-$i",
                messageId = "msg-$i",
                projectId = "test-project",
                content = "Content $i",
                importance = 0.5f,
                createdAt = now + (i * 1000L),
                chunkIndex = 0,
                chunkTotal = 1,
                chunkTokens = 50,
                embeddingId = "emb-$i"
            )
        }

        val recentMemories = repository.getRecentMemories("test-project", limit = 5)

        assertEquals(5, recentMemories.size)
        // Should be most recent first
        assertEquals("mem-14", recentMemories[0].id)
        assertEquals("mem-13", recentMemories[1].id)
        assertEquals("mem-12", recentMemories[2].id)
    }

    @Test
    fun `get memories for specific message`() {
        val now = System.currentTimeMillis()

        // Create 3 chunks for message "msg-1"
        repository.createMemory(
            id = "mem-1",
            messageId = "msg-1",
            projectId = "test-project",
            content = "Chunk 0",
            importance = 0.5f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 3,
            chunkTokens = 100,
            embeddingId = "emb-1"
        )

        repository.createMemory(
            id = "mem-2",
            messageId = "msg-1",
            projectId = "test-project",
            content = "Chunk 1",
            importance = 0.5f,
            createdAt = now,
            chunkIndex = 1,
            chunkTotal = 3,
            chunkTokens = 150,
            embeddingId = "emb-2"
        )

        repository.createMemory(
            id = "mem-3",
            messageId = "msg-1",
            projectId = "test-project",
            content = "Chunk 2",
            importance = 0.5f,
            createdAt = now,
            chunkIndex = 2,
            chunkTotal = 3,
            chunkTokens = 120,
            embeddingId = "emb-3"
        )

        // Create memory for different message
        repository.createMemory(
            id = "mem-4",
            messageId = "msg-2",
            projectId = "test-project",
            content = "Different message",
            importance = 0.5f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 80,
            embeddingId = "emb-4"
        )

        val memories = repository.getMemoriesForMessage("msg-1")

        assertEquals(3, memories.size)
        assertEquals("mem-1", memories[0].id)  // chunk_index 0
        assertEquals("mem-2", memories[1].id)  // chunk_index 1
        assertEquals("mem-3", memories[2].id)  // chunk_index 2
    }

    @Test
    fun `update memory access tracking`() {
        val now = System.currentTimeMillis()

        repository.createMemory(
            id = "mem-1",
            messageId = "msg-1",
            projectId = "test-project",
            content = "Test",
            importance = 0.5f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-1"
        )

        val accessTime = now + 10000

        repository.updateMemoryAccess("mem-1", accessTime)

        val memory = repository.getMemoryById("mem-1")!!

        assertEquals(1L, memory.access_count)
        assertEquals(accessTime, memory.last_accessed_at)
    }

    @Test
    fun `pin and unpin memory`() {
        repository.createMemory(
            id = "mem-1",
            messageId = "msg-1",
            projectId = "test-project",
            content = "Test",
            importance = 0.5f,
            createdAt = System.currentTimeMillis(),
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-1"
        )

        // Initially not pinned
        assertEquals(0L, repository.getMemoryById("mem-1")!!.is_pinned)

        // Pin it
        repository.pinMemory("mem-1")
        assertEquals(1L, repository.getMemoryById("mem-1")!!.is_pinned)

        // Unpin it
        repository.unpinMemory("mem-1")
        assertEquals(0L, repository.getMemoryById("mem-1")!!.is_pinned)
    }

    @Test
    fun `get pinned memories`() {
        val now = System.currentTimeMillis()

        repository.createMemory(
            id = "mem-1",
            messageId = "msg-1",
            projectId = "test-project",
            content = "Normal",
            importance = 0.5f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-1"
        )

        repository.createMemory(
            id = "mem-2",
            messageId = "msg-2",
            projectId = "test-project",
            content = "Pinned",
            importance = 0.5f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-2"
        )

        repository.pinMemory("mem-2")

        val pinnedMemories = repository.getPinnedMemories("test-project")

        assertEquals(1, pinnedMemories.size)
        assertEquals("mem-2", pinnedMemories[0].id)
    }

    @Test
    fun `delete memory by ID`() {
        repository.createMemory(
            id = "mem-1",
            messageId = "msg-1",
            projectId = "test-project",
            content = "Test",
            importance = 0.5f,
            createdAt = System.currentTimeMillis(),
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-1"
        )

        assertNotNull(repository.getMemoryById("mem-1"))

        repository.deleteMemory("mem-1")

        assertNull(repository.getMemoryById("mem-1"))
    }

    @Test
    fun `delete all memories for project`() {
        val now = System.currentTimeMillis()

        repeat(5) { i ->
            repository.createMemory(
                id = "mem-$i",
                messageId = "msg-$i",
                projectId = "test-project",
                content = "Content $i",
                importance = 0.5f,
                createdAt = now,
                chunkIndex = 0,
                chunkTotal = 1,
                chunkTokens = 50,
                embeddingId = "emb-$i"
            )
        }

        assertEquals(5, repository.getMemoriesForProject("test-project").size)

        repository.deleteMemoriesForProject("test-project")

        assertEquals(0, repository.getMemoriesForProject("test-project").size)
    }

    @Test
    fun `delete low importance memories`() {
        val now = System.currentTimeMillis()

        repository.createMemory(
            id = "mem-1",
            messageId = "msg-1",
            projectId = "test-project",
            content = "Low",
            importance = 0.2f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-1"
        )

        repository.createMemory(
            id = "mem-2",
            messageId = "msg-2",
            projectId = "test-project",
            content = "High",
            importance = 0.9f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-2"
        )

        assertEquals(2, repository.getMemoriesForProject("test-project").size)

        repository.deleteLowImportanceMemories("test-project", importanceThreshold = 0.3f)

        val remaining = repository.getMemoriesForProject("test-project")
        assertEquals(1, remaining.size)
        assertEquals("mem-2", remaining[0].id)  // High importance survives
    }

    @Test
    fun `get memory count`() {
        assertEquals(0, repository.getMemoryCount("test-project"))

        repeat(10) { i ->
            repository.createMemory(
                id = "mem-$i",
                messageId = "msg-$i",
                projectId = "test-project",
                content = "Content $i",
                importance = 0.5f,
                createdAt = System.currentTimeMillis(),
                chunkIndex = 0,
                chunkTotal = 1,
                chunkTokens = 50,
                embeddingId = "emb-$i"
            )
        }

        assertEquals(10, repository.getMemoryCount("test-project"))
    }

    @Test
    fun `get memory statistics`() {
        val now = System.currentTimeMillis()

        repository.createMemory(
            id = "mem-1",
            messageId = "msg-1",
            projectId = "test-project",
            content = "Test 1",
            importance = 0.8f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-1"
        )

        repository.createMemory(
            id = "mem-2",
            messageId = "msg-2",
            projectId = "test-project",
            content = "Test 2",
            importance = 0.6f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-2"
        )

        repository.pinMemory("mem-2")
        repository.updateMemoryAccess("mem-1", now + 1000)
        repository.updateMemoryAccess("mem-1", now + 2000)

        val stats = repository.getMemoryStats("test-project")

        assertNotNull(stats)
        assertEquals(2, stats.totalMemories)
        assertEquals(0.7f, stats.avgImportance, 0.01f)  // (0.8 + 0.6) / 2
        assertEquals(1.0f, stats.avgDecay, 0.01f)
        assertEquals(2, stats.totalAccesses)  // mem-1 accessed twice
        assertEquals(1, stats.pinnedCount)  // mem-2 is pinned
    }

    @Test
    fun `search memories by content`() {
        val now = System.currentTimeMillis()

        repository.createMemory(
            id = "mem-1",
            messageId = "msg-1",
            projectId = "test-project",
            content = "The quick brown fox",
            importance = 0.5f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-1"
        )

        repository.createMemory(
            id = "mem-2",
            messageId = "msg-2",
            projectId = "test-project",
            content = "Jumped over the lazy dog",
            importance = 0.5f,
            createdAt = now,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb-2"
        )

        val results = repository.searchMemoriesByContent("test-project", "fox")

        assertEquals(1, results.size)
        assertEquals("mem-1", results[0].id)
    }
}
