package app.m1k3.ai.assistant.passages

import app.m1k3.ai.domain.passages.services.VectorIndex
import io.github.jbellis.jvector.graph.GraphIndexBuilder
import io.github.jbellis.jvector.graph.GraphSearcher
import io.github.jbellis.jvector.vector.VectorSimilarityFunction
import io.github.jbellis.jvector.vector.types.VectorFloat
import io.github.jbellis.jvector.graph.SearchResult
import io.github.jbellis.jvector.graph.OnHeapGraphIndex
import io.github.jbellis.jvector.graph.RandomAccessVectorValues
import io.github.jbellis.jvector.vector.VectorizationProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class JVectorIndex : VectorIndex {
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, VectorIndex.Entry>()
    private var isBuilt = false
    private var index: OnHeapGraphIndex? = null
    private var searcher: GraphSearcher? = null
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

    override suspend fun add(
        id: String,
        vector: FloatArray,
        modelId: String,
    ) {
        mutex.withLock {
            entries[id] = VectorIndex.Entry(id, vector, modelId)
            isBuilt = false // Invalidate index
        }
    }

    override suspend fun remove(id: String) {
        mutex.withLock {
            if (entries.remove(id) != null) {
                isBuilt = false // Invalidate index
            }
        }
    }

    override suspend fun search(
        query: FloatArray,
        modelId: String,
        k: Int,
    ): List<VectorIndex.SearchHit> {
        if (k <= 0 || query.isEmpty()) return emptyList()

        mutex.withLock {
            if (!isBuilt) {
                buildIndex(modelId)
            }
            
            val currentIndex = index ?: return emptyList()
            val currentSearcher = searcher ?: return emptyList()
            
            val queryVector = floatArrayToVectorFloat(query)
            
            // JVector 3.0 search API might take (queryVector, ep, k, visitLimit) or similar
            // SearchResult search(GraphIndex index, VectorFloat<?> queryVector, int topK)
            // Wait, GraphSearcher has a static or instance method? Let's try instance searcher.search(queryVector, ep, topK)
            // Actually, GraphSearcher.search(queryVector, topK, vectorValues, similarityFunction) or similar.
            // Let's assume it has a method searcher.search(queryVector, topK, ...)
            // Or GraphSearcher.search(queryVector, ep, topK, ...)
            
            // Wait! In JVector 3.0 GraphSearcher has search(GraphIndex.View, VectorFloat, ...)
            // Let me look up the exact search signature using the compiler error.
            
            // I'll put a dummy call that compiles, then fix.
            // Or just `GraphSearcher.search` static method? 
            // `GraphSearcher.search(queryVector, k, validEntries.size, currentIndex, ...)`
            
            val result = GraphSearcher.search(
                queryVector,
                k,
                vectorsMapForSearch,
                VectorSimilarityFunction.COSINE,
                currentIndex,
                null
            )

            val hits = result.nodes.map { node ->
                VectorIndex.SearchHit(
                    id = ordinalToId[node.node] ?: "",
                    similarity = node.score
                )
            }.filter { it.id.isNotEmpty() }
            
            return hits
        }
    }
    
    private var vectorsMapForSearch: MapVectorValues? = null

    private fun buildIndex(modelId: String) {
        val validEntries = entries.values.filter { it.modelId == modelId }.toList()
        if (validEntries.isEmpty()) {
            index = null
            searcher = null
            vectorsMapForSearch = null
            return
        }

        idToOrdinal.clear()
        ordinalToId.clear()
        
        val dimension = validEntries.first().vector.size
        val vectorsMap = mutableMapOf<Int, VectorFloat<*>>()

        validEntries.forEachIndexed { i, entry ->
            idToOrdinal[entry.id] = i
            ordinalToId[i] = entry.id
            vectorsMap[i] = floatArrayToVectorFloat(entry.vector)
        }

        val vectorValues = MapVectorValues(dimension, vectorsMap)
        vectorsMapForSearch = vectorValues
        
        // M=16, beamWidth=100
        val builder = GraphIndexBuilder(
            vectorValues,
            VectorSimilarityFunction.COSINE,
            16,
            100,
            1.2f,
            1.2f
        )
        
        index = builder.build(vectorValues)
        // searcher = GraphSearcher(index)
        isBuilt = true
    }

    override suspend fun rebuild(entries: List<VectorIndex.Entry>) {
        mutex.withLock {
            this.entries.clear()
            entries.forEach { entry ->
                this.entries[entry.id] = entry
            }
            isBuilt = false
        }
    }

    override suspend fun size(): Int = mutex.withLock { entries.size }

    override suspend fun clear() {
        mutex.withLock {
            entries.clear()
            isBuilt = false
            index = null
            searcher = null
            vectorsMapForSearch = null
        }
    }
}
