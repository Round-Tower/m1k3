package app.m1k3.ai.assistant.memory

import app.m1k3.ai.assistant.embedding.VectorSearchManager
import app.m1k3.ai.assistant.embedding.SearchResult as PlatformSearchResult

/**
 * Android wrapper for VectorSearchEngine platform interface
 *
 * Bridges the existing VectorSearchManager implementation to the new
 * memory.VectorSearchEngine interface required by MemoryManager.
 *
 * This adapter pattern allows the new memory system to work with existing
 * vector search infrastructure without duplicating code.
 *
 * **Architecture:**
 * ```
 * MemoryManager → memory.VectorSearchEngine (interface)
 *                        ↓
 *             AndroidVectorSearchEngine (adapter)
 *                        ↓
 *             VectorSearchManager (implementation)
 *                        ↓
 *             Linear search or HNSW (future)
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val platformSearch = VectorSearchManager(context, dimensions, projectId)
 * platformSearch.initialize()
 * val memorySearch: memory.VectorSearchEngine = AndroidVectorSearchEngine(platformSearch)
 * val memoryManager = MemoryManager(
 *     embeddingEngine = androidEmbedding,
 *     vectorSearch = memorySearch,
 *     ...
 * )
 * ```
 */
class AndroidVectorSearchEngine(
    private val platformSearch: VectorSearchManager
) : app.m1k3.ai.assistant.memory.VectorSearchEngine {

    /**
     * Add vector to index
     *
     * @param id Vector ID (embedding_id from MemoryMetadata)
     * @param vector Embedding vector
     * @return Result indicating success or error
     */
    override suspend fun addVector(id: String, vector: FloatArray): Result<Unit> {
        return platformSearch.addVector(id, vector)
    }

    /**
     * Search for similar vectors
     *
     * Maps platform SearchResult to memory SearchResult.
     * Default minSimilarity is 0.0 in platform (accepts all results),
     * filtering will be done by MemoryRanker based on composite scoring.
     *
     * @param queryVector Query embedding
     * @param k Number of results to return
     * @return Result with search results (id + similarity)
     */
    override suspend fun search(
        queryVector: FloatArray,
        k: Int
    ): Result<List<app.m1k3.ai.assistant.memory.SearchResult>> {
        return try {
            // Call platform search with no similarity threshold
            // (MemoryRanker will do the ranking)
            val platformResult = platformSearch.search(
                queryVector = queryVector,
                k = k,
                minSimilarity = 0.0f
            ).getOrThrow()

            // Convert platform SearchResult to memory SearchResult
            val memoryResults = platformResult.map { platformResult ->
                app.m1k3.ai.assistant.memory.SearchResult(
                    id = platformResult.id,
                    similarity = platformResult.similarity
                )
            }

            Result.success(memoryResults)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove vector from index
     *
     * @param id Vector ID to remove
     * @return Result indicating success or error
     */
    override suspend fun removeVector(id: String): Result<Unit> {
        return platformSearch.removeVector(id)
    }
}
