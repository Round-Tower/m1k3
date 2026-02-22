package app.m1k3.ai.domain.repositories

import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * VectorSearchRepository Contract Tests
 *
 * Tests the contract defined by VectorSearchRepository interface.
 * Uses a simple in-memory implementation to validate expected behaviors.
 */
class VectorSearchRepositoryTest {

    private lateinit var repository: VectorSearchRepository

    @BeforeTest
    fun setup() {
        repository = InMemoryVectorSearchRepository()
    }

    // ===== addVector Tests =====

    @Test
    fun `addVector stores vector successfully`() = runTest {
        val vector = floatArrayOf(1.0f, 0.0f, 0.0f)

        val result = repository.addVector("vec-1", vector)

        assertTrue(result.isSuccess)
        assertEquals(1, repository.getStats().vectorCount)
    }

    @Test
    fun `addVector overwrites existing vector with same id`() = runTest {
        val vector1 = floatArrayOf(1.0f, 0.0f, 0.0f)
        val vector2 = floatArrayOf(0.0f, 1.0f, 0.0f)

        repository.addVector("vec-1", vector1)
        repository.addVector("vec-1", vector2)

        assertEquals(1, repository.getStats().vectorCount)
    }

    // ===== search Tests =====

    @Test
    fun `search returns empty list when index is empty`() = runTest {
        val query = floatArrayOf(1.0f, 0.0f, 0.0f)

        val result = repository.search(query, k = 10)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `search returns results sorted by similarity descending`() = runTest {
        // Add three vectors
        repository.addVector("similar", floatArrayOf(1.0f, 0.0f, 0.0f))
        repository.addVector("orthogonal", floatArrayOf(0.0f, 1.0f, 0.0f))
        repository.addVector("half-similar", floatArrayOf(0.7f, 0.7f, 0.0f))

        // Query for vector similar to first
        val query = floatArrayOf(1.0f, 0.0f, 0.0f)
        val results = repository.search(query, k = 3).getOrThrow()

        assertEquals(3, results.size)
        assertEquals("similar", results[0].id)
        assertTrue(results[0].similarity > results[1].similarity)
        assertTrue(results[1].similarity > results[2].similarity)
    }

    @Test
    fun `search respects k limit`() = runTest {
        repeat(10) { i ->
            repository.addVector("vec-$i", floatArrayOf(i.toFloat(), 0.0f, 0.0f))
        }

        val query = floatArrayOf(1.0f, 0.0f, 0.0f)
        val results = repository.search(query, k = 5).getOrThrow()

        assertEquals(5, results.size)
    }

    @Test
    fun `search returns all vectors when k exceeds index size`() = runTest {
        repository.addVector("vec-1", floatArrayOf(1.0f, 0.0f, 0.0f))
        repository.addVector("vec-2", floatArrayOf(0.0f, 1.0f, 0.0f))

        val query = floatArrayOf(1.0f, 0.0f, 0.0f)
        val results = repository.search(query, k = 100).getOrThrow()

        assertEquals(2, results.size)
    }

    // ===== removeVector Tests =====

    @Test
    fun `removeVector removes existing vector`() = runTest {
        repository.addVector("vec-1", floatArrayOf(1.0f, 0.0f, 0.0f))
        assertEquals(1, repository.getStats().vectorCount)

        val result = repository.removeVector("vec-1")

        assertTrue(result.isSuccess)
        assertEquals(0, repository.getStats().vectorCount)
    }

    @Test
    fun `removeVector succeeds for non-existent id`() = runTest {
        val result = repository.removeVector("non-existent")

        assertTrue(result.isSuccess)
    }

    // ===== getStats Tests =====

    @Test
    fun `getStats returns correct vector count`() = runTest {
        assertEquals(0, repository.getStats().vectorCount)

        repository.addVector("vec-1", floatArrayOf(1.0f, 0.0f, 0.0f))
        assertEquals(1, repository.getStats().vectorCount)

        repository.addVector("vec-2", floatArrayOf(0.0f, 1.0f, 0.0f))
        assertEquals(2, repository.getStats().vectorCount)
    }

    // ===== VectorSearchResult Tests =====

    @Test
    fun `VectorSearchResult holds id and similarity`() {
        val result = VectorSearchResult("test-id", 0.95f)

        assertEquals("test-id", result.id)
        assertEquals(0.95f, result.similarity)
    }

    // ===== VectorIndexStats Tests =====

    @Test
    fun `VectorIndexStats holds stats correctly`() {
        val stats = VectorIndexStats(
            vectorCount = 100,
            dimensions = 384,
            indexType = "hnsw"
        )

        assertEquals(100, stats.vectorCount)
        assertEquals(384, stats.dimensions)
        assertEquals("hnsw", stats.indexType)
    }

    @Test
    fun `VectorIndexStats has linear default type`() {
        val stats = VectorIndexStats(vectorCount = 50, dimensions = 512)

        assertEquals("linear", stats.indexType)
    }
}

/**
 * Simple in-memory implementation for testing
 */
private class InMemoryVectorSearchRepository : VectorSearchRepository {
    private val vectors = mutableMapOf<String, FloatArray>()

    override suspend fun addVector(id: String, vector: FloatArray): Result<Unit> {
        vectors[id] = normalize(vector)
        return Result.success(Unit)
    }

    override suspend fun search(queryVector: FloatArray, k: Int): Result<List<VectorSearchResult>> {
        if (vectors.isEmpty()) {
            return Result.success(emptyList())
        }

        val normalizedQuery = normalize(queryVector)
        val results = vectors.map { (id, vec) ->
            VectorSearchResult(id, cosineSimilarity(normalizedQuery, vec))
        }.sortedByDescending { it.similarity }
         .take(k)

        return Result.success(results)
    }

    override suspend fun removeVector(id: String): Result<Unit> {
        vectors.remove(id)
        return Result.success(Unit)
    }

    override fun getStats(): VectorIndexStats {
        val dims = vectors.values.firstOrNull()?.size ?: 0
        return VectorIndexStats(
            vectorCount = vectors.size,
            dimensions = dims,
            indexType = "linear"
        )
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = kotlin.math.sqrt(norm)
        return if (norm > 0f) FloatArray(vector.size) { vector[it] / norm } else vector
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot  // Already normalized
    }
}
