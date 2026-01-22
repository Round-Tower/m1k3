package app.m1k3.ai.domain.repositories

/**
 * VectorSearchRepository - Domain contract for vector similarity search
 *
 * **ARCHITECTURE NOTE:**
 * This is a DOMAIN-LAYER interface (Clean Architecture).
 * - Used by: Domain use cases (SearchMemoriesUseCase)
 * - Implemented by: Platform-specific adapters
 *
 * ```
 * Domain Layer:     VectorSearchRepository (this) <- Use cases
 *                           |
 * Application Layer: VectorSearchManager        <- Lifecycle/caching
 *                           |
 * Platform Layer:    HnswIndex / LinearSearch  <- Actual implementation
 * ```
 *
 * Pure Kotlin interface with no platform dependencies.
 * Defines operations for nearest-neighbor vector search.
 *
 * **Philosophy:**
 * Query Vector -> Index Search -> Ranked Results
 * Repository abstracts index implementation (HNSW, linear, Faiss) from business logic.
 *
 * **Responsibilities:**
 * - Add vectors to search index
 * - Search for k-nearest neighbors
 * - Remove vectors from index
 * - Report index statistics
 *
 * **Platform Implementations:**
 * - Android: VectorSearchManager (linear cosine similarity, <10ms @ 1K vectors)
 * - iOS: Accelerate vDSP or custom HNSW (future)
 *
 * **Similarity Metric:** Cosine similarity (normalized dot product)
 * - Range: [0.0, 1.0] where 1.0 = identical, 0.0 = orthogonal
 *
 * **Usage:**
 * ```kotlin
 * val vectorRepo: VectorSearchRepository = get() // Koin injection
 *
 * // Add vector
 * vectorRepo.addVector("memory-123", embedding).onSuccess {
 *     println("Vector indexed")
 * }
 *
 * // Search
 * vectorRepo.search(queryEmbedding, k = 10).onSuccess { results ->
 *     results.forEach { (id, similarity) ->
 *         println("$id: ${(similarity * 100).toInt()}% similar")
 *     }
 * }
 *
 * // Remove
 * vectorRepo.removeVector("memory-123")
 * ```
 */
interface VectorSearchRepository {

    /**
     * Add or update vector in search index
     *
     * Inserts a new vector or overwrites existing vector with same ID.
     * Vectors are automatically normalized for cosine similarity.
     *
     * **Thread Safety:** Implementations must be thread-safe.
     *
     * @param id Unique vector identifier (e.g., "{memoryId}_emb")
     * @param vector Embedding vector (will be normalized)
     * @return Result.success if indexed, Result.failure on error
     */
    suspend fun addVector(id: String, vector: FloatArray): Result<Unit>

    /**
     * Search for k nearest neighbors
     *
     * Returns the k most similar vectors to the query, ranked by similarity.
     * If fewer than k vectors exist, returns all available.
     *
     * **Performance:**
     * - Linear scan: O(n), ~5-10ms for 1K vectors
     * - HNSW: O(log n), ~1-2ms for 100K vectors
     *
     * @param queryVector Query embedding (will be normalized)
     * @param k Maximum results to return (typical: 10-50)
     * @return Result with list of (id, similarity) pairs, sorted descending
     */
    suspend fun search(queryVector: FloatArray, k: Int): Result<List<VectorSearchResult>>

    /**
     * Remove vector from search index
     *
     * Safe to call even if ID doesn't exist (no-op).
     *
     * @param id Vector identifier to remove
     * @return Result.success (even if not found), Result.failure on error
     */
    suspend fun removeVector(id: String): Result<Unit>

    /**
     * Get index statistics
     *
     * @return Current index size and metadata
     */
    fun getStats(): VectorIndexStats
}

/**
 * Vector search result with ID and similarity score
 */
data class VectorSearchResult(
    /** Vector identifier */
    val id: String,
    /** Cosine similarity score [0.0, 1.0] */
    val similarity: Float
)

/**
 * Vector index statistics
 */
data class VectorIndexStats(
    /** Number of vectors in index */
    val vectorCount: Int,
    /** Dimension of indexed vectors */
    val dimensions: Int,
    /** Index type (e.g., "linear", "hnsw") */
    val indexType: String = "linear"
)
