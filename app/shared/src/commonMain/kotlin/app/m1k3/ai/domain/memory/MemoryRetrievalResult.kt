package app.m1k3.ai.domain.memory

/**
 * Memory Retrieval Result
 *
 * Result of retrieving relevant memories from semantic search.
 * Contains the matched memories along with metadata about the retrieval.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * @property memories List of retrieved memories with similarity scores
 * @property totalTokens Approximate token count of all memory content
 * @property droppedCount Number of memories dropped due to token budget
 */
data class MemoryRetrievalResult(
    val memories: List<MemorySearchResult>,
    val totalTokens: Int,
    val droppedCount: Int
) {
    /**
     * Whether any memories were retrieved
     */
    val hasMemories: Boolean
        get() = memories.isNotEmpty()

    /**
     * Number of memories retrieved
     */
    val memoryCount: Int
        get() = memories.size

    /**
     * Get memories ordered by chunk index for coherent reading
     */
    fun getOrderedByChunks(): List<MemorySearchResult> {
        return memories.sortedWith(
            compareBy({ it.messageId }, { it.chunkIndex })
        )
    }

    /**
     * Get memories ordered by similarity (highest first)
     */
    fun getOrderedBySimilarity(): List<MemorySearchResult> {
        return memories.sortedByDescending { it.similarity }
    }

    /**
     * Format memories as context string for prompt injection
     */
    fun formatAsContext(): String {
        if (memories.isEmpty()) return ""

        return buildString {
            appendLine("Relevant memories:")
            getOrderedByChunks().forEach { memory ->
                appendLine("- ${memory.content}")
            }
        }
    }

    companion object {
        /**
         * Create an empty result
         */
        fun empty(): MemoryRetrievalResult = MemoryRetrievalResult(
            memories = emptyList(),
            totalTokens = 0,
            droppedCount = 0
        )
    }
}
