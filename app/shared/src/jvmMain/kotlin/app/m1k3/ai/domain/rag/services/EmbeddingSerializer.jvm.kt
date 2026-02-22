package app.m1k3.ai.domain.rag.services

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * JVM implementation of EmbeddingSerializer using java.nio.ByteBuffer.
 *
 * Identical to Android implementation - shares JVM runtime.
 */
actual object EmbeddingSerializer {

    /**
     * Serialize FloatArray to ByteArray using ByteBuffer.
     *
     * Format: IEEE 754 single-precision, little-endian
     */
    actual fun serialize(embedding: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(embedding.size * Float.SIZE_BYTES)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    /**
     * Deserialize ByteArray to FloatArray using ByteBuffer.
     *
     * @throws IllegalArgumentException if byte array size is not divisible by 4
     */
    actual fun deserialize(bytes: ByteArray): FloatArray {
        if (bytes.isEmpty()) return floatArrayOf()
        require(bytes.size % Float.SIZE_BYTES == 0) {
            "Byte array size must be divisible by 4 (got ${bytes.size})"
        }

        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val embedding = FloatArray(bytes.size / Float.SIZE_BYTES)
        for (i in embedding.indices) {
            embedding[i] = buffer.getFloat()
        }
        return embedding
    }
}
