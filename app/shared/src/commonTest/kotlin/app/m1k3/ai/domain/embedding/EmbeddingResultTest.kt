package app.m1k3.ai.domain.embedding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for EmbeddingResult data class.
 *
 * Verifies correct behavior of the data class including
 * custom equals/hashCode for FloatArray comparison.
 */
class EmbeddingResultTest {

    @Test
    fun `constructor creates result with correct properties`() {
        val embedding = floatArrayOf(0.1f, 0.2f, 0.3f)
        val result = EmbeddingResult(
            embedding = embedding,
            text = "test text",
            dimensions = 3,
            inferenceTimeMs = 100L
        )

        assertTrue(embedding.contentEquals(result.embedding))
        assertEquals("test text", result.text)
        assertEquals(3, result.dimensions)
        assertEquals(100L, result.inferenceTimeMs)
    }

    @Test
    fun `equals compares embeddings by content`() {
        val result1 = EmbeddingResult(
            embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
            text = "test",
            dimensions = 3,
            inferenceTimeMs = 100L
        )
        val result2 = EmbeddingResult(
            embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
            text = "test",
            dimensions = 3,
            inferenceTimeMs = 100L
        )

        assertEquals(result1, result2)
    }

    @Test
    fun `equals returns false for different embeddings`() {
        val result1 = EmbeddingResult(
            embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
            text = "test",
            dimensions = 3,
            inferenceTimeMs = 100L
        )
        val result2 = EmbeddingResult(
            embedding = floatArrayOf(0.4f, 0.5f, 0.6f),
            text = "test",
            dimensions = 3,
            inferenceTimeMs = 100L
        )

        assertNotEquals(result1, result2)
    }

    @Test
    fun `equals returns false for different text`() {
        val result1 = EmbeddingResult(
            embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
            text = "test1",
            dimensions = 3,
            inferenceTimeMs = 100L
        )
        val result2 = EmbeddingResult(
            embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
            text = "test2",
            dimensions = 3,
            inferenceTimeMs = 100L
        )

        assertNotEquals(result1, result2)
    }

    @Test
    fun `hashCode is consistent with equals`() {
        val result1 = EmbeddingResult(
            embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
            text = "test",
            dimensions = 3,
            inferenceTimeMs = 100L
        )
        val result2 = EmbeddingResult(
            embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
            text = "test",
            dimensions = 3,
            inferenceTimeMs = 100L
        )

        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    fun `copy creates new instance with modified properties`() {
        val original = EmbeddingResult(
            embedding = floatArrayOf(0.1f, 0.2f, 0.3f),
            text = "original",
            dimensions = 3,
            inferenceTimeMs = 100L
        )
        val copied = original.copy(text = "copied")

        assertEquals("copied", copied.text)
        assertTrue(original.embedding.contentEquals(copied.embedding))
    }

    @Test
    fun `empty embedding is valid`() {
        val result = EmbeddingResult(
            embedding = floatArrayOf(),
            text = "",
            dimensions = 0,
            inferenceTimeMs = 0L
        )

        assertEquals(0, result.dimensions)
        assertTrue(result.embedding.isEmpty())
    }
}
