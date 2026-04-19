package app.m1k3.ai.domain.passages.services

/**
 * VectorIndex — abstract top-K similarity search over passage embeddings.
 *
 * Domain interface. Pure Kotlin, no platform dependencies.
 *
 * The `SqlDelightPassageRepository` keeps embeddings in the `Passage` table
 * (as BLOBs), but an index layered on top lets search skip the
 * pull-all-rows-every-time cost. Implementations are interchangeable:
 *
 * - [LinearScanVectorIndex] — in-memory map, exhaustive cosine scan. Baseline;
 *   the same math as the previous in-line implementation, but with vectors
 *   cached after one rebuild so subsequent searches don't re-read the DB.
 * - `HnswVectorIndex` (future) — approximate, sub-linear, pure Kotlin.
 * - `SqliteVecIndex` (future) — native sqlite-vec extension, scales to 100k+.
 *
 * All variants honour `modelId`: an index row is only considered for a search
 * whose query embedding came from the same model. A single index can hold
 * rows from multiple models (e.g. during an embedding-model migration), but
 * never mixes their similarity scores — mismatched rows are filtered out.
 *
 * Thread safety: implementations must tolerate concurrent [add] / [remove] /
 * [search] calls from the coroutine ioDispatcher. All operations are
 * `suspend` to keep the interface implementation-agnostic — an HNSW backend
 * might do a write lock, a sqlite-vec backend might dispatch a SQL statement.
 */
interface VectorIndex {
    /**
     * Insert or replace a vector.
     *
     * @param id Stable passage ID (primary key in the Passage table).
     * @param vector Embedding. Length must equal the embedder's dimension.
     * @param modelId Model that produced [vector]. Recorded alongside the
     *   vector so queries can filter for matching-model rows only.
     */
    suspend fun add(
        id: String,
        vector: FloatArray,
        modelId: String,
    )

    /**
     * Remove a vector. No-op if no vector with [id] is present.
     */
    suspend fun remove(id: String)

    /**
     * Top-K similarity search.
     *
     * @param query The query embedding.
     * @param modelId Only rows added under this model are considered.
     * @param k Max results to return. Callers should pass a small number;
     *   implementations may cap for safety.
     * @return Ranked list of (id, similarity), highest similarity first.
     *   Empty list if no rows match the model or no vectors pass the
     *   implementation's minimum-similarity threshold (if any).
     */
    suspend fun search(
        query: FloatArray,
        modelId: String,
        k: Int,
    ): List<SearchHit>

    /**
     * Replace the index contents with [entries].
     *
     * Used to (re-)hydrate the index from a persistent backing store (e.g.
     * on first search after app launch) and after bulk operations that
     * would otherwise require many [add] / [remove] calls.
     */
    suspend fun rebuild(entries: List<Entry>)

    /**
     * Number of vectors currently in the index across all models.
     * Primarily for diagnostics / perf tests.
     */
    suspend fun size(): Int

    /** Remove all vectors. */
    suspend fun clear()

    /** Persisted triple: vector + metadata. */
    data class Entry(
        val id: String,
        val vector: FloatArray,
        val modelId: String,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Entry) return false
            if (id != other.id) return false
            if (!vector.contentEquals(other.vector)) return false
            if (modelId != other.modelId) return false
            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + vector.contentHashCode()
            result = 31 * result + modelId.hashCode()
            return result
        }
    }

    /** Ranked search result. */
    data class SearchHit(
        val id: String,
        val similarity: Float,
    )
}
