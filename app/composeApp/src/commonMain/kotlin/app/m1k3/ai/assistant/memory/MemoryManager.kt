package app.m1k3.ai.assistant.memory

import app.m1k3.ai.assistant.database.MemoryMetadata
import app.m1k3.ai.domain.memory.ImportanceCalculator
import app.m1k3.ai.domain.memory.ConversationContext
import app.m1k3.ai.domain.memory.services.Chunk
import app.m1k3.ai.domain.memory.services.SemanticChunker
import app.m1k3.ai.domain.repositories.EmbeddingRepository
import kotlinx.datetime.Clock

/**
 * Documentation signed: Kev + claude-sonnet-4-5-20250929, 2026-01-15
 * Format: MurphySig v0.1 (https://murphysig.dev/spec)
 * Prior: Partial documentation (class-level KDoc existed, interfaces lacked detail)
 *
 * Context: Enhanced interface documentation for platform-agnostic memory layer.
 * Key improvements:
 * - EmbeddingEngine and VectorSearchEngine interfaces: comprehensive parameter docs
 * - Clarified nullable dependency semantics (Result.failure vs throws)
 * - Added threading/dispatcher guidance for implementors
 * - Documented text truncation, empty index handling, error modes
 * - Added iOS platform migration notes for Core ML and Accelerate frameworks
 *
 * Confidence: 0.88 - Interface contracts are clear and technically accurate.
 * KMP mobile AI reviewer verified threading semantics and platform notes.
 * Reduced from 0.92 due to:
 * - ChunkWithImportance private data class lacks KDoc (low priority)
 * - Result vs exception patterns could be more explicit in method-level docs
 * - No @since tags for API versioning
 *
 * Open: Should we extract SearchResult data class to shared types package for reuse?
 */

/**
 * 間 AI - Memory Manager
 *
 * High-level orchestration layer for semantic memory system.
 * Coordinates chunking, embedding, importance calculation, and storage.
 *
 * **Architecture:**
 * ```
 * Message → SemanticChunker → [Chunks]
 *                                  ↓
 *                          ImportanceCalculator
 *                                  ↓
 *                          EmbeddingEngine → [Vectors]
 *                                  ↓
 *                     MemoryRepository + VectorSearch
 * ```
 *
 * **Retrieval Flow:**
 * ```
 * Query → EmbeddingEngine → QueryVector
 *                              ↓
 *                        VectorSearch → [Similar IDs + Scores]
 *                              ↓
 *                        MemoryRepository → [MemoryMetadata]
 *                              ↓
 *                        MemoryRanker → Ranked Context
 * ```
 *
 * **Philosophy:**
 * - Single entry point for memory operations
 * - Hides complexity from ChatViewModel
 * - Automatic importance filtering (min threshold: 0.3)
 * - Type-safe operations with Result pattern
 *
 * **Responsibilities:**
 * - Create: Message → Chunks → Embeddings → Storage
 * - Retrieve: Query → Similar memories → Ranked context
 * - Manage: Access tracking, cleanup, statistics
 */
