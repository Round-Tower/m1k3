package app.m1k3.ai.domain.embedding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for EmbeddingTaskType enum.
 *
 * Verifies that all expected task types exist and are properly defined.
 */
class EmbeddingTaskTypeTest {

    @Test
    fun `contains all expected task types`() {
        val expectedTypes = setOf(
            "RETRIEVAL",
            "QUERY",
            "CLASSIFICATION",
            "CLUSTERING",
            "DOCUMENT",
            "CODE"
        )

        val actualTypes = EmbeddingTaskType.entries.map { it.name }.toSet()

        assertEquals(expectedTypes, actualTypes)
    }

    @Test
    fun `has exactly 6 task types`() {
        assertEquals(6, EmbeddingTaskType.entries.size)
    }

    @Test
    fun `RETRIEVAL is first entry by convention`() {
        assertEquals(EmbeddingTaskType.RETRIEVAL, EmbeddingTaskType.entries.first())
    }

    @Test
    fun `valueOf returns correct enum for valid names`() {
        assertEquals(EmbeddingTaskType.RETRIEVAL, EmbeddingTaskType.valueOf("RETRIEVAL"))
        assertEquals(EmbeddingTaskType.QUERY, EmbeddingTaskType.valueOf("QUERY"))
        assertEquals(EmbeddingTaskType.CODE, EmbeddingTaskType.valueOf("CODE"))
    }

    @Test
    fun `entries list is not empty`() {
        assertTrue(EmbeddingTaskType.entries.isNotEmpty())
    }
}
