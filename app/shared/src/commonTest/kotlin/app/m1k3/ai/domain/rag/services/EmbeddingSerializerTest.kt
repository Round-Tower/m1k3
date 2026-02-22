package app.m1k3.ai.domain.rag.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EmbeddingSerializer Tests
 *
 * Tests for cross-platform embedding serialization/deserialization.
 * Ensures consistent byte format across Android and iOS platforms.
 *
 * Format: IEEE 754 single-precision floats, little-endian
 * 384 dimensions * 4 bytes = 1,536 bytes per embedding
 */
class EmbeddingSerializerTest {

    // ========================================
    // Round-trip Tests
    // ========================================

    @Test
    fun `serializes and deserializes simple embedding`() {
        val original = floatArrayOf(1.0f, 2.0f, 3.0f)

        val bytes = EmbeddingSerializer.serialize(original)
        val restored = EmbeddingSerializer.deserialize(bytes)

        assertEquals(original.size, restored.size)
        original.indices.forEach { i ->
            assertEquals(original[i], restored[i], 0.0001f)
        }
    }

    @Test
    fun `serializes and deserializes 384-dimensional embedding`() {
        // Typical MiniLM embedding size
        val original = FloatArray(384) { it.toFloat() / 384f }

        val bytes = EmbeddingSerializer.serialize(original)
        val restored = EmbeddingSerializer.deserialize(bytes)

        assertEquals(384, restored.size)
        original.indices.forEach { i ->
            assertEquals(original[i], restored[i], 0.0001f)
        }
    }

    @Test
    fun `handles negative values`() {
        val original = floatArrayOf(-1.0f, -0.5f, 0.0f, 0.5f, 1.0f)

        val bytes = EmbeddingSerializer.serialize(original)
        val restored = EmbeddingSerializer.deserialize(bytes)

        original.indices.forEach { i ->
            assertEquals(original[i], restored[i], 0.0001f)
        }
    }

    @Test
    fun `handles special float values`() {
        val original = floatArrayOf(
            Float.MIN_VALUE,
            Float.MAX_VALUE,
            0.0f,
            -0.0f
        )

        val bytes = EmbeddingSerializer.serialize(original)
        val restored = EmbeddingSerializer.deserialize(bytes)

        assertEquals(Float.MIN_VALUE, restored[0])
        assertEquals(Float.MAX_VALUE, restored[1])
        assertEquals(0.0f, restored[2])
    }

    // ========================================
    // Byte Format Tests
    // ========================================

    @Test
    fun `produces correct byte size`() {
        val embedding = FloatArray(384) { 0.0f }
        val bytes = EmbeddingSerializer.serialize(embedding)

        // 384 floats * 4 bytes = 1536 bytes
        assertEquals(1536, bytes.size)
    }

    @Test
    fun `produces 4 bytes per float`() {
        val embedding = floatArrayOf(1.0f)
        val bytes = EmbeddingSerializer.serialize(embedding)

        assertEquals(4, bytes.size)
    }

    // ========================================
    // Edge Cases
    // ========================================

    @Test
    fun `handles empty embedding`() {
        val original = floatArrayOf()

        val bytes = EmbeddingSerializer.serialize(original)
        val restored = EmbeddingSerializer.deserialize(bytes)

        assertEquals(0, bytes.size)
        assertEquals(0, restored.size)
    }

    @Test
    fun `handles single element`() {
        val original = floatArrayOf(42.5f)

        val bytes = EmbeddingSerializer.serialize(original)
        val restored = EmbeddingSerializer.deserialize(bytes)

        assertEquals(1, restored.size)
        assertEquals(42.5f, restored[0], 0.0001f)
    }

    @Test
    fun `preserves precision for distinguishable floats`() {
        // Values with sufficient difference for float precision (~7 sig digits)
        val original = floatArrayOf(0.1234567f, 0.1234568f)

        val bytes = EmbeddingSerializer.serialize(original)
        val restored = EmbeddingSerializer.deserialize(bytes)

        // These values differ at the 7th decimal place, within float precision
        assertTrue(restored[0] != restored[1], "Expected distinct values after round-trip")
        assertEquals(original[0], restored[0], 1e-7f)
        assertEquals(original[1], restored[1], 1e-7f)
    }

    // ========================================
    // Special Float Values (Edge Cases)
    // ========================================

    @Test
    fun `handles NaN value`() {
        val original = floatArrayOf(Float.NaN, 1.0f)

        val bytes = EmbeddingSerializer.serialize(original)
        val restored = EmbeddingSerializer.deserialize(bytes)

        assertTrue(restored[0].isNaN(), "Expected NaN to be preserved")
        assertEquals(1.0f, restored[1])
    }

    @Test
    fun `handles positive infinity`() {
        val original = floatArrayOf(Float.POSITIVE_INFINITY)

        val bytes = EmbeddingSerializer.serialize(original)
        val restored = EmbeddingSerializer.deserialize(bytes)

        assertEquals(Float.POSITIVE_INFINITY, restored[0])
    }

    @Test
    fun `handles negative infinity`() {
        val original = floatArrayOf(Float.NEGATIVE_INFINITY)

        val bytes = EmbeddingSerializer.serialize(original)
        val restored = EmbeddingSerializer.deserialize(bytes)

        assertEquals(Float.NEGATIVE_INFINITY, restored[0])
    }

    // ========================================
    // Invalid Input Handling
    // ========================================

    @Test
    fun `throws on misaligned byte array`() {
        // 5 bytes - not divisible by 4
        val misaligned = ByteArray(5) { it.toByte() }

        try {
            EmbeddingSerializer.deserialize(misaligned)
            assertTrue(false, "Expected IllegalArgumentException for misaligned bytes")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("divisible by 4") == true)
        }
    }

    @Test
    fun `throws on odd-sized byte array`() {
        // 7 bytes - odd number, definitely not aligned
        val oddSize = ByteArray(7)

        try {
            EmbeddingSerializer.deserialize(oddSize)
            assertTrue(false, "Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }
}
