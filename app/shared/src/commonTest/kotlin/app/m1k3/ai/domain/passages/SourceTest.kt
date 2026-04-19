package app.m1k3.ai.domain.passages

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for Source domain entity + SourceKind enum.
 *
 * TDD: Phase 1 — Passage-shape foundation for unified-context RAG.
 */
class SourceTest {
    @Test
    fun `source with zero passages is considered empty`() {
        val source =
            Source(
                id = "src-1",
                uri = "file:///notes/today.md",
                kind = SourceKind.MARKDOWN,
                title = "Today",
                byteSize = 0,
                chunkCount = 0,
                importedAt = 1_000L,
            )

        assertTrue(source.isEmpty, "Source with zero chunks should be empty")
    }

    @Test
    fun `source with passages is not empty`() {
        val source =
            Source(
                id = "src-1",
                uri = "file:///notes/today.md",
                kind = SourceKind.MARKDOWN,
                title = "Today",
                byteSize = 4_096,
                chunkCount = 3,
                importedAt = 1_000L,
            )

        assertFalse(source.isEmpty)
    }

    @Test
    fun `day one source kinds are text and markdown only`() {
        // Contract: only kinds we have an ingestion path for on day one.
        // New kinds (WEB, PDF, NOTE, MEMORY, SYSTEM, TRANSCRIPT…) land with their own TDD cycle.
        assertEquals(2, SourceKind.values().size)
        assertTrue(SourceKind.values().contains(SourceKind.TEXT))
        assertTrue(SourceKind.values().contains(SourceKind.MARKDOWN))
    }
}
