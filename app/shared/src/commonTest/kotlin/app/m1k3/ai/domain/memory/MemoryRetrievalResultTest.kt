package app.m1k3.ai.domain.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for MemoryRetrievalResult.
 */
class MemoryRetrievalResultTest {

    private fun createMemory(
        id: String,
        content: String,
        similarity: Float,
        messageId: String = "msg_1",
        chunkIndex: Int = 0
    ) = MemorySearchResult(
        id = id,
        content = content,
        importance = 0.5f,
        similarity = similarity,
        chunkIndex = chunkIndex,
        chunkTotal = 1,
        messageId = messageId,
        createdAt = 1000L
    )

    @Test
    fun `hasMemories returns true when memories exist`() {
        val result = MemoryRetrievalResult(
            memories = listOf(createMemory("m1", "test", 0.9f)),
            totalTokens = 10,
            droppedCount = 0
        )

        assertTrue(result.hasMemories)
    }

    @Test
    fun `hasMemories returns false when empty`() {
        val result = MemoryRetrievalResult.empty()

        assertFalse(result.hasMemories)
    }

    @Test
    fun `memoryCount returns correct count`() {
        val result = MemoryRetrievalResult(
            memories = listOf(
                createMemory("m1", "first", 0.9f),
                createMemory("m2", "second", 0.8f),
                createMemory("m3", "third", 0.7f)
            ),
            totalTokens = 30,
            droppedCount = 0
        )

        assertEquals(3, result.memoryCount)
    }

    @Test
    fun `getOrderedByChunks sorts by messageId then chunkIndex`() {
        val result = MemoryRetrievalResult(
            memories = listOf(
                createMemory("m1", "msg2-chunk1", 0.9f, messageId = "msg_2", chunkIndex = 1),
                createMemory("m2", "msg1-chunk0", 0.8f, messageId = "msg_1", chunkIndex = 0),
                createMemory("m3", "msg2-chunk0", 0.7f, messageId = "msg_2", chunkIndex = 0),
                createMemory("m4", "msg1-chunk1", 0.6f, messageId = "msg_1", chunkIndex = 1)
            ),
            totalTokens = 40,
            droppedCount = 0
        )

        val ordered = result.getOrderedByChunks()

        assertEquals("msg_1", ordered[0].messageId)
        assertEquals(0, ordered[0].chunkIndex)
        assertEquals("msg_1", ordered[1].messageId)
        assertEquals(1, ordered[1].chunkIndex)
        assertEquals("msg_2", ordered[2].messageId)
        assertEquals(0, ordered[2].chunkIndex)
        assertEquals("msg_2", ordered[3].messageId)
        assertEquals(1, ordered[3].chunkIndex)
    }

    @Test
    fun `getOrderedBySimilarity sorts by similarity descending`() {
        val result = MemoryRetrievalResult(
            memories = listOf(
                createMemory("m1", "low", 0.5f),
                createMemory("m2", "high", 0.9f),
                createMemory("m3", "medium", 0.7f)
            ),
            totalTokens = 30,
            droppedCount = 0
        )

        val ordered = result.getOrderedBySimilarity()

        assertEquals(0.9f, ordered[0].similarity)
        assertEquals(0.7f, ordered[1].similarity)
        assertEquals(0.5f, ordered[2].similarity)
    }

    @Test
    fun `formatAsContext produces expected format`() {
        val result = MemoryRetrievalResult(
            memories = listOf(
                createMemory("m1", "First memory", 0.9f, chunkIndex = 0),
                createMemory("m2", "Second memory", 0.8f, chunkIndex = 1)
            ),
            totalTokens = 20,
            droppedCount = 0
        )

        val context = result.formatAsContext()

        assertTrue(context.contains("Relevant memories:"))
        assertTrue(context.contains("First memory"))
        assertTrue(context.contains("Second memory"))
    }

    @Test
    fun `formatAsContext returns empty string for no memories`() {
        val result = MemoryRetrievalResult.empty()

        val context = result.formatAsContext()

        assertEquals("", context)
    }

    @Test
    fun `empty factory creates correct empty result`() {
        val result = MemoryRetrievalResult.empty()

        assertEquals(0, result.memoryCount)
        assertEquals(0, result.totalTokens)
        assertEquals(0, result.droppedCount)
        assertFalse(result.hasMemories)
    }

    @Test
    fun `droppedCount is preserved`() {
        val result = MemoryRetrievalResult(
            memories = listOf(createMemory("m1", "test", 0.9f)),
            totalTokens = 10,
            droppedCount = 5
        )

        assertEquals(5, result.droppedCount)
    }
}
