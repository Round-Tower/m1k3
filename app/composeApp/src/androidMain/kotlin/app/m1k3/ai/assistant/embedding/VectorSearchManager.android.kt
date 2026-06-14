package app.m1k3.ai.assistant.embedding

import android.content.Context
import android.util.Log
import io.github.jbellis.jvector.graph.GraphIndexBuilder
import io.github.jbellis.jvector.graph.GraphSearcher
import io.github.jbellis.jvector.vector.VectorSimilarityFunction
import io.github.jbellis.jvector.vector.types.VectorFloat
import io.github.jbellis.jvector.graph.OnHeapGraphIndex
import io.github.jbellis.jvector.graph.RandomAccessVectorValues
import io.github.jbellis.jvector.vector.VectorizationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.*

class VectorSearchManager(
    private val context: Context,
    private val dimensions: Int = 512,
    private val projectId: String
) {
    companion object {
        private const val TAG = "VectorSearchManager"

        private fun getIndexFile(context: Context, projectId: String) =
            File(context.filesDir, "hnsw_index_$projectId.bin")

        private fun getMetadataFile(context: Context, projectId: String) =
            File(context.filesDir, "hnsw_metadata_$projectId.json")
    }

    private var idToVector = mutableMapOf<String, FloatArray>()
    private var vectorCount = 0
    private val mutex = Mutex()

    // JVector HNSW graph integration
    private var jVectorIndex: OnHeapGraphIndex? = null
    private var isIndexStale = false
    private var idToOrdinal = mutableMapOf<String, Int>()
    private var ordinalToId = mutableMapOf<Int, String>()
    private val vectorTypeSupport = VectorizationProvider.getInstance().vectorTypeSupport

    class MapVectorValues(
        private val dimension: Int,
        private val vectors: Map<Int, VectorFloat<*>>
    ) : RandomAccessVectorValues {
        override fun size(): Int = vectors.size
        override fun dimension(): Int = dimension
        override fun copy(): RandomAccessVectorValues = this
        override fun getVector(id: Int): VectorFloat<*> = vectors[id] ?: throw IllegalArgumentException("Missing vector $id")
        override fun isValueShared(): Boolean = false
    }

    private fun floatArrayToVectorFloat(array: FloatArray): VectorFloat<*> {
        val vector = vectorTypeSupport.createFloatVector(array.size)
        for (i in array.indices) {
            vector.set(i, array[i])
        }
        return vector
    }

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                Log.d(TAG, "Initializing vector search for project $projectId...")

                val indexFile = getIndexFile(context, projectId)
                val metadataFile = getMetadataFile(context, projectId)

                if (metadataFile.exists()) {
                    loadIndex(indexFile, metadataFile)
                } else {
                    createNewIndex()
                }

                buildJVectorGraph()

                Log.d(TAG, "Vector search initialized: $vectorCount vectors loaded")
                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize vector search", e)
                Result.failure(e)
            }
        }
    }

    private fun buildJVectorGraph() {
        if (idToVector.isEmpty()) {
            jVectorIndex = null
            isIndexStale = false
            return
        }

        idToOrdinal.clear()
        ordinalToId.clear()

        val vectorsMap = mutableMapOf<Int, VectorFloat<*>>()
        var ordinal = 0
        idToVector.forEach { (id, vector) ->
            idToOrdinal[id] = ordinal
            ordinalToId[ordinal] = id
            vectorsMap[ordinal] = floatArrayToVectorFloat(vector)
            ordinal++
        }

        val vectorValues = MapVectorValues(dimensions, vectorsMap)
        val builder = GraphIndexBuilder(
            vectorValues,
            VectorSimilarityFunction.COSINE,
            16,
            100,
            1.2f,
            1.2f
        )

        jVectorIndex = builder.build(vectorValues)
        isIndexStale = false
        Log.d(TAG, "JVector Graph built with ${vectorsMap.size} vectors")
    }

    suspend fun addVector(id: String, vector: FloatArray): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                require(vector.size == dimensions) {
                    "Vector dimension mismatch: expected $dimensions, got ${vector.size}"
                }

                idToVector[id] = normalizeVector(vector)
                vectorCount = idToVector.size
                isIndexStale = true

                Log.d(TAG, "Vector added: $id (total: $vectorCount)")
                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to add vector", e)
                Result.failure(e)
            }
        }
    }

    suspend fun addVectorsBatch(vectors: Map<String, FloatArray>): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                Log.d(TAG, "Adding batch of ${vectors.size} vectors...")

                vectors.forEach { (id, vector) ->
                    require(vector.size == dimensions) {
                        "Vector dimension mismatch: expected $dimensions, got ${vector.size}"
                    }
                    idToVector[id] = normalizeVector(vector)
                }

                vectorCount = idToVector.size
                isIndexStale = true

                Log.d(TAG, "Batch added: ${vectors.size} vectors (total: $vectorCount)")
                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to add batch", e)
                Result.failure(e)
            }
        }
    }

    suspend fun search(
        queryVector: FloatArray,
        k: Int = 10,
        minSimilarity: Float = 0.0f
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                require(queryVector.size == dimensions) {
                    "Query vector dimension mismatch: expected $dimensions, got ${queryVector.size}"
                }

                if (vectorCount == 0) {
                    return@withContext Result.success(emptyList())
                }

                val startTime = System.currentTimeMillis()

                if (isIndexStale || jVectorIndex == null) {
                    buildJVectorGraph()
                }

                val normalized = normalizeVector(queryVector)

                val results = if (jVectorIndex != null) {
                    jvectorSearch(normalized, k, minSimilarity)
                } else {
                    linearSearch(normalized, k, minSimilarity)
                }

                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "Search completed: ${results.size} results in ${duration}ms via JVector")

                Result.success(results)

            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                Result.failure(e)
            }
        }
    }

    private fun jvectorSearch(
        queryVector: FloatArray,
        k: Int,
        minSimilarity: Float
    ): List<SearchResult> {
        val currentIndex = jVectorIndex ?: return linearSearch(queryVector, k, minSimilarity)

        val vectorsMap = mutableMapOf<Int, VectorFloat<*>>()
        idToVector.forEach { (id, vector) ->
            val ord = idToOrdinal[id] ?: return@forEach
            vectorsMap[ord] = floatArrayToVectorFloat(vector)
        }
        val vectorValues = MapVectorValues(dimensions, vectorsMap)

        val qVec = floatArrayToVectorFloat(queryVector)

        val result = GraphSearcher.search(
            qVec,
            k,
            vectorValues,
            VectorSimilarityFunction.COSINE,
            currentIndex,
            null
        )

        val hits = result.nodes.mapNotNull { node ->
            val id = ordinalToId[node.node] ?: return@mapNotNull null
            if (node.score >= minSimilarity) {
                SearchResult(id, node.score)
            } else null
        }

        return hits.sortedByDescending { it.similarity }
    }

    suspend fun removeVector(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (idToVector.remove(id) != null) {
                    vectorCount = idToVector.size
                    isIndexStale = true
                    Log.d(TAG, "Vector removed: $id (remaining: $vectorCount)")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove vector", e)
                Result.failure(e)
            }
        }
    }

    suspend fun saveIndex(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val metadataFile = getMetadataFile(context, projectId)
                ObjectOutputStream(FileOutputStream(metadataFile)).use { oos ->
                    oos.writeObject(idToVector)
                }

                Log.d(TAG, "Index saved: $vectorCount vectors")
                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save index", e)
                Result.failure(e)
            }
        }
    }

    suspend fun clear(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                idToVector.clear()
                vectorCount = 0
                isIndexStale = true
                jVectorIndex = null

                val indexFile = getIndexFile(context, projectId)
                val metadataFile = getMetadataFile(context, projectId)
                indexFile.delete()
                metadataFile.delete()

                Log.d(TAG, "Index cleared")
                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear index", e)
                Result.failure(e)
            }
        }
    }

    private fun createNewIndex() {
        idToVector.clear()
        vectorCount = 0
        isIndexStale = true
        Log.d(TAG, "Created new empty index")
    }

    private fun loadIndex(indexFile: File, metadataFile: File) {
        try {
            ObjectInputStream(FileInputStream(metadataFile)).use { ois ->
                @Suppress("UNCHECKED_CAST")
                idToVector = ois.readObject() as MutableMap<String, FloatArray>
            }
            vectorCount = idToVector.size
            isIndexStale = true
            Log.d(TAG, "Index loaded: $vectorCount vectors")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load index, creating new", e)
            createNewIndex()
        }
    }

    private fun linearSearch(
        queryVector: FloatArray,
        k: Int,
        minSimilarity: Float
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        idToVector.forEach { (id, vector) ->
            val similarity = cosineSimilarity(queryVector, vector)
            if (similarity >= minSimilarity) {
                results.add(SearchResult(id, similarity))
            }
        }
        return results.sortedByDescending { it.similarity }.take(k)
    }

    private fun normalizeVector(vector: FloatArray): FloatArray {
        var norm = 0f
        for (value in vector) {
            norm += value * value
        }
        norm = kotlin.math.sqrt(norm)

        return if (norm > 0f) {
            FloatArray(vector.size) { i -> vector[i] / norm }
        } else {
            vector
        }
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        require(v1.size == v2.size) { "Vectors must have same dimensions" }

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }

        return if (norm1 > 0f && norm2 > 0f) {
            dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))
        } else {
            0f
        }
    }

    fun getStats(): VectorSearchStats {
        return VectorSearchStats(
            vectorCount = vectorCount,
            dimensions = dimensions,
            projectId = projectId,
            hasIndex = vectorCount > 0
        )
    }
}

data class SearchResult(
    val id: String,
    val similarity: Float
)

data class VectorSearchStats(
    val vectorCount: Int,
    val dimensions: Int,
    val projectId: String,
    val hasIndex: Boolean
)
