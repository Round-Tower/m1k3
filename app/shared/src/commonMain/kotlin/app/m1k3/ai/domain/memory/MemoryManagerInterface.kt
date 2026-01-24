package app.m1k3.ai.domain.memory

/**
 * Memory Manager Interface - Semantic memory system abstraction.
 *
 * High-level interface for the semantic memory system, providing:
 * - Memory creation from messages
 * - Semantic retrieval based on query similarity
 * - Memory lifecycle management (cleanup, pinning)
 * - Statistics and monitoring
 *
 * Domain interface - Pure Kotlin, no platform dependencies.
 * Platform implementations coordinate chunking, embedding, and storage.
 *
 * **Architecture:**
 * ```
 * Message → SemanticChunker → [Chunks]
 *                                  ↓
 *                          ImportanceCalculator
 *                                  ↓
 *                          EmbeddingRepository → [Vectors]
 *                                  ↓
 *                     MemoryRepository + VectorSearch
 * ```
 *
 * @see MemorySearchResult for individual memory results
 * @see MemoryRetrievalResult for retrieval results with metadata
 * @see MemoryStats for system statistics
 */
interface MemoryManagerInterface {

    /**
     * Create memories from message content.
     *
     * Pipeline:
     * 1. Chunk message (100-300 tokens, semantic boundaries)
     * 2. Calculate importance for each chunk
     * 3. Filter low-importance chunks (< threshold)
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
    ): Result<Int>

    /**
     * Retrieve relevant memories for query.
     *
     * Pipeline:
     * 1. Embed query text
     * 2. Vector search for similar embeddings
     * 3. Fetch memory metadata
     * 4. Rank with composite scoring
     * 5. Select within token budget
     *
     * @param queryText User's current message
     * @param topK Number of candidates from vector search (default 20)
     * @return Result with retrieval results including memories and metadata
     */
    suspend fun retrieveRelevantMemories(
        queryText: String,
        topK: Int = 20
    ): Result<MemoryRetrievalResult>

    /**
     * Get recent memories (temporal context, no semantic search).
     *
     * Useful for conversation continuity without specific query.
     *
     * @param limit Number of recent memories (default 10)
     * @return List of recent memories
     */
    fun getRecentMemories(limit: Int = 10): List<MemorySearchResult>

    /**
     * Get high-importance memories.
     *
     * Filter for quality content above importance threshold.
     *
     * @param importanceThreshold Minimum importance (default 0.7)
     * @return List of high-importance memories
     */
    fun getHighImportanceMemories(importanceThreshold: Float = 0.7f): List<MemorySearchResult>

    /**
     * Delete all memories for a message.
     *
     * Cascade deletion when message is deleted from chat history.
     *
     * @param messageId Message to delete memories for
     * @return Result indicating success or failure
     */
    suspend fun deleteMemoriesForMessage(messageId: String): Result<Unit>

    /**
     * Clean up low-importance memories.
     *
     * Remove memories below importance threshold to free storage.
     * Pinned memories are protected from cleanup.
     *
     * @param importanceThreshold Delete below this (default 0.3)
     * @return Result with count of deleted memories
     */
    suspend fun cleanupLowImportanceMemories(
        importanceThreshold: Float = 0.3f
    ): Result<Int>

    /**
     * Get memory statistics.
     *
     * @return Statistics about stored memories, or null if unavailable
     */
    fun getMemoryStats(): MemoryStats?

    /**
     * Get memory count.
     *
     * @return Total number of memories for project
     */
    fun getMemoryCount(): Long

    /**
     * Pin a memory (prevent deletion during cleanup).
     *
     * @param memoryId Memory to pin
     */
    fun pinMemory(memoryId: String)

    /**
     * Unpin a memory (allow deletion during cleanup).
     *
     * @param memoryId Memory to unpin
     */
    fun unpinMemory(memoryId: String)
}
