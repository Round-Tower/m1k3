package app.m1k3.ai.domain.memory

/**
 * Memory Chunk - Text chunk with metadata
 *
 * Represents a single chunk of text from semantic chunking.
 * Used by SemanticChunker and MemoryManager for storage and retrieval.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * @property text The text content of this chunk
 * @property tokenCount Estimated number of tokens in this chunk
 * @property chunkIndex 0-based index of this chunk in the sequence
 * @property totalChunks Total number of chunks in the sequence
 */
data class MemoryChunk(
    val text: String,
    val tokenCount: Int,
    val chunkIndex: Int,
    val totalChunks: Int
) {
    /**
     * Whether this is the first chunk in the sequence
     */
    val isFirstChunk: Boolean
        get() = chunkIndex == 0

    /**
     * Whether this is the last chunk in the sequence
     */
    val isLastChunk: Boolean
        get() = chunkIndex == totalChunks - 1
}
