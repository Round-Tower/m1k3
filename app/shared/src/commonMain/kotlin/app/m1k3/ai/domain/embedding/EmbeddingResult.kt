package app.m1k3.ai.domain.embedding

/**
 * Embedding Result - Text embedding with metadata.
 *
 * Contains the embedding vector along with the source text and
 * performance metrics for the embedding operation.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Note: Custom equals/hashCode implementation for proper FloatArray comparison.
 *
 * @property embedding The embedding vector (normalized)
 * @property text The source text that was embedded
 * @property dimensions The dimensionality of the embedding (e.g., 384 for Gemma)
 * @property inferenceTimeMs Time taken to generate the embedding in milliseconds
 */
data class EmbeddingResult(
    val embedding: FloatArray,
    val text: String,
    val dimensions: Int,
    val inferenceTimeMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as EmbeddingResult

        if (!embedding.contentEquals(other.embedding)) return false
        if (text != other.text) return false
        if (dimensions != other.dimensions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = embedding.contentHashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + dimensions
        return result
    }
}
