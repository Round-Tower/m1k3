package app.m1k3.ai.domain.rag.services

/**
 * iOS implementation of EmbeddingSerializer using pure Kotlin.
 *
 * Manual byte conversion since java.nio is not available on iOS.
 * Uses little-endian format for consistency with Android.
 */
actual object EmbeddingSerializer {

    /**
     * Serialize FloatArray to ByteArray using manual bit conversion.
     *
     * Format: IEEE 754 single-precision, little-endian (4 bytes per float)
     */
    actual fun serialize(embedding: FloatArray): ByteArray {
        val bytes = ByteArray(embedding.size * 4)
        var offset = 0

        for (value in embedding) {
            val bits = value.toRawBits()
            // Little-endian: LSB first
            bytes[offset++] = (bits and 0xFF).toByte()
            bytes[offset++] = ((bits shr 8) and 0xFF).toByte()
            bytes[offset++] = ((bits shr 16) and 0xFF).toByte()
            bytes[offset++] = ((bits shr 24) and 0xFF).toByte()
        }

        return bytes
    }

    /**
     * Deserialize ByteArray to FloatArray using manual bit conversion.
     *
     * @throws IllegalArgumentException if byte array size is not divisible by 4
     */
    actual fun deserialize(bytes: ByteArray): FloatArray {
        if (bytes.isEmpty()) return floatArrayOf()
        require(bytes.size % 4 == 0) {
            "Byte array size must be divisible by 4 (got ${bytes.size})"
        }

        val embedding = FloatArray(bytes.size / 4)
        var offset = 0

        for (i in embedding.indices) {
            // Little-endian: read LSB first
            val bits = (bytes[offset++].toInt() and 0xFF) or
                    ((bytes[offset++].toInt() and 0xFF) shl 8) or
                    ((bytes[offset++].toInt() and 0xFF) shl 16) or
                    ((bytes[offset++].toInt() and 0xFF) shl 24)
            embedding[i] = Float.fromBits(bits)
        }

        return embedding
    }
}
