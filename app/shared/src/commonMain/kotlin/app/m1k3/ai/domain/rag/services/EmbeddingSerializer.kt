package app.m1k3.ai.domain.rag.services

/**
 * EmbeddingSerializer - Cross-platform embedding serialization
 *
 * Serializes/deserializes FloatArray embeddings to ByteArray for storage.
 * Uses IEEE 754 single-precision floats in little-endian format.
 *
 * **Why expect/actual:**
 * - Android: Uses java.nio.ByteBuffer for efficiency
 * - iOS: Uses pure Kotlin implementation (no JVM dependencies)
 *
 * **Format:**
 * - Each float: 4 bytes, little-endian
 * - 384 dimensions: 1,536 bytes total
 *
 * Domain service - expect/actual for platform-specific implementation.
 */
expect object EmbeddingSerializer {
    /**
     * Serialize FloatArray to ByteArray for BLOB storage.
     *
     * @param embedding The embedding vector
     * @return ByteArray in little-endian IEEE 754 format
     */
    fun serialize(embedding: FloatArray): ByteArray

    /**
     * Deserialize ByteArray from BLOB storage to FloatArray.
     *
     * @param bytes ByteArray in little-endian IEEE 754 format
     * @return The embedding vector
     */
    fun deserialize(bytes: ByteArray): FloatArray
}
