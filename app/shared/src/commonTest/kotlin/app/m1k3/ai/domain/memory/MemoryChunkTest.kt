package app.m1k3.ai.domain.memory

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for MemoryChunk domain entity
 *
 * TDD: Phase 1 - Domain Value Objects
 */
class MemoryChunkTest {

    @Test
    fun `first chunk is identified correctly`() {
        val chunk = MemoryChunk(
            text = "First chunk content",
            tokenCount = 50,
            chunkIndex = 0,
            totalChunks = 3
        )

        assertTrue(chunk.isFirstChunk, "Chunk with index 0 should be first chunk")
        assertFalse(chunk.isLastChunk, "Chunk with index 0 should not be last chunk when total is 3")
    }

    @Test
    fun `last chunk is identified correctly`() {
        val chunk = MemoryChunk(
            text = "Last chunk content",
            tokenCount = 50,
            chunkIndex = 2,
            totalChunks = 3
        )

        assertFalse(chunk.isFirstChunk, "Chunk with index 2 should not be first chunk")
        assertTrue(chunk.isLastChunk, "Chunk with index 2 should be last chunk when total is 3")
    }

    @Test
    fun `middle chunk is neither first nor last`() {
        val chunk = MemoryChunk(
            text = "Middle chunk content",
            tokenCount = 50,
            chunkIndex = 1,
            totalChunks = 3
        )

        assertFalse(chunk.isFirstChunk, "Middle chunk should not be first")
        assertFalse(chunk.isLastChunk, "Middle chunk should not be last")
    }

    @Test
    fun `single chunk is both first and last`() {
        val chunk = MemoryChunk(
            text = "Only chunk",
            tokenCount = 100,
            chunkIndex = 0,
            totalChunks = 1
        )

        assertTrue(chunk.isFirstChunk, "Single chunk should be first")
        assertTrue(chunk.isLastChunk, "Single chunk should be last")
    }
}
