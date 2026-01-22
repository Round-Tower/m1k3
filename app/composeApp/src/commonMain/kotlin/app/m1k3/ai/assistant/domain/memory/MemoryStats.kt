package app.m1k3.ai.assistant.domain.memory

/**
 * Memory System Statistics
 *
 * Aggregated metrics about the semantic memory system state.
 * Used by MemoryRepository for monitoring and debugging.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * @property totalMemories Total number of memories stored
 * @property averageImportance Average importance score across all memories
 * @property averageDecay Average decay factor (1.0 = no decay, 0.0 = fully decayed)
 * @property totalAccesses Total number of memory accesses
 * @property pinnedCount Number of pinned memories (prevent decay)
 * @property vectorCount Number of vectors in index
 * @property embeddingDimensions Dimensionality of embedding vectors
 * @property hasVectorIndex Whether vector index is initialized
 */
data class MemoryStats(
    val totalMemories: Long,
    val averageImportance: Float,
    val averageDecay: Float = 1.0f,
    val totalAccesses: Long = 0L,
    val pinnedCount: Long = 0L,
    val vectorCount: Long = 0L,
    val embeddingDimensions: Int = 0,
    val hasVectorIndex: Boolean
) {
    /**
     * Memory health score (0.0-1.0) based on importance and decay
     */
    val healthScore: Float
        get() = (averageImportance * averageDecay).coerceIn(0f, 1f)

    /**
     * Whether the memory system is healthy (health score >= 0.5)
     */
    val isHealthy: Boolean
        get() = healthScore >= 0.5f
}
