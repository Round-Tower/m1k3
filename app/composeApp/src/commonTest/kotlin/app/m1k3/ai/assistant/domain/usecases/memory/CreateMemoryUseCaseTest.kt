package app.m1k3.ai.assistant.domain.usecases.memory

import app.m1k3.ai.assistant.domain.memory.ConversationContext
import app.m1k3.ai.assistant.domain.memory.ImportanceCalculator
import app.m1k3.ai.assistant.domain.memory.MemorySearchResult
import app.m1k3.ai.assistant.domain.memory.MemoryStats
import app.m1k3.ai.assistant.domain.memory.services.Chunk
import app.m1k3.ai.assistant.domain.memory.services.SemanticChunker
import app.m1k3.ai.assistant.domain.repositories.EmbeddingRepository
import app.m1k3.ai.assistant.domain.repositories.MemoryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * CreateMemoryUseCase Tests - TDD Red Phase
 *
 * Validates memory creation orchestration:
 * 1. Calculate importance
 * 2. Skip low-importance content (< 0.3)
 * 3. Chunk text semantically
 * 4. Generate embeddings
 * 5. Store in vector index
 *
 * **Success Criteria:**
 * - ✅ High-importance content creates memories
 * - ✅ Low-importance content is filtered out
 * - ✅ Multiple chunks are created for long text
 * - ✅ Single chunk for short text
 * - ✅ Empty/blank text handled gracefully
 * - ✅ Embedding failures handled gracefully
 * - ✅ Repository failures handled gracefully
 */
class CreateMemoryUseCaseTest {

    private lateinit var mockMemoryRepo: MockMemoryRepository
    private lateinit var mockEmbeddingRepo: MockEmbeddingRepository
    private lateinit var mockChunker: MockSemanticChunker
    private lateinit var mockImportanceCalc: MockImportanceCalculator
    private lateinit var useCase: CreateMemoryUseCase

    @BeforeTest
    fun setup() {
        mockMemoryRepo = MockMemoryRepository()
        mockEmbeddingRepo = MockEmbeddingRepository()
        mockChunker = MockSemanticChunker()
        mockImportanceCalc = MockImportanceCalculator()

        useCase = CreateMemoryUseCase(
            memoryRepository = mockMemoryRepo,
            embeddingRepository = mockEmbeddingRepo,
            semanticChunker = mockChunker,
            importanceCalculator = mockImportanceCalc
        )
    }

    // ========================================
    // Happy Path Tests
    // ========================================

    @Test
    fun `creates memory for high-importance content`() = runTest {
        // Given: High-importance content (0.8)
        val messageId = "msg-123"
        val content = "User discussed photosynthesis in detail. It's a very important biological process."
        mockImportanceCalc.importanceScore = 0.8f

        // Chunker returns 1 chunk
        val chunk = Chunk(
            id = "${messageId}_chunk_0",
            content = content,
            messageId = messageId,
            projectId = "proj-1",
            timestamp = System.currentTimeMillis(),
            role = "user",
            tokenCount = 150
        )
        mockChunker.chunks = listOf(chunk)

        // Embedding repo returns vector
        mockEmbeddingRepo.embedding = FloatArray(384) { 0.1f }

        // When: Create memory
        val result = useCase.execute(
            messageId = messageId,
            content = content,
            projectId = "proj-1",
            role = "user"
        )

        // Then: Success with 1 chunk created
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        assertEquals(1, mockMemoryRepo.createCallCount)
    }

    @Test
    fun `skips low-importance content`() = runTest {
        // Given: Low-importance content (0.2)
        mockImportanceCalc.importanceScore = 0.2f

        // When: Create memory
        val result = useCase.execute(
            messageId = "msg-123",
            content = "Just saying hi",
            projectId = "proj-1",
            role = "user"
        )

        // Then: Success but 0 chunks created (filtered out)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        assertEquals(0, mockMemoryRepo.createCallCount)
    }

    @Test
    fun `creates multiple chunks for long content`() = runTest {
        // Given: High-importance long content
        mockImportanceCalc.importanceScore = 0.75f

        val longContent = "A".repeat(1000) // Long text
        val chunk1 = Chunk(
            id = "msg-123_chunk_0",
            content = longContent.substring(0, 500),
            messageId = "msg-123",
            projectId = "proj-1",
            timestamp = System.currentTimeMillis(),
            role = "user",
            tokenCount = 250
        )
        val chunk2 = Chunk(
            id = "msg-123_chunk_1",
            content = longContent.substring(450, 950),
            messageId = "msg-123",
            projectId = "proj-1",
            timestamp = System.currentTimeMillis(),
            role = "user",
            tokenCount = 250
        )
        mockChunker.chunks = listOf(chunk1, chunk2)

        mockEmbeddingRepo.embedding = FloatArray(384) { 0.1f }

        // When: Create memory
        val result = useCase.execute(
            messageId = "msg-123",
            content = longContent,
            projectId = "proj-1",
            role = "user"
        )

        // Then: Success with 2 chunks created
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
        assertEquals(2, mockMemoryRepo.createCallCount)
    }

