package app.m1k3.ai.domain.usecases.memory

import app.m1k3.ai.domain.memory.MemorySearchResult
import app.m1k3.ai.domain.memory.MemoryStats
import app.m1k3.ai.domain.repositories.MemoryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * SearchMemoriesUseCase Tests - TDD Red Phase
 *
 * Validates memory search orchestration:
 * 1. Query validation (non-blank)
 * 2. Semantic search via repository
 * 3. Result ranking by similarity
 * 4. TopK limiting
 * 5. Similarity threshold filtering
 *
 * **Success Criteria:**
 * - ✅ Returns relevant memories for valid queries
 * - ✅ Respects topK limit
 * - ✅ Respects minSimilarity threshold
 * - ✅ Empty results for no matches
 * - ✅ Rejects blank/empty queries
 * - ✅ Handles repository failures gracefully
 */
class SearchMemoriesUseCaseTest {

    private lateinit var mockMemoryRepo: MockMemoryRepository
    private lateinit var useCase: SearchMemoriesUseCase

    @BeforeTest
    fun setup() {
        mockMemoryRepo = MockMemoryRepository()
        useCase = SearchMemoriesUseCase(memoryRepository = mockMemoryRepo)
    }

    // ========================================
    // Happy Path Tests
    // ========================================

    @Test
    fun `returns memories for valid query`() = runTest {
        // Given: Repository has 3 memories
        val memory1 = createMemory("memory-1", "Photosynthesis is a process", similarity = 0.9f)
        val memory2 = createMemory("memory-2", "Plants use light energy", similarity = 0.8f)
        val memory3 = createMemory("memory-3", "Biology topic discussed", similarity = 0.6f)
        mockMemoryRepo.memories = listOf(memory1, memory2, memory3)

        // When: Search for "photosynthesis"
        val result = useCase.execute(
            query = "photosynthesis",
            topK = 10,
            minSimilarity = 0.5f
        )

        // Then: Returns all 3 memories
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.size)
    }

    @Test
    fun `respects topK limit`() = runTest {
        // Given: Repository has 5 memories
        val memories = (1..5).map { i ->
            createMemory("memory-$i", "Content $i", similarity = 0.9f - (i * 0.1f))
        }
        mockMemoryRepo.memories = memories

        // When: Search with topK = 3
        val result = useCase.execute(
            query = "test query",
            topK = 3,
            minSimilarity = 0.5f
        )

        // Then: Returns only top 3
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.size)
    }

    @Test
    fun `respects minSimilarity threshold`() = runTest {
        // Given: Memories with varying similarity
        val memory1 = createMemory("memory-1", "High similarity", similarity = 0.9f)
        val memory2 = createMemory("memory-2", "Medium similarity", similarity = 0.6f)
        val memory3 = createMemory("memory-3", "Low similarity", similarity = 0.3f)
        mockMemoryRepo.memories = listOf(memory1, memory2, memory3)

        // When: Search with minSimilarity = 0.5
        val result = useCase.execute(
            query = "test query",
            topK = 10,
            minSimilarity = 0.5f
        )

        // Then: Only memories >= 0.5 returned (2 memories)
        assertTrue(result.isSuccess)
        val memories = result.getOrNull()!!
        assertEquals(2, memories.size)
        assertTrue(memories.all { it.similarity >= 0.5f })
    }

    @Test
    fun `returns empty list when no matches`() = runTest {
        // Given: Repository has no memories
        mockMemoryRepo.memories = emptyList()

        // When: Search
        val result = useCase.execute(
            query = "test query",
            topK = 10,
            minSimilarity = 0.5f
        )

        // Then: Returns empty list
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    // ========================================
    // Validation Tests
    // ========================================

    @Test
    fun `rejects blank query`() = runTest {
        // When: Search with blank query
        val result = useCase.execute(
            query = "   ",
            topK = 10,
            minSimilarity = 0.5f
        )

        // Then: Failure returned
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("blank") == true)
    }

    @Test
    fun `rejects empty query`() = runTest {
        // When: Search with empty query
        val result = useCase.execute(
            query = "",
            topK = 10,
            minSimilarity = 0.5f
        )

        // Then: Failure returned
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("blank") == true)
    }

    @Test
    fun `rejects invalid topK`() = runTest {
        // When: Search with topK = 0
        val result = useCase.execute(
            query = "test",
            topK = 0,
            minSimilarity = 0.5f
        )

        // Then: Failure returned
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("topK") == true)
    }

    @Test
    fun `rejects invalid minSimilarity below range`() = runTest {
        // When: Search with minSimilarity = -0.1
        val result = useCase.execute(
            query = "test",
            topK = 10,
            minSimilarity = -0.1f
        )

        // Then: Failure returned
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("minSimilarity") == true)
    }

    @Test
    fun `rejects invalid minSimilarity above range`() = runTest {
        // When: Search with minSimilarity = 1.5
        val result = useCase.execute(
            query = "test",
            topK = 10,
            minSimilarity = 1.5f
        )

        // Then: Failure returned
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("minSimilarity") == true)
    }

    // ========================================
    // Error Handling Tests
    // ========================================

    @Test
    fun `handles repository failure gracefully`() = runTest {
        // Given: Repository will fail
        mockMemoryRepo.shouldFail = true

        // When: Search
        val result = useCase.execute(
            query = "test query",
            topK = 10,
            minSimilarity = 0.5f
        )

        // Then: Failure returned
        assertTrue(result.isFailure)
    }

    // ========================================
    // Default Parameter Tests
    // ========================================

    @Test
    fun `uses default topK when not specified`() = runTest {
        // Given: Many memories
        val memories = (1..20).map { i ->
            createMemory("memory-$i", "Content $i", similarity = 0.9f)
        }
        mockMemoryRepo.memories = memories

        // When: Search without topK (should default to 10)
        val result = useCase.execute(query = "test")

        // Then: Returns 10 memories (default)
        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrNull()?.size)
    }

    @Test
    fun `uses default minSimilarity when not specified`() = runTest {
        // Given: Memories with varying similarity
        val memory1 = createMemory("memory-1", "High", similarity = 0.9f)
        val memory2 = createMemory("memory-2", "Medium", similarity = 0.6f)
        val memory3 = createMemory("memory-3", "Low", similarity = 0.4f)
        mockMemoryRepo.memories = listOf(memory1, memory2, memory3)

        // When: Search without minSimilarity (should default to 0.5)
        val result = useCase.execute(query = "test")

        // Then: Returns only memories >= 0.5 (2 memories)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createMemory(
        id: String,
        content: String,
        similarity: Float,
        importance: Float = 0.7f
    ): MemorySearchResult {
        return MemorySearchResult(
            id = id,
            content = content,
            importance = importance,
            similarity = similarity,
            chunkIndex = 0,
            chunkTotal = 1,
            messageId = "msg-123",
            createdAt = System.currentTimeMillis()
        )
    }

    // ========================================
    // Mock Implementation
    // ========================================

    private class MockMemoryRepository : MemoryRepository {
        var memories: List<MemorySearchResult> = emptyList()
        var shouldFail: Boolean = false

        override suspend fun initialize(): Result<Unit> = Result.success(Unit)

        override suspend fun createMemoryFromMessage(
            messageId: String,
            content: String,
            importance: Float
        ): Result<Int> = Result.success(0)

        override suspend fun searchMemories(
            query: String,
            topK: Int,
            minSimilarity: Float
        ): Result<List<MemorySearchResult>> {
            return if (shouldFail) {
                Result.failure(Exception("Repository search failed"))
            } else {
                // Filter by similarity and limit by topK
                val filtered = memories.filter { it.similarity >= minSimilarity }
                val limited = filtered.take(topK)
                Result.success(limited)
            }
        }

        override suspend fun getHighImportanceMemories(limit: Int): Result<List<MemorySearchResult>> =
            Result.success(memories)

        override suspend fun getMemoryStats(): Result<MemoryStats> =
            Result.success(
                MemoryStats(
                    totalMemories = memories.size.toLong(),
                    averageImportance = 0.5f,
                    hasVectorIndex = true
                )
            )

        override suspend fun storeChunkWithEmbedding(
            messageId: String,
            content: String,
            importance: Float,
            chunkIndex: Int,
            chunkTotal: Int,
            chunkTokens: Int,
            embedding: FloatArray,
            projectId: String?
        ): Result<String> = Result.success("mock-memory-$chunkIndex")

        override suspend fun shutdown(): Result<Unit> = Result.success(Unit)
    }
}
