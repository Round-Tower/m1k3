package app.m1k3.ai.domain.passages

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for Passage domain entity.
 *
 * TDD: Phase 1 — Passage-shape foundation for unified-context RAG.
 */
class PassageTest {
    private fun passage(
        sequence: Int,
        totalPassages: Int,
    ) = Passage(
        id = "src-1_passage_$sequence",
        sourceId = "src-1",
        sequence = sequence,
        content = "body",
        tokenCount = 42,
        totalPassagesInSource = totalPassages,
    )

    @Test
    fun `first passage is identified by sequence zero`() {
        val first = passage(sequence = 0, totalPassages = 3)

        assertTrue(first.isFirstPassage)
        assertFalse(first.isLastPassage)
    }

    @Test
    fun `last passage is identified by sequence equals total minus one`() {
        val last = passage(sequence = 2, totalPassages = 3)

        assertFalse(last.isFirstPassage)
        assertTrue(last.isLastPassage)
    }

    @Test
    fun `middle passage is neither first nor last`() {
        val middle = passage(sequence = 1, totalPassages = 3)

        assertFalse(middle.isFirstPassage)
        assertFalse(middle.isLastPassage)
    }

    @Test
    fun `single passage source has a passage that is both first and last`() {
        val only = passage(sequence = 0, totalPassages = 1)

        assertTrue(only.isFirstPassage)
        assertTrue(only.isLastPassage)
    }
}
