package app.m1k3.ai.domain.rag.services

import app.m1k3.ai.domain.rag.RetrievedFact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ContextBudgetManager Tests
 *
 * Tests for context budget management to prevent overwhelming small models.
 * Manages token/char limits for RAG facts injection.
 */
class ContextBudgetManagerTest {

    // ========================================
    // calculateRagBudget Tests
    // ========================================

    @Test
    fun `returns full budget when no history or memory`() {
        val manager = ContextBudgetManager(maxContextChars = 1600)

        val budget = manager.calculateRagBudget(historyChars = 0, memoryChars = 0)

        assertEquals(1600, budget)
    }

    @Test
    fun `subtracts history and memory from budget`() {
        val manager = ContextBudgetManager(maxContextChars = 1600)

        val budget = manager.calculateRagBudget(historyChars = 400, memoryChars = 200)

        assertEquals(1000, budget)
    }

    @Test
    fun `returns zero when budget exceeded by history and memory`() {
        val manager = ContextBudgetManager(maxContextChars = 1600)

        val budget = manager.calculateRagBudget(historyChars = 1000, memoryChars = 800)

        assertEquals(0, budget)
    }

    // ========================================
    // selectFactsWithinBudget Tests
    // ========================================

    @Test
    fun `returns empty list for zero budget`() {
        val manager = ContextBudgetManager()
        val facts = listOf(createFact("Content", 0.9f))

        val selected = manager.selectFactsWithinBudget(facts, budgetChars = 0)

        assertTrue(selected.isEmpty())
    }

    @Test
    fun `returns empty list for empty facts`() {
        val manager = ContextBudgetManager()

        val selected = manager.selectFactsWithinBudget(emptyList(), budgetChars = 500)

        assertTrue(selected.isEmpty())
    }

    @Test
    fun `preserves order of facts (already sorted by similarity)`() {
        val manager = ContextBudgetManager()
        val facts = listOf(
            createFact("First highest", 0.95f),
            createFact("Second high", 0.85f),
            createFact("Third medium", 0.75f)
        )

        val selected = manager.selectFactsWithinBudget(facts, budgetChars = 500)

        assertEquals("First highest", selected[0])
        assertEquals("Second high", selected[1])
    }

    @Test
    fun `truncates long facts with ellipsis`() {
        val manager = ContextBudgetManager(maxFactLength = 20)
        val longContent = "This is a very long fact that exceeds the limit"
        val facts = listOf(createFact(longContent, 0.9f))

        val selected = manager.selectFactsWithinBudget(facts, budgetChars = 500)

        assertEquals(1, selected.size)
        assertEquals("This is a very lo...", selected[0])
        assertEquals(20, selected[0].length)
    }

    @Test
    fun `does not truncate short facts`() {
        val manager = ContextBudgetManager(maxFactLength = 100)
        val facts = listOf(createFact("Short fact", 0.9f))

        val selected = manager.selectFactsWithinBudget(facts, budgetChars = 500)

        assertEquals("Short fact", selected[0])
    }

    @Test
    fun `stops when budget exhausted`() {
        val manager = ContextBudgetManager(maxFactLength = 100)
        // Each fact + separator is ~15 chars, prefix "Facts: " is 7 chars
        // Budget 50 chars should fit ~2 facts max
        val facts = listOf(
            createFact("Fact one", 0.9f),   // 8 chars + 2 (". ") = 10
            createFact("Fact two", 0.8f),   // 8 chars + 2 = 10
            createFact("Fact three", 0.7f)  // Would exceed
        )

        // Budget: 7 (prefix) + 8 + 2 + 8 + 2 = 27 chars, allow ~35
        val selected = manager.selectFactsWithinBudget(facts, budgetChars = 35)

        assertEquals(2, selected.size)
    }

    // ========================================
    // formatFacts Tests
    // ========================================

    @Test
    fun `returns empty string for empty facts`() {
        val manager = ContextBudgetManager()

        val formatted = manager.formatFacts(emptyList())

        assertEquals("", formatted)
    }

    @Test
    fun `formats single fact without separator`() {
        val manager = ContextBudgetManager()

        val formatted = manager.formatFacts(listOf("AI is intelligence"))

        assertEquals("Facts: AI is intelligence", formatted)
    }

    @Test
    fun `formats multiple facts with period separator`() {
        val manager = ContextBudgetManager()

        val formatted = manager.formatFacts(listOf("AI is intelligence", "ML is a subset"))

        assertEquals("Facts: AI is intelligence. ML is a subset", formatted)
    }

    @Test
    fun `formatted output is single line`() {
        val manager = ContextBudgetManager()

        val formatted = manager.formatFacts(listOf("Fact one", "Fact two", "Fact three"))

        assertEquals(0, formatted.count { it == '\n' })
    }

    // ========================================
    // Integration Tests
    // ========================================

    @Test
    fun `end to end - calculates budget and selects facts`() {
        val manager = ContextBudgetManager(maxContextChars = 200, maxFactLength = 50)

        // History takes 100 chars, memory takes 50, leaving 50 for RAG
        val budget = manager.calculateRagBudget(historyChars = 100, memoryChars = 50)
        assertEquals(50, budget)

        val facts = listOf(
            createFact("Important fact about AI", 0.9f),
            createFact("Another fact about ML", 0.8f),
            createFact("Third fact", 0.7f)
        )

        val selected = manager.selectFactsWithinBudget(facts, budget)
        val formatted = manager.formatFacts(selected)

        // Should fit at least first fact in 50 char budget
        assertTrue(formatted.length <= 50)
        assertTrue(formatted.startsWith("Facts: "))
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createFact(content: String, similarity: Float): RetrievedFact {
        return RetrievedFact(
            content = content,
            category = "test_category",
            similarity = similarity
        )
    }
}
