package app.m1k3.ai.domain.passages.services

/**
 * PassageEmbedder — produces dense vector embeddings for passage content
 * and user queries.
 *
 * Domain interface. Pure Kotlin.
 *
 * Implementations wrap a platform-specific embedding engine (e.g. ONNX
 * MiniLM / EmbeddingGemma on Android). Returning null is legal and means
 * "unavailable" — the repository falls back to keyword search in that case.
 *
 * @property modelId Stable identifier persisted alongside embeddings
 *                   (e.g. `"minilm-l6-v2"`), so a future model swap can
 *                   invalidate or re-embed rows.
 * @property dimension Expected length of the returned FloatArray.
 */
interface PassageEmbedder {
    val modelId: String
    val dimension: Int

    /**
     * Embed [text] into a [dimension]-length vector.
     *
     * @return The embedding, or null if the engine failed to produce one
     *         (model not loaded, transient error, OOM, etc). Callers must
     *         tolerate null and degrade to keyword-only retrieval.
     */
    suspend fun embed(text: String): FloatArray?
}

/**
 * Cosine similarity between two vectors.
 *
 * Returns 0.0 when either vector is empty, lengths differ, or either has
 * zero magnitude — callers treat these as "no match" rather than NaN.
 */
fun cosineSimilarity(
    a: FloatArray,
    b: FloatArray,
): Float {
    if (a.isEmpty() || a.size != b.size) return 0f
    var dot = 0f
    var magA = 0f
    var magB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        magA += a[i] * a[i]
        magB += b[i] * b[i]
    }
    if (magA == 0f || magB == 0f) return 0f
    return dot / (kotlin.math.sqrt(magA) * kotlin.math.sqrt(magB))
}
