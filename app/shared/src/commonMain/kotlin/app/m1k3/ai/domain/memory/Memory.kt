package app.m1k3.ai.domain.memory

/**
 * Memory - Domain entity for semantic memory.
 *
 * Represents a chunk of semantically meaningful content from a conversation,
 * stored with importance scoring and decay tracking for intelligent retrieval.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 * Maps to/from platform-specific storage (SQLDelight MemoryMetadata).
 *
 * **Lifecycle:**
 * 1. Created from message content during conversation
 * 2. Importance calculated based on content and context
 * 3. Embedding generated and stored in vector index
 * 4. Retrieved via semantic similarity search
 * 5. Decays over time unless pinned or frequently accessed
 *
 * @property id Unique memory identifier
 * @property messageId Source message ID
 * @property projectId Project/conversation ID
 * @property content Semantic chunk text
 * @property importance Importance score (0.0-1.0, threshold: 0.3)
 * @property createdAt Creation timestamp (epoch milliseconds)
 * @property chunkIndex Position in original message (0-based)
 * @property chunkTotal Total chunks from source message
 * @property chunkTokens Token count for this chunk
 * @property embeddingId ID in vector index
 * @property embeddingModel Model used for embedding
 * @property accessCount Times retrieved via RAG
 * @property lastAccessedAt Last RAG retrieval timestamp
 * @property decayFactor Decay factor (1.0 = fresh, 0.0 = forgotten)
 * @property isPinned Whether memory is protected from decay/cleanup
 */
data class Memory(
    val id: String,
    val messageId: String,
    val projectId: String,
    val content: String,
    val importance: Float,
    val createdAt: Long,
    val chunkIndex: Int,
    val chunkTotal: Int,
    val chunkTokens: Int? = null,
    val embeddingId: String,
    val embeddingModel: String = DEFAULT_EMBEDDING_MODEL,
    val accessCount: Int = 0,
    val lastAccessedAt: Long? = null,
    val decayFactor: Float = 1.0f,
    val isPinned: Boolean = false
) {
    /**
     * Effective importance considering decay.
     *
     * Combines base importance with decay factor for retrieval ranking.
     */
    val effectiveImportance: Float
        get() = importance * decayFactor

    /**
     * Whether this memory is considered high importance (>= 0.7).
     */
    val isHighImportance: Boolean
        get() = importance >= HIGH_IMPORTANCE_THRESHOLD

    /**
     * Whether this memory is decayed below usefulness threshold.
     */
    val isDecayed: Boolean
        get() = decayFactor < DECAY_THRESHOLD

    /**
     * Whether this memory should be cleaned up.
     *
     * Pinned memories are never eligible for cleanup.
     */
    val isEligibleForCleanup: Boolean
        get() = !isPinned && (importance < CLEANUP_THRESHOLD || isDecayed)

    /**
     * Convert to MemorySearchResult with a given similarity score.
     *
     * Used after vector search to create retrieval results.
     *
     * @param similarity Cosine similarity from vector search
     * @return MemorySearchResult for retrieval
     */
    fun toSearchResult(similarity: Float): MemorySearchResult {
        return MemorySearchResult(
            id = id,
            content = content,
            importance = importance,
            similarity = similarity,
            chunkIndex = chunkIndex,
            chunkTotal = chunkTotal,
            messageId = messageId,
            createdAt = createdAt
        )
    }

    companion object {
        /** Default embedding model (384-dimensional) */
        const val DEFAULT_EMBEDDING_MODEL = "all-MiniLM-L6-v2"

        /** Importance threshold for high-importance classification */
        const val HIGH_IMPORTANCE_THRESHOLD = 0.7f

        /** Minimum importance for memory persistence */
        const val CLEANUP_THRESHOLD = 0.3f

        /** Decay threshold below which memory is considered forgotten */
        const val DECAY_THRESHOLD = 0.1f

        /**
         * Create from a MemorySearchResult (for testing/mocking).
         *
         * Note: Some fields will have default values.
         */
        fun fromSearchResult(
            result: MemorySearchResult,
            projectId: String,
            embeddingId: String
        ): Memory {
            return Memory(
                id = result.id,
                messageId = result.messageId,
                projectId = projectId,
                content = result.content,
                importance = result.importance,
                createdAt = result.createdAt,
                chunkIndex = result.chunkIndex,
                chunkTotal = result.chunkTotal,
                embeddingId = embeddingId
            )
        }
    }
}
