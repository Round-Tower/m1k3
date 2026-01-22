package app.m1k3.ai.assistant.domain.usecases.memory

import app.m1k3.ai.assistant.domain.memory.MemorySearchResult
import app.m1k3.ai.assistant.domain.repositories.MemoryRepository

/**
 * SearchMemoriesUseCase - Search semantic memories by similarity
 *
 * Domain use case for retrieving relevant memories based on query similarity.
 * Pure Kotlin, no platform dependencies.
 *
 * **Philosophy:**
 * Simple orchestration layer. Repository handles complexity,
 * use case enforces validation and business rules.
 *
 * **Orchestration:**
 * 1. Validate query (non-blank)
 * 2. Validate parameters (topK > 0, minSimilarity 0-1)
 * 3. Delegate to repository for semantic search
 * 4. Return ranked results
 *
 * **Usage:**
 * ```kotlin
 * val searchMemories = SearchMemoriesUseCase(memoryRepository)
 *
 * // Search with defaults (topK=10, minSimilarity=0.5)
 * searchMemories.execute("photosynthesis").onSuccess { memories ->
 *     memories.forEach { println(it.content) }
 * }
 *
 * // Search with custom parameters
 * searchMemories.execute(
 *     query = "AI concepts",
 *     topK = 5,
 *     minSimilarity = 0.7f
 * ).onSuccess { memories ->
 *     println("Found ${memories.size} highly relevant memories")
 * }
 * ```
 */
class SearchMemoriesUseCase(
    private val memoryRepository: MemoryRepository
) {
    /**
     * Execute memory search
     *
     * @param query User's search query (must not be blank)
     * @param topK Maximum results to return (default: 10, must be > 0)
     * @param minSimilarity Minimum similarity threshold 0.0-1.0 (default: 0.5)
     * @return Result.success(List<MemorySearchResult>) or Result.failure
     */
    suspend fun execute(
        query: String,
        topK: Int = 10,
        minSimilarity: Float = 0.5f
    ): Result<List<MemorySearchResult>> {
        // Validate query
        if (query.isBlank()) {
            return Result.failure(IllegalArgumentException("Query cannot be blank"))
        }

        // Validate topK
        if (topK <= 0) {
            return Result.failure(IllegalArgumentException("topK must be greater than 0"))
        }

        // Validate minSimilarity
        if (minSimilarity < 0f || minSimilarity > 1f) {
            return Result.failure(
                IllegalArgumentException("minSimilarity must be between 0.0 and 1.0")
            )
        }

        // Delegate to repository
        return memoryRepository.searchMemories(query, topK, minSimilarity)
    }
}
