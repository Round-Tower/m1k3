package app.m1k3.ai.assistant.memory

import app.m1k3.ai.assistant.embedding.VectorSearchManager
import app.m1k3.ai.assistant.embedding.SearchResult as PlatformSearchResult
import app.m1k3.ai.domain.repositories.VectorSearchRepository
import app.m1k3.ai.domain.repositories.VectorSearchResult
import app.m1k3.ai.domain.repositories.VectorIndexStats

/**
 * Android adapter implementing domain VectorSearchRepository
 *
 * Bridges the existing VectorSearchManager implementation to the domain-layer
 * VectorSearchRepository interface required by MemoryManager.
 *
 * This adapter pattern allows the new memory system to work with existing
 * vector search infrastructure without duplicating code.
 *
 * **Architecture:**
 * ```
 * MemoryManager → VectorSearchRepository (domain interface)
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
 * val memorySearch: VectorSearchRepository = AndroidVectorSearchEngine(platformSearch)
 * val memoryManager = MemoryManager(
 *     embeddingRepository = androidEmbedding,
 *     vectorSearchRepository = memorySearch,
 *     ...
 * )
 * ```
 */
class AndroidVectorSearchEngine(
    private val platformSearch: VectorSearchManager
) : VectorSearchRepository {

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
     * Maps platform SearchResult to domain VectorSearchResult.
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
    ): Result<List<VectorSearchResult>> {
        return try {
            // Call platform search with no similarity threshold
            // (MemoryRanker will do the ranking)
            val platformResult = platformSearch.search(
                queryVector = queryVector,
                k = k,
                minSimilarity = 0.0f
            ).getOrThrow()

            // Convert platform SearchResult to domain VectorSearchResult
            val domainResults = platformResult.map { platformResult ->
                VectorSearchResult(
                    id = platformResult.id,
                    similarity = platformResult.similarity
                )
            }

            Result.success(domainResults)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get index statistics
     *
     * Returns current vector count and dimensions from platform search.
     *
     * @return VectorIndexStats with count, dimensions, and index type
     */
    override fun getStats(): VectorIndexStats {
        val platformStats = platformSearch.getStats()
        return VectorIndexStats(
            vectorCount = platformStats.vectorCount,
            dimensions = platformStats.dimensions,
            indexType = "hnsw"  // VectorSearchManager uses JVector HNSW
        )
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
