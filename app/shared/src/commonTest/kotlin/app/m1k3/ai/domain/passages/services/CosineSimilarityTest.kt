package app.m1k3.ai.domain.passages.services

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the pure-Kotlin [cosineSimilarity] helper.
 */
class CosineSimilarityTest {
    private fun close(
        expected: Float,
        actual: Float,
        tol: Float = 1e-5f,
    ): Boolean = abs(expected - actual) < tol

    @Test
    fun `identical vectors have similarity 1`() {
        val v = floatArrayOf(1f, 2f, 3f, 4f)
        assertTrue(close(1f, cosineSimilarity(v, v)))
    }

    @Test
    fun `orthogonal vectors have similarity 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, cosineSimilarity(a, b))
    }

    @Test
    fun `opposite vectors have similarity minus 1`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(-1f, -2f, -3f)
        assertTrue(close(-1f, cosineSimilarity(a, b)))
    }

    @Test
    fun `empty vectors return zero`() {
        assertEquals(0f, cosineSimilarity(floatArrayOf(), floatArrayOf()))
    }

    @Test
    fun `mismatched lengths return zero`() {
        assertEquals(0f, cosineSimilarity(floatArrayOf(1f, 2f), floatArrayOf(1f)))
    }

    @Test
    fun `zero magnitude vectors return zero`() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(1f, 2f, 3f)
        assertEquals(0f, cosineSimilarity(a, b))
        assertEquals(0f, cosineSimilarity(b, a))
    }

    @Test
    fun `similarity is symmetric`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(4f, 5f, 6f)
        assertTrue(close(cosineSimilarity(a, b), cosineSimilarity(b, a)))
    }
}
