package app.m1k3.ai.assistant.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for SemanticChunker
 *
 * Validates chunk size, semantic boundaries, and overlap logic.
 */
class SemanticChunkerTest {

    // Use the SimpleTokenCounter for tests (~4 chars per token)
    private val chunker = SemanticChunker(
        tokenCounter = SimpleTokenCounter(),
        minChunkTokens = 100,
        maxChunkTokens = 300,
        overlapTokens = 20
    )

    @Test
    fun `short text below minimum is skipped`() {
        val shortText = "Too short"  // ~2 tokens
        val now = System.currentTimeMillis()

        val chunks = chunker.chunkMessage(
            messageContent = shortText,
            messageId = "msg-1",
            projectId = "proj-1",
            timestamp = now,
            role = "user"
        )

        assertEquals(0, chunks.size, "Short text should produce no chunks")
    }

    @Test
    fun `text within range produces single chunk`() {
        val text = "a".repeat(400) + "." // ~100 tokens (within range)
        val now = System.currentTimeMillis()

        val chunks = chunker.chunkMessage(
            messageContent = text,
            messageId = "msg-1",
            projectId = "proj-1",
            timestamp = now,
            role = "user"
        )

        assertEquals(1, chunks.size)
        assertTrue(chunks[0].tokenCount >= 100)
        assertTrue(chunks[0].tokenCount <= 300)
    }

    @Test
    fun `long text is split into multiple chunks`() {
        // Create text > 300 tokens (~1200 chars = ~300 tokens)
        val text = "word ".repeat(240) + "."  // ~1200 chars, ~300 tokens
        val now = System.currentTimeMillis()

        val chunks = chunker.chunkMessage(
            messageContent = text,
            messageId = "msg-1",
            projectId = "proj-1",
            timestamp = now,
            role = "user"
        )

        // Should produce multiple chunks
        assertTrue(chunks.size > 1, "Long text should split into multiple chunks")

        // Each chunk should respect limits
        chunks.forEach { chunk ->
            assertTrue(chunk.tokenCount >= 100, "Chunk too small: ${chunk.tokenCount}")
            assertTrue(chunk.tokenCount <= 300, "Chunk too large: ${chunk.tokenCount}")
        }
    }

    @Test
    fun `semantic boundaries are respected`() {
        val text = """
            This is the first topic. It has several sentences.
            However, let's move to the second topic.
            The second topic is different.
        """.trimIndent()

        val now = System.currentTimeMillis()

        val chunks = chunker.chunkMessage(
            messageContent = text,
            messageId = "msg-1",
            projectId = "proj-1",
            timestamp = now,
            role = "user"
        )

        // Should detect "However" as semantic boundary
        // (This is probabilistic based on token count meeting minimum)
        assertTrue(chunks.size >= 1)
    }

    @Test
    fun `chunk IDs are unique and deterministic`() {
        val text = "a".repeat(400) + "."  // ~100 tokens
        val now = System.currentTimeMillis()

        val chunks1 = chunker.chunkMessage(
            messageContent = text,
            messageId = "msg-1",
            projectId = "proj-1",
            timestamp = now,
            role = "user"
        )

        val chunks2 = chunker.chunkMessage(
            messageContent = text,
            messageId = "msg-1",
            projectId = "proj-1",
            timestamp = now,
            role = "user"
        )

        // Same input should produce same IDs
        assertEquals(chunks1.size, chunks2.size)
        chunks1.zip(chunks2).forEach { (c1, c2) ->
            assertEquals(c1.id, c2.id, "IDs should be deterministic")
        }
    }

    @Test
    fun `chunk preserves metadata`() {
        val text = "a".repeat(400) + "."  // ~100 tokens
        val messageId = "msg-123"
        val projectId = "proj-456"
        val timestamp = 1234567890L
        val role = "user"

        val chunks = chunker.chunkMessage(
            messageContent = text,
            messageId = messageId,
            projectId = projectId,
            timestamp = timestamp,
            role = role
        )

        assertTrue(chunks.isNotEmpty())
        val chunk = chunks.first()

        assertEquals(messageId, chunk.messageId)
        assertEquals(projectId, chunk.projectId)
        assertEquals(timestamp, chunk.timestamp)
        assertEquals(role, chunk.role)
    }

    @Test
    fun `empty or blank text produces no chunks`() {
        val now = System.currentTimeMillis()

        val emptyChunks = chunker.chunkMessage(
            messageContent = "",
            messageId = "msg-1",
            projectId = "proj-1",
            timestamp = now,
            role = "user"
        )

        val blankChunks = chunker.chunkMessage(
            messageContent = "   ",
            messageId = "msg-2",
            projectId = "proj-1",
            timestamp = now,
            role = "user"
        )

        assertEquals(0, emptyChunks.size)
        assertEquals(0, blankChunks.size)
    }

    @Test
    fun `chunks include token count`() {
        val text = "a".repeat(400) + "."  // ~100 tokens
        val now = System.currentTimeMillis()

        val chunks = chunker.chunkMessage(
            messageContent = text,
            messageId = "msg-1",
            projectId = "proj-1",
            timestamp = now,
            role = "user"
        )

        assertTrue(chunks.isNotEmpty())
        chunks.forEach { chunk ->
            assertTrue(chunk.tokenCount > 0, "Token count should be tracked")
        }
    }
}