    // ========================================
    // Edge Case Tests
    // ========================================

    @Test
    fun `handles empty content gracefully`() = runTest {
        // Given: Empty content
        mockImportanceCalc.importanceScore = 0.8f
        mockChunker.chunks = emptyList()

        // When: Create memory
        val result = useCase.execute(
            messageId = "msg-123",
            content = "",
            projectId = "proj-1",
            role = "user"
        )

        // Then: Success with 0 chunks
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        assertEquals(0, mockMemoryRepo.createCallCount)
    }

    @Test
    fun `handles blank content gracefully`() = runTest {
        // Given: Blank content (whitespace only)
        mockImportanceCalc.importanceScore = 0.8f
        mockChunker.chunks = emptyList()

        // When: Create memory
        val result = useCase.execute(
            messageId = "msg-123",
            content = "   \n  \t  ",
            projectId = "proj-1",
            role = "user"
        )

        // Then: Success with 0 chunks
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        assertEquals(0, mockMemoryRepo.createCallCount)
    }

    // ========================================
    // Error Handling Tests
    // ========================================

    @Test
    fun `handles embedding generation failure`() = runTest {
        // Given: High-importance content but embedding fails
        mockImportanceCalc.importanceScore = 0.8f

        val chunk = Chunk(
            id = "msg-123_chunk_0",
            content = "Test content",
            messageId = "msg-123",
            projectId = "proj-1",
            timestamp = System.currentTimeMillis(),
            role = "user",
            tokenCount = 150
        )
        mockChunker.chunks = listOf(chunk)

        mockEmbeddingRepo.shouldFail = true

        // When: Create memory
        val result = useCase.execute(
            messageId = "msg-123",
            content = "Test content",
            projectId = "proj-1",
            role = "user"
        )

        // Then: Failure returned
        assertTrue(result.isFailure)
        assertEquals(0, mockMemoryRepo.createCallCount)
    }

    @Test
    fun `handles repository storage failure`() = runTest {
        // Given: High-importance content but repo fails
        mockImportanceCalc.importanceScore = 0.8f

        val chunk = Chunk(
            id = "msg-123_chunk_0",
            content = "Test content",
            messageId = "msg-123",
            projectId = "proj-1",
            timestamp = System.currentTimeMillis(),
            role = "user",
            tokenCount = 150
        )
        mockChunker.chunks = listOf(chunk)

        mockEmbeddingRepo.embedding = FloatArray(384) { 0.1f }
        mockMemoryRepo.shouldFail = true

        // When: Create memory
        val result = useCase.execute(
            messageId = "msg-123",
            content = "Test content",
            projectId = "proj-1",
            role = "user"
        )

        // Then: Failure returned
        assertTrue(result.isFailure)
    }

    // ========================================
    // Mock Implementations
    // ========================================

    private class MockMemoryRepository : MemoryRepository {
        var createCallCount = 0
        var shouldFail = false
        private val memories = mutableListOf<MemorySearchResult>()

        override suspend fun initialize(): Result<Unit> = Result.success(Unit)

        override suspend fun createMemoryFromMessage(
            messageId: String,
            content: String,
            importance: Float
        ): Result<Int> {
            if (shouldFail) {
                return Result.failure(Exception("Repository storage failed"))
            }
            createCallCount++
            return Result.success(1)
        }

        override suspend fun searchMemories(
            query: String,
            topK: Int,
            minSimilarity: Float
        ): Result<List<MemorySearchResult>> = Result.success(memories)

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
        ): Result<String> {
            if (shouldFail) {
                return Result.failure(Exception("Repository storage failed"))
            }
            createCallCount++
            return Result.success("mock-memory-$chunkIndex")
        }

        override suspend fun shutdown(): Result<Unit> = Result.success(Unit)
    }

    private class MockEmbeddingRepository : EmbeddingRepository {
        override val modelName: String = "MockEmbedding"
        override val embeddingDimensions: Int = 384

        var embedding: FloatArray = FloatArray(384) { 0.1f }
        var shouldFail: Boolean = false

        override suspend fun loadModel(): Result<Unit> = Result.success(Unit)

        override suspend fun embed(text: String): Result<FloatArray> {
            return if (shouldFail) {
                Result.failure(Exception("Embedding failed"))
            } else {
                Result.success(embedding)
            }
        }

        override suspend fun embedBatch(texts: List<String>): Result<List<FloatArray>> {
            return if (shouldFail) {
                Result.failure(Exception("Batch embedding failed"))
            } else {
                Result.success(texts.map { embedding })
            }
        }

        override fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float = 0.9f
    }

    private class MockSemanticChunker : SemanticChunker() {
        var chunks: List<Chunk> = emptyList()

        override fun chunkMessage(
            messageContent: String,
            messageId: String,
            projectId: String?,
            timestamp: Long,
            role: String
        ): List<Chunk> = chunks
    }

    private class MockImportanceCalculator : ImportanceCalculator() {
        var importanceScore: Float = 0.5f

        override fun calculateImportance(content: String, context: ConversationContext): Float = importanceScore
    }
}