class MemoryManager(
    private val chunker: SemanticChunker,
    private val repository: MemoryDataSource,
    private val importanceCalculator: ImportanceCalculator,
    private val memoryRanker: MemoryRanker,
    private val projectId: String,
    private val minImportanceThreshold: Float = 0.3f,
    /**
     * Embedding repository for text-to-vector conversion (domain interface)
     *
     * **Required for:**
     * - `createMemoriesFromMessage()` - Returns Result.failure(IllegalStateException) if null
     * - `retrieveRelevantMemories()` - Returns Result.failure(IllegalStateException) if null
     *
     * **Optional only for:**
     * - Testing non-memory operations (stats, pinning, recent queries)
     * - Dependency injection in unit tests with mocked operations
     *
     * **Platform implementations:**
     * - Android: ONNX Runtime adapters (MiniLM, Gemma)
     * - iOS: Core ML adapters (future)
     */
    private val embeddingRepository: EmbeddingRepository? = null,
    /**
     * Vector search engine for semantic similarity (platform-specific)
     *
     * **Required for:**
     * - `retrieveRelevantMemories()` - Returns Result.failure(IllegalStateException) if null
     * - `deleteMemoriesForMessage()` - Returns Result.failure(IllegalStateException) if null
     * - `cleanupLowImportanceMemories()` - Returns Result.failure(IllegalStateException) if null
     *
     * **Optional only for:**
     * - Testing read-only operations (stats, recent memories)
     * - Unit tests with mocked repository queries
     *
     * **Platform implementations:**
     * - Android: VectorSearchManager (linear cosine similarity)
     * - iOS: Accelerate framework BNNS or Core ML (future)
     *   - Expected: HNSW via Accelerate vDSP or custom Swift implementation
     *   - Thread: Dispatch queues for background indexing
     *   - Memory: Consider memory pressure on older iOS devices
     */
    private val vectorSearch: VectorSearchEngine? = null
) {

    /**
     * Create memories from message content
     *
     * **Pipeline:**
     * 1. Chunk message (100-300 tokens, semantic boundaries)
     * 2. Calculate importance for each chunk
     * 3. Filter low-importance chunks (< 0.3)
     * 4. Generate embeddings
     * 5. Store metadata + vectors
     *
     * @param messageId Source message ID
     * @param content Message text
     * @param role Message role (user/assistant)
     * @param conversationContext Context for importance calculation
     * @return Result with count of memories created
     */
    suspend fun createMemoriesFromMessage(
        messageId: String,
        content: String,
        role: String,
        conversationContext: ConversationContext
    ): Result<Int> {
        return try {
            val embeddingRepo = embeddingRepository
                ?: return Result.failure(IllegalStateException("Embedding repository not initialized"))
            val vectorSearch = vectorSearch
                ?: return Result.failure(IllegalStateException("Vector search not initialized"))

            val timestamp = Clock.System.now().toEpochMilliseconds()

            // Step 1: Chunk message
            val chunks = chunker.chunkMessage(
                messageContent = content,
                messageId = messageId,
                projectId = projectId,
                timestamp = timestamp,
                role = role
            )

            if (chunks.isEmpty()) {
                return Result.success(0)
            }

            // Step 2: Calculate importance + filter
            val importantChunks = chunks.mapNotNull { chunk ->
                val importance = importanceCalculator.calculateImportance(
                    content = chunk.content,
                    context = conversationContext
                )

                if (importance >= minImportanceThreshold) {
                    ChunkWithImportance(chunk, importance)
                } else {
                    null  // Skip low-importance chunks
                }
            }

            if (importantChunks.isEmpty()) {
                return Result.success(0)  // No chunks met importance threshold
            }

            // Step 3: Generate embeddings (batch for efficiency)
            val texts = importantChunks.map { it.chunk.content }
            val embeddings = embeddingRepo.embedBatch(texts).getOrThrow()

            // Step 4: Store metadata + vectors
            var storedCount = 0
            importantChunks.forEachIndexed { index, chunkWithImportance ->
                val chunk = chunkWithImportance.chunk
                val embedding = embeddings[index]
                val embeddingId = "${chunk.id}_emb"

                // Store vector in search index
                vectorSearch.addVector(embeddingId, embedding).getOrThrow()

                // Store metadata in repository
                repository.createMemory(
                    id = chunk.id,
                    messageId = messageId,
                    projectId = projectId,
                    content = chunk.content,
                    importance = chunkWithImportance.importance,
                    createdAt = timestamp,
                    chunkIndex = index,
                    chunkTotal = chunks.size,
                    chunkTokens = chunk.tokenCount,
                    embeddingId = embeddingId
                )

                storedCount++
            }

            Result.success(storedCount)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retrieve relevant memories for query
     *
     * **Pipeline:**
     * 1. Embed query text
     * 2. Vector search for similar embeddings
     * 3. Fetch memory metadata from repository
     * 4. Rank with MemoryRanker (composite scoring)
     * 5. Select within token budget
     *
     * @param queryText User's current message
     * @param topK Number of candidates from vector search (default 20)
     * @return Result with assembled context
     */
    suspend fun retrieveRelevantMemories(
        queryText: String,
        topK: Int = 20
    ): Result<MemoryRankingResult> {
        return try {
            val embeddingRepo = embeddingRepository
                ?: return Result.failure(IllegalStateException("Embedding repository not initialized"))
            val vectorSearch = vectorSearch
                ?: return Result.failure(IllegalStateException("Vector search not initialized"))

            // Step 1: Embed query
            val queryEmbedding = embeddingRepo.embed(queryText).getOrThrow()

            // Step 2: Vector search
            val searchResults = vectorSearch.search(
                queryVector = queryEmbedding,
                k = topK
            ).getOrThrow()

            if (searchResults.isEmpty()) {
                return Result.success(MemoryRankingResult(
                    selectedMemories = emptyList(),
                    totalTokens = 0,
                    droppedCount = 0,
                    rankingScores = emptyMap()
                ))
            }

            // Step 3: Fetch memory metadata
            val memories = searchResults.mapNotNull { result ->
                repository.getMemoryByEmbeddingId(result.id)
            }

            // Step 4: Rank and select with memory ranker
            val rankingResult = memoryRanker.rankAndSelect(
                searchResults = searchResults,
                memories = memories,
                currentTimestamp = Clock.System.now().toEpochMilliseconds()
            )

            // Step 5: Update access tracking for selected memories
            val now = Clock.System.now().toEpochMilliseconds()
            rankingResult.selectedMemories.forEach { memory ->
                repository.updateMemoryAccess(memory.id, now)
            }

            Result.success(rankingResult)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get recent memories (temporal context, no semantic search)
     *
     * Useful for conversation continuity without specific query.
     *
     * @param limit Number of recent memories (default 10)
     * @return List of recent memories
     */
    fun getRecentMemories(limit: Int = 10): List<MemoryMetadata> {
        return repository.getRecentMemories(projectId, limit)
    }

    /**
     * Get high-importance memories
     *
     * Filter for quality content (importance >= 0.7)
     *
     * @param importanceThreshold Minimum importance (default 0.7)
     * @return List of high-importance memories
     */
    fun getHighImportanceMemories(importanceThreshold: Float = 0.7f): List<MemoryMetadata> {
        return repository.getHighImportanceMemories(projectId, importanceThreshold)
    }

    /**
     * Delete all memories for a message
     *
     * Cascade deletion when message is deleted from chat history.
     *
     * @param messageId Message to delete memories for
     */
    suspend fun deleteMemoriesForMessage(messageId: String): Result<Unit> {
        return try {
            val vectorSearch = vectorSearch
                ?: return Result.failure(IllegalStateException("Vector search not initialized"))

            // Get memories to delete
            val memories = repository.getMemoriesForMessage(messageId)

            // Delete from vector index
            memories.forEach { memory ->
                vectorSearch.removeVector(memory.embedding_id)
            }

            // Delete from repository
            repository.deleteMemoriesForMessage(messageId)

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clean up low-importance memories
     *
     * Remove memories below importance threshold to free storage.
     *
     * @param importanceThreshold Delete below this (default 0.3)
     * @return Result with count of deleted memories
     */
    suspend fun cleanupLowImportanceMemories(
        importanceThreshold: Float = 0.3f
    ): Result<Int> {
        return try {
            val vectorSearch = vectorSearch
                ?: return Result.failure(IllegalStateException("Vector search not initialized"))

            // Get low-importance memories
            val allMemories = repository.getMemoriesForProject(projectId)
            val lowImportanceMemories = allMemories.filter {
                it.importance < importanceThreshold && it.is_pinned == 0L
            }

            // Delete from vector index
            lowImportanceMemories.forEach { memory ->
                vectorSearch.removeVector(memory.embedding_id)
            }

            // Delete from repository
            repository.deleteLowImportanceMemories(projectId, importanceThreshold)

            Result.success(lowImportanceMemories.size)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get memory statistics
     *
     * @return Statistics about stored memories
     */
    fun getMemoryStats(): MemoryDataSourceStats? {
        return repository.getMemoryStats(projectId)
    }

    /**
     * Get memory count
     *
     * @return Total number of memories for project
     */
    fun getMemoryCount(): Long {
        return repository.getMemoryCount(projectId)
    }

    /**
     * Pin a memory (prevent deletion)
     *
     * @param memoryId Memory to pin
     */
    fun pinMemory(memoryId: String) {
        repository.pinMemory(memoryId)
    }

    /**
     * Unpin a memory (allow deletion)
     *
     * @param memoryId Memory to unpin
     */
    fun unpinMemory(memoryId: String) {
        repository.unpinMemory(memoryId)
    }
}

/**
 * Chunk with calculated importance
 */
private data class ChunkWithImportance(
    val chunk: Chunk,
    val importance: Float
)

/**
 * Vector search engine interface (platform-specific implementation)
 *
 * **ARCHITECTURE NOTE:**
 * This is a legacy interface local to MemoryManager.
 * No domain equivalent exists yet - would be VectorSearchRepository.
 * AndroidVectorSearchEngine adapts platform implementations to this interface.
 * TODO: Create domain.repositories.VectorSearchRepository when expanding domain layer.
 *
 * Performs nearest neighbor search over embedded vectors using cosine similarity.
 * Maintains an in-memory or disk-backed index for fast approximate or exact search.
 *
 * **Platform Implementations:**
 * - Android: VectorSearchManager (linear scan, exact search, <10ms @ 1K vectors)
 * - iOS: Accelerate BNNS or Core ML (future)
 *
 * **Similarity Metric:** Cosine similarity (normalized dot product), range [0.0, 1.0]
 * where 1.0 = identical vectors, 0.0 = orthogonal vectors.
 *
 * **Threading:**
 * - Implementations handle their own dispatcher switching (typically Dispatchers.Default)
 * - Safe to call from Main thread - will not block UI (suspends internally)
 * - Index updates (add/remove) use mutex or synchronized blocks to prevent corruption
 * - Search queries can run concurrently with minimal lock contention
 */
interface VectorSearchEngine {
    /**
     * Add or update vector in search index
     *
     * Inserts a new vector into the index or overwrites existing vector with same ID.
     * Vectors are automatically normalized to unit length for cosine similarity.
     *
     * @param id Unique vector identifier (typically "{memoryId}_emb")
     *           Must match the embedding_id in MemoryRepository
     * @param vector Embedding vector (any FloatArray, will be normalized)
     *               Must have same dimensions as all other vectors in index
     * @return Result.success if added successfully, OR
     *         Result.failure if:
     *         - Dimension mismatch with existing vectors
     *         - Index is locked (rare)
     *         - Disk write error (if persisted)
     * @see removeVector To delete vectors before re-adding
     */
    suspend fun addVector(id: String, vector: FloatArray): Result<Unit>

    /**
     * Search for k nearest neighbors using cosine similarity
     *
     * Returns the k most similar vectors to the query, ranked by similarity score.
     * If fewer than k vectors exist in index, returns all available vectors.
     *
     * @param queryVector Query embedding (any FloatArray, will be normalized)
     *                    Typically from EmbeddingEngine.embed(userQuery)
     * @param k Maximum number of results to return (top-k)
     *          Typical values: 10-50 for initial retrieval before re-ranking
     * @return Result.success with list of SearchResults (id, similarity) sorted descending by similarity.
     *         Returns empty list (not failure) if index is empty.
     *         Result.failure if:
     *         - Dimension mismatch with indexed vectors
     * @see SearchResult Data class containing vector ID and similarity score [0.0, 1.0]
     */
    suspend fun search(queryVector: FloatArray, k: Int): Result<List<SearchResult>>

    /**
     * Remove vector from search index
     *
     * Deletes the vector with given ID. Safe to call even if ID doesn't exist (no-op).
     * Should be called when deleting memories to prevent orphaned vectors in index.
     *
     * @param id Vector identifier to remove (must match addVector() ID)
     * @return Result.success even if ID not found, OR
     *         Result.failure if:
     *         - Disk write error (if persisted)
     *         - Index is locked (rare)
     */
    suspend fun removeVector(id: String): Result<Unit>
}
