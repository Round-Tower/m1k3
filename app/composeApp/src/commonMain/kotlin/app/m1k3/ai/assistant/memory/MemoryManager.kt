package app.m1k3.ai.assistant.memory

import app.m1k3.ai.assistant.database.MemoryMetadata
import app.m1k3.ai.assistant.domain.memory.ImportanceCalculator
import app.m1k3.ai.assistant.domain.memory.ConversationContext

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
 *                        ContextAssembler → Ranked Context
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
    private val repository: MemoryRepository,
    private val importanceCalculator: ImportanceCalculator,
    private val contextAssembler: ContextAssembler,
    private val projectId: String,
    private val minImportanceThreshold: Float = 0.3f
) {

    /**
     * Embedding engine and vector search (platform-specific)
     * Must be set after initialization
     */
    var embeddingEngine: EmbeddingEngine? = null
    var vectorSearch: VectorSearchEngine? = null

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
            val embeddingEngine = embeddingEngine
                ?: return Result.failure(IllegalStateException("Embedding engine not initialized"))
            val vectorSearch = vectorSearch
                ?: return Result.failure(IllegalStateException("Vector search not initialized"))

            val timestamp = System.currentTimeMillis()

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
            val embeddings = embeddingEngine.embed(texts).getOrThrow()

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
     * 4. Rank with ContextAssembler (composite scoring)
     * 5. Select within token budget
     *
     * @param queryText User's current message
     * @param topK Number of candidates from vector search (default 20)
     * @return Result with assembled context
     */
    suspend fun retrieveRelevantMemories(
        queryText: String,
        topK: Int = 20
    ): Result<ContextResult> {
        return try {
            val embeddingEngine = embeddingEngine
                ?: return Result.failure(IllegalStateException("Embedding engine not initialized"))
            val vectorSearch = vectorSearch
                ?: return Result.failure(IllegalStateException("Vector search not initialized"))

            // Step 1: Embed query
            val queryEmbedding = embeddingEngine.embed(listOf(queryText))
                .getOrThrow()
                .first()

            // Step 2: Vector search
            val searchResults = vectorSearch.search(
                queryVector = queryEmbedding,
                k = topK
            ).getOrThrow()

            if (searchResults.isEmpty()) {
                return Result.success(ContextResult(
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

            // Step 4: Rank and select with context assembler
            val context = contextAssembler.assembleContext(
                searchResults = searchResults,
                memories = memories,
                currentTimestamp = System.currentTimeMillis()
            )

            // Step 5: Update access tracking for selected memories
            val now = System.currentTimeMillis()
            context.selectedMemories.forEach { memory ->
                repository.updateMemoryAccess(memory.id, now)
            }

            Result.success(context)

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
    fun getMemoryStats(): MemoryRepositoryStats? {
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
 * Embedding engine interface (platform-specific implementation)
 */
interface EmbeddingEngine {
    /**
     * Generate embeddings for texts
     *
     * @param texts List of text strings to embed
     * @return Result with list of embedding vectors
     */
    suspend fun embed(texts: List<String>): Result<List<FloatArray>>

    /**
     * Get embedding dimensions
     */
    val dimensions: Int
}

/**
 * Vector search engine interface (platform-specific implementation)
 */
interface VectorSearchEngine {
    /**
     * Add vector to index
     *
     * @param id Vector ID (embedding_id)
     * @param vector Embedding vector
     */
    suspend fun addVector(id: String, vector: FloatArray): Result<Unit>

    /**
     * Search for similar vectors
     *
     * @param queryVector Query embedding
     * @param k Number of results
     * @return Result with search results (id + similarity)
     */
    suspend fun search(queryVector: FloatArray, k: Int): Result<List<SearchResult>>

    /**
     * Remove vector from index
     *
     * @param id Vector ID to remove
     */
    suspend fun removeVector(id: String): Result<Unit>
}
