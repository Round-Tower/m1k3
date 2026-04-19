package app.m1k3.ai.domain.passages.services

import app.m1k3.ai.domain.memory.TokenCounter
import app.m1k3.ai.domain.passages.Passage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for PassageChunker.
 *
 * TDD: Phase 1 — Paragraph-aware greedy chunker for any text source
 * (notes, markdown, imports). Pure Kotlin, no platform dependencies.
 *
 * Strategy (MVP):
 * - Paragraph-aware greedy packing up to maxChunkTokens.
 * - Oversize paragraphs fall through to word-level splitting.
 * - Sequence numbers are monotonic starting at 0.
 * - Each passage carries totalPassagesInSource for isFirst/isLast convenience.
 */
class PassageChunkerTest {
    /**
     * Deterministic token counter for test independence:
     * 1 token per whitespace-separated word.
     */
    private class WordCounter : TokenCounter {
        override fun countTokens(text: String): Int = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size
    }

    private val sourceId = "src-1"

    @Test
    fun `empty content produces no passages`() {
        val chunker = PassageChunker(tokenCounter = WordCounter())

        val passages = chunker.chunk(sourceId, "")

        assertTrue(passages.isEmpty())
    }

    @Test
    fun `blank content produces no passages`() {
        val chunker = PassageChunker(tokenCounter = WordCounter())

        val passages = chunker.chunk(sourceId, "   \n\n   \n")

        assertTrue(passages.isEmpty())
    }

    @Test
    fun `single short paragraph becomes one passage`() {
        val chunker =
            PassageChunker(
                tokenCounter = WordCounter(),
                maxChunkTokens = 100,
                minChunkTokens = 1,
            )

        val passages = chunker.chunk(sourceId, "hello world from m1k3")

        assertEquals(1, passages.size)
        assertEquals(0, passages[0].sequence)
        assertEquals(1, passages[0].totalPassagesInSource)
        assertEquals("hello world from m1k3", passages[0].content)
        assertEquals(4, passages[0].tokenCount)
        assertEquals(sourceId, passages[0].sourceId)
    }

    @Test
    fun `multiple short paragraphs pack into a single passage when under budget`() {
        val chunker =
            PassageChunker(
                tokenCounter = WordCounter(),
                maxChunkTokens = 100,
                minChunkTokens = 1,
            )

        val content = "para one has four words\n\npara two has four words\n\npara three too"
        val passages = chunker.chunk(sourceId, content)

        assertEquals(1, passages.size, "Three small paragraphs should pack into one passage")
        assertTrue(passages[0].content.contains("para one"))
        assertTrue(passages[0].content.contains("para two"))
        assertTrue(passages[0].content.contains("para three"))
    }

    @Test
    fun `paragraphs that exceed the budget split across multiple passages`() {
        val chunker =
            PassageChunker(
                tokenCounter = WordCounter(),
                maxChunkTokens = 5,
                minChunkTokens = 1,
            )

        val content = "one two three four\n\nfive six seven eight\n\nnine ten eleven twelve"
        val passages = chunker.chunk(sourceId, content)

        assertTrue(passages.size >= 2, "Expected multiple passages, got ${passages.size}")
        passages.forEachIndexed { i, p ->
            assertEquals(i, p.sequence, "Sequence numbers must be monotonic from zero")
            assertEquals(passages.size, p.totalPassagesInSource)
        }
    }

    @Test
    fun `oversize single paragraph is split on word boundaries to stay within budget`() {
        val chunker =
            PassageChunker(
                tokenCounter = WordCounter(),
                maxChunkTokens = 3,
                minChunkTokens = 1,
            )

        val content = "alpha beta gamma delta epsilon zeta eta theta iota"
        val passages = chunker.chunk(sourceId, content)

        assertTrue(passages.size >= 3, "Oversize paragraph should split; got ${passages.size} passages")
        passages.forEach { p ->
            assertTrue(
                p.tokenCount <= 3,
                "Passage '${p.content}' has ${p.tokenCount} tokens, exceeds budget of 3",
            )
        }
    }

    @Test
    fun `passage ids are unique within a source`() {
        val chunker =
            PassageChunker(
                tokenCounter = WordCounter(),
                maxChunkTokens = 3,
                minChunkTokens = 1,
            )

        val content = "one two three\n\nfour five six\n\nseven eight nine"
        val passages = chunker.chunk(sourceId, content)

        val ids = passages.map(Passage::id)
        assertEquals(ids.size, ids.toSet().size, "Passage ids must be unique within a source")
    }

    @Test
    fun `passage ids include the source id for traceability`() {
        val chunker =
            PassageChunker(
                tokenCounter = WordCounter(),
                maxChunkTokens = 100,
                minChunkTokens = 1,
            )

        val passages = chunker.chunk("src-xyz", "some content here")

        assertEquals(1, passages.size)
        assertTrue(
            passages[0].id.contains("src-xyz"),
            "Passage id should reference its source id for traceability, got '${passages[0].id}'",
        )
    }

    @Test
    fun `different sources produce different passage ids even for identical content`() {
        val chunker =
            PassageChunker(
                tokenCounter = WordCounter(),
                maxChunkTokens = 100,
                minChunkTokens = 1,
            )

        val a = chunker.chunk("src-a", "identical content")
        val b = chunker.chunk("src-b", "identical content")

        assertEquals(1, a.size)
        assertEquals(1, b.size)
        assertNotEquals(a[0].id, b[0].id, "Passage ids must be namespaced by source id")
    }
}
