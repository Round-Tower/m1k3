package app.m1k3.ai.domain.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for Memory domain entity.
 */
class MemoryTest {

    @Test
    fun `memory with default values is not pinned`() {
        val memory = Memory(
            id = "mem_1",
            messageId = "msg_1",
            projectId = "proj_1",
            content = "Test content",
            importance = 0.5f,
            createdAt = 1000L,
            chunkIndex = 0,
            chunkTotal = 1,
            embeddingId = "emb_1"
        )

        assertFalse(memory.isPinned)
        assertEquals(1.0f, memory.decayFactor)
        assertEquals(0, memory.accessCount)
    }

    @Test
    fun `effectiveImportance combines importance and decay`() {
        val memory = Memory(
            id = "mem_1",
            messageId = "msg_1",
            projectId = "proj_1",
            content = "Test content",
            importance = 0.8f,
            createdAt = 1000L,
            chunkIndex = 0,
            chunkTotal = 1,
            embeddingId = "emb_1",
            decayFactor = 0.5f
        )

        assertEquals(0.4f, memory.effectiveImportance, 0.001f)
    }

    @Test
    fun `isHighImportance returns true above threshold`() {
        val highImportance = Memory(
            id = "mem_1",
            messageId = "msg_1",
            projectId = "proj_1",
            content = "Important content",
            importance = 0.8f,
            createdAt = 1000L,
            chunkIndex = 0,
            chunkTotal = 1,
            embeddingId = "emb_1"
        )

        val lowImportance = highImportance.copy(importance = 0.3f)

        assertTrue(highImportance.isHighImportance)
        assertFalse(lowImportance.isHighImportance)
    }

    @Test
    fun `isDecayed returns true when decay below threshold`() {
        val fresh = Memory(
            id = "mem_1",
            messageId = "msg_1",
            projectId = "proj_1",
            content = "Content",
            importance = 0.5f,
            createdAt = 1000L,
            chunkIndex = 0,
            chunkTotal = 1,
            embeddingId = "emb_1",
            decayFactor = 1.0f
        )

        val decayed = fresh.copy(decayFactor = 0.05f)

        assertFalse(fresh.isDecayed)
        assertTrue(decayed.isDecayed)
    }

    @Test
    fun `toSearchResult creates MemorySearchResult with similarity`() {
        val memory = Memory(
            id = "mem_1",
            messageId = "msg_1",
            projectId = "proj_1",
            content = "Test content",
            importance = 0.7f,
            createdAt = 1000L,
            chunkIndex = 0,
            chunkTotal = 2,
            embeddingId = "emb_1"
        )

        val result = memory.toSearchResult(similarity = 0.9f)

        assertEquals("mem_1", result.id)
        assertEquals("Test content", result.content)
        assertEquals(0.7f, result.importance)
        assertEquals(0.9f, result.similarity)
        assertEquals(0, result.chunkIndex)
        assertEquals(2, result.chunkTotal)
    }

    @Test
    fun `copy preserves all fields`() {
        val original = Memory(
            id = "mem_1",
            messageId = "msg_1",
            projectId = "proj_1",
            content = "Content",
            importance = 0.5f,
            createdAt = 1000L,
            chunkIndex = 0,
            chunkTotal = 1,
            chunkTokens = 50,
            embeddingId = "emb_1",
            embeddingModel = "gemma",
            accessCount = 5,
            lastAccessedAt = 2000L,
            decayFactor = 0.9f,
            isPinned = true
        )

        val copied = original.copy(content = "New content")

        assertEquals("New content", copied.content)
        assertEquals(original.importance, copied.importance)
        assertEquals(original.isPinned, copied.isPinned)
        assertEquals(original.decayFactor, copied.decayFactor)
    }
}
