package app.m1k3.ai.assistant.memory.test

import app.m1k3.ai.assistant.memory.EmbeddingEngine
import app.m1k3.ai.assistant.memory.SearchResult
import app.m1k3.ai.assistant.memory.VectorSearchEngine

/**
 * Shared test utilities for Phase 2 Memory System tests.
 *
 * Provides mock implementations of EmbeddingEngine and VectorSearchEngine
 * for consistent testing across MemoryManagerTest, MemoryRetrievalQualityTest,
 * and MemoryIntegrationTest.
 */

/**
 * Mock embedding engine that generates deterministic embeddings based on text hash.
 *
 * **Behavior:**
 * - Returns embeddings with values in range [0.0, 1.0]
 * - Same text always produces same embedding (deterministic)
 * - Useful for testing memory creation and storage
 *
 * **Usage:**
 * ```kotlin
 * val mockEngine = MockEmbeddingEngine(dimensions = 384)
 * val memoryManager = MemoryManager(..., embeddingEngine = mockEngine)
 * ```
 */
class MockEmbeddingEngine(
    override val dimensions: Int = 384
) : EmbeddingEngine {

    override suspend fun embed(texts: List<String>): Result<List<FloatArray>> {
        // Generate deterministic embeddings based on text content
        val embeddings = texts.map { text ->
            FloatArray(dimensions) { i ->
                // Use text hash + index to create deterministic but varied values
                ((text.hashCode() + i) % 100) / 100f
            }
        }
        return Result.success(embeddings)
    }
}

/**
 * Mock vector search engine with configurable search results.
 *
 * **Behavior:**
 * - Stores vectors in memory (HashMap)
 * - Can return pre-configured search results via setSearchResults()
 * - Falls back to returning all vectors if no results configured
 * - Useful for testing retrieval and ranking logic
 *
 * **Usage:**
 * ```kotlin
 * val mockSearch = MockVectorSearchEngine()
 * mockSearch.setSearchResults(listOf(
 *     SearchResult("mem-1", 0.95f),
 *     SearchResult("mem-2", 0.80f)
 * ))
 * val memoryManager = MemoryManager(..., vectorSearch = mockSearch)
 * ```
 */
class MockVectorSearchEngine : VectorSearchEngine {
    private val vectors = mutableMapOf<String, FloatArray>()
    private var searchResults: List<SearchResult>? = null

    /**
     * Configure search results to return for next search() call.
     *
     * @param results List of SearchResult with memory IDs and similarity scores
     */
    fun setSearchResults(results: List<SearchResult>) {
        searchResults = results
    }

    /**
     * Get current vector count (useful for assertions).
     */
    fun vectorCount(): Int = vectors.size

    /**
     * Add vector directly (useful for test setup).
     */
    fun addVectorInternal(id: String, vector: FloatArray) {
        vectors[id] = vector
    }

    override suspend fun addVector(id: String, vector: FloatArray): Result<Unit> {
        vectors[id] = vector
        return Result.success(Unit)
    }

    override suspend fun search(queryVector: FloatArray, k: Int): Result<List<SearchResult>> {
        // Return pre-configured results if set, otherwise return all vectors
        val results = searchResults ?: vectors.keys.sorted().take(k).map { id ->
            SearchResult(id, 0.9f)  // Dummy high similarity
        }
        return Result.success(results.take(k))
    }

    override suspend fun removeVector(id: String): Result<Unit> {
        vectors.remove(id)
        return Result.success(Unit)
    }
}

/**
 * Deterministic embedding engine with configurable query vector.
 *
 * **Behavior:**
 * - Returns the same configured vector for all texts (useful for retrieval testing)
 * - Allows setting a specific query vector to control similarity scores
 * - Falls back to hash-based embedding if no query vector set
 *
 * **Usage:**
 * ```kotlin
 * val engine = DeterministicEmbeddingEngine()
 * engine.setQueryVector(floatArrayOf(1.0f, 0.0f, 0.0f))  // France vector
 * ```
 *
 * Used in MemoryRetrievalQualityTest to test precision/recall with known relevance.
 */
class DeterministicEmbeddingEngine(
    override val dimensions: Int = 384
) : EmbeddingEngine {
    private var queryVector: FloatArray? = null

    /**
     * Set the query vector to return for all embed() calls.
     *
     * @param vector FloatArray of length `dimensions`
     */
    fun setQueryVector(vector: FloatArray) {
        require(vector.size == dimensions) { "Vector must be $dimensions-dimensional" }
        queryVector = vector
    }

    override suspend fun embed(texts: List<String>): Result<List<FloatArray>> {
        // Use query vector if set, otherwise use content hash
        val embeddings = texts.map { text ->
            queryVector?.copyOf() ?: FloatArray(dimensions) { i ->
                ((text.hashCode() + i) % 100) / 100f
            }
        }
        return Result.success(embeddings)
    }
}

/**
 * Deterministic vector search engine with configurable search results.
 *
 * **Behavior:**
 * - Stores vectors internally
 * - Returns pre-configured search results (no actual similarity calculation)
 * - Useful for testing retrieval quality with known ground truth
 *
 * **Usage:**
 * ```kotlin
 * val engine = DeterministicVectorSearchEngine()
 * engine.setSearchResults(listOf(
 *     SearchResult("mem-paris", 0.95f),      // Relevant
 *     SearchResult("mem-python", 0.60f)      // Not relevant
 * ))
 * ```
 *
 * Used in MemoryRetrievalQualityTest to validate precision/recall metrics.
 */
class DeterministicVectorSearchEngine : VectorSearchEngine {
    private val vectors = mutableMapOf<String, FloatArray>()
    private var searchResults: List<SearchResult>? = null

    /**
     * Configure search results to return for next search() call.
     */
    fun setSearchResults(results: List<SearchResult>) {
        searchResults = results
    }

    /**
     * Add vector directly without going through interface.
     */
    fun addVectorInternal(id: String, vector: FloatArray) {
        vectors[id] = vector
    }

    override suspend fun addVector(id: String, vector: FloatArray): Result<Unit> {
        vectors[id] = vector
        return Result.success(Unit)
    }

    override suspend fun search(queryVector: FloatArray, k: Int): Result<List<SearchResult>> {
        // Return pre-configured results if set, otherwise return all
        val results = searchResults ?: vectors.keys.map { id ->
            SearchResult(id, 0.8f)
        }
        return Result.success(results.take(k))
    }

    override suspend fun removeVector(id: String): Result<Unit> {
        vectors.remove(id)
        return Result.success(Unit)
    }
}
