package app.m1k3.ai.domain.repositories

import app.m1k3.ai.domain.memory.MemorySearchResult
import app.m1k3.ai.domain.memory.MemoryStats

/**
 * MemoryRepository - Domain contract for semantic memory storage
 *
 * Pure Kotlin interface with no platform dependencies.
 * Defines operations for vector-based memory storage and retrieval.
 *
 * **Philosophy:**
 * Repository pattern decouples business logic from data layer.
 * Domain defines "what", platform implements "how".
 *
 * **Responsibilities:**
 * - Initialize vector index for semantic search
 * - Create memory chunks from message content
 * - Search memories by semantic similarity
 * - Retrieve high-importance memories
 * - Provide health/diagnostic metrics
 * - Clean shutdown of resources
 *
 * **Platform Implementations:**
 * - Android: ONNX Runtime + custom HNSW index
 * - iOS: Core ML + custom HNSW index (future)
 *
 * **Usage:**
 * ```kotlin
 * val memoryRepo: MemoryRepository = get() // Koin injection
 *
 * // Initialize on app start
 * memoryRepo.initialize().onSuccess {
 *     println("Memory system ready")
 * }
 *
 * // Create memory from conversation
 * memoryRepo.createMemoryFromMessage(
 *     messageId = "msg-123",
 *     content = "User discussed photosynthesis",
 *     importance = 0.75f
 * ).onSuccess { chunkCount ->
 *     println("Created $chunkCount memory chunks")
 * }
 *
 * // Search for relevant memories
 * memoryRepo.searchMemories(
 *     query = "Tell me about plants",
 *     topK = 5,
 *     minSimilarity = 0.5f
 * ).onSuccess { results ->
 *     results.forEach { println(it.content) }
 * }
 * ```
 */
interface MemoryRepository {
    /**
     * Initialize memory system
     *
     * Platform implementations should:
     * 1. Load embedding model (ONNX/Core ML)
     * 2. Initialize vector index (HNSW)
     * 3. Verify database schema
     *
     * @return Result.success if initialized, Result.failure with exception
     */
    suspend fun initialize(): Result<Unit>

    /**
     * Create memory chunks from message content
     *
     * Orchestrates:
     * 1. Semantic chunking (via SemanticChunker)
     * 2. Embedding generation
     * 3. Vector index insertion
     * 4. Database persistence
     *
     * Only creates memory if importance >= 0.3 (threshold for relevance)
     *
     * @param messageId Associated message ID for linking
     * @param content Message text to chunk and embed
     * @param importance Calculated importance score (0.0-1.0)
     * @return Result.success(chunkCount) or Result.failure
     */
    suspend fun createMemoryFromMessage(
        messageId: String,
        content: String,
        importance: Float
    ): Result<Int>

    /**
     * Search memories by semantic similarity
     *
     * Uses vector similarity (cosine distance) to find relevant memories.
     * Returns memories ranked by similarity score.
     *
     * @param query User's question or context
     * @param topK Maximum number of results (default: 10)
     * @param minSimilarity Minimum similarity threshold (0.0-1.0, default: 0.5)
     * @return Result.success(List<MemorySearchResult>) or Result.failure
     */
    suspend fun searchMemories(
        query: String,
        topK: Int = 10,
        minSimilarity: Float = 0.5f
    ): Result<List<MemorySearchResult>>

    /**
     * Get high-importance memories
     *
     * Retrieves memories sorted by importance score (no similarity search).
     * Useful for "what do you remember about me?" queries.
     *
     * @param limit Maximum results (default: 20)
     * @return Result.success(List<MemorySearchResult>) or Result.failure
     */
    suspend fun getHighImportanceMemories(limit: Int = 20): Result<List<MemorySearchResult>>

    /**
     * Get memory system statistics
     *
     * Provides health metrics:
     * - Total memories stored
     * - Average importance
     * - Vector index status
     *
     * @return Result.success(MemoryStats) or Result.failure
     */
    suspend fun getMemoryStats(): Result<MemoryStats>

    /**
     * Store a single memory chunk with its embedding
     *
     * Low-level storage method used by CreateMemoryUseCase after
     * chunking and embedding are complete. Stores both:
     * - Metadata in database (content, importance, etc.)
     * - Embedding vector in search index
     *
     * @param messageId Associated message ID
     * @param content Chunk text content
     * @param importance Importance score (0.0-1.0)
     * @param chunkIndex Index of this chunk (0-based)
     * @param chunkTotal Total number of chunks
     * @param chunkTokens Approximate token count
     * @param embedding Pre-computed embedding vector
     * @param projectId Optional project ID
     * @return Result.success(memoryId) or Result.failure
     */
    suspend fun storeChunkWithEmbedding(
        messageId: String,
        content: String,
        importance: Float,
        chunkIndex: Int,
        chunkTotal: Int,
        chunkTokens: Int,
        embedding: FloatArray,
        projectId: String? = null
    ): Result<String>

    /**
     * Shutdown memory system
     *
     * Platform implementations should:
     * 1. Close embedding model sessions
     * 2. Flush vector index to disk
     * 3. Clean up native resources
     *
     * @return Result.success if shutdown clean, Result.failure with exception
     */
    suspend fun shutdown(): Result<Unit>
}
