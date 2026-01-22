package app.m1k3.ai.domain.memory

/**
 * Memory Search Result
 *
 * Represents a memory retrieved from semantic search with similarity scoring.
 * Used by MemoryRepository and SearchMemoriesUseCase.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * @property id Unique memory ID
 * @property content Memory text content
 * @property importance Importance score (0.0-1.0)
 * @property similarity Cosine similarity to query (0.0-1.0)
 * @property chunkIndex Index of this chunk in original message
 * @property chunkTotal Total chunks in original message
 * @property messageId Source message ID
 * @property createdAt Timestamp when memory was created (milliseconds since epoch)
 */
data class MemorySearchResult(
    val id: String,
    val content: String,
    val importance: Float,
    val similarity: Float,
    val chunkIndex: Int,
    val chunkTotal: Int,
    val messageId: String,
    val createdAt: Long
) {
    /**
     * Whether this memory is high quality (similarity >= 0.8)
     */
    val isHighQuality: Boolean
        get() = similarity >= 0.8f

    /**
     * Whether this memory is highly important (importance >= 0.7)
     */
    val isHighImportance: Boolean
        get() = importance >= 0.7f
}
