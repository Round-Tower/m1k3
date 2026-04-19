package app.m1k3.ai.domain.passages.services

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * LinearScanVectorIndex — in-memory [VectorIndex] that scores every candidate
 * against the query with cosine similarity.
 *
 * Baseline implementation. Exactly the math the previous in-line code did,
 * but with two meaningful wins:
 *
 * 1. **Vectors stay deserialized in memory.** The BLOB→FloatArray round-trip
 *    happens once per passage (at [add] / [rebuild] time), not per search.
 *    At ~5000 passages × 384 dims × 4 bytes that's ~7.5 MB resident — cheap.
 * 2. **No DB I/O per search.** Previously `searchPassages` pulled every
 *    embedded row — including `content` TEXT — on every query. Now the
 *    index returns ranked IDs and the repository pulls just the top-K rows.
 *
 * Complexity: `search` is O(N × d) per call. Fine at N ≲ 10k; a follow-up
 * HNSW or sqlite-vec backend takes over beyond that.
 *
 * Thread safety: all public methods are guarded by a [Mutex]. This is cheap
 * because the index is local to a single repository instance and contention
 * is light (one writer during ingest, one reader during chat).
 */
class LinearScanVectorIndex : VectorIndex {
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, VectorIndex.Entry>()

    override suspend fun add(
        id: String,
        vector: FloatArray,
        modelId: String,
    ) {
        mutex.withLock {
            entries[id] = VectorIndex.Entry(id, vector, modelId)
        }
    }

    override suspend fun remove(id: String) {
        mutex.withLock {
            entries.remove(id)
        }
    }

    override suspend fun search(
        query: FloatArray,
        modelId: String,
        k: Int,
    ): List<VectorIndex.SearchHit> {
        if (k <= 0 || query.isEmpty()) return emptyList()

        // Snapshot under the lock so we don't hold it during math. The snapshot
        // is a shallow copy of the Map's values; Entry instances themselves are
        // effectively immutable (FloatArray content isn't mutated after add).
        val snapshot: List<VectorIndex.Entry> =
            mutex.withLock {
                entries.values.filter { it.modelId == modelId }.toList()
            }

        if (snapshot.isEmpty()) return emptyList()

        val scored = ArrayList<VectorIndex.SearchHit>(snapshot.size)
        for (entry in snapshot) {
            val score = cosineSimilarity(query, entry.vector)
            if (score > 0f) {
                scored.add(VectorIndex.SearchHit(entry.id, score))
            }
        }
        return scored.sortedByDescending { it.similarity }.take(k)
    }

    override suspend fun rebuild(entries: List<VectorIndex.Entry>) {
        mutex.withLock {
            this.entries.clear()
            entries.forEach { entry ->
                this.entries[entry.id] = entry
            }
        }
    }

    override suspend fun size(): Int = mutex.withLock { entries.size }

    override suspend fun clear() {
        mutex.withLock {
            entries.clear()
        }
    }
}
