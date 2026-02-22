package app.m1k3.ai.domain.rag.services

import app.m1k3.ai.domain.rag.RetrievedFact

/**
 * Context Budget Manager
 *
 * Manages context character budgets for small language models.
 * Uses approximate token counting (chars/4) for efficiency.
 *
 * Domain service - Pure Kotlin, no platform dependencies.
 *
 * **Philosophy:**
 * Small models (SmolLM2-360M) get overwhelmed when context is too large.
 * This manager ensures RAG facts fit within a budget that leaves room
 * for conversation history, memory, and response generation.
 *
 * @property maxContextChars Maximum chars for all RAG context (~400 tokens)
 * @property maxFactLength Maximum chars per fact before truncation
 */
class ContextBudgetManager(
    private val maxContextChars: Int = DEFAULT_MAX_CONTEXT_CHARS,
    private val maxFactLength: Int = DEFAULT_MAX_FACT_LENGTH
) {
    /**
     * Calculate available character budget for RAG facts.
     *
     * Subtracts space used by conversation history and memory context,
     * returning remaining space for RAG facts.
     *
     * @param historyChars Characters used by conversation history
     * @param memoryChars Characters used by memory context
     * @return Available characters for RAG facts
     */
    fun calculateRagBudget(historyChars: Int, memoryChars: Int): Int {
        val usedChars = historyChars + memoryChars
        return (maxContextChars - usedChars).coerceAtLeast(0)
    }

    /**
     * Select facts that fit within the character budget.
     *
     * Facts should already be sorted by similarity (highest first).
     * Takes facts greedily until budget exhausted, truncating long facts.
     *
     * @param facts Sorted facts (highest similarity first)
     * @param budgetChars Available character budget
     * @return List of fact content strings that fit within budget
     */
    fun selectFactsWithinBudget(
        facts: List<RetrievedFact>,
        budgetChars: Int
    ): List<String> {
        if (budgetChars <= 0 || facts.isEmpty()) return emptyList()

        val selected = mutableListOf<String>()
        var usedChars = FACTS_PREFIX.length // "Facts: "

        for (fact in facts) {
            val truncatedContent = truncateFact(fact.content)
            val factChars = truncatedContent.length + FACT_SEPARATOR.length

            if (usedChars + factChars > budgetChars) break

            selected.add(truncatedContent)
            usedChars += factChars
        }

        return selected
    }

    /**
     * Format selected facts into minimal context string.
     *
     * Uses inline format: "Facts: x. y. z."
     * Single line, no bullets, minimal overhead.
     *
     * @param facts Selected fact content strings
     * @return Formatted string or empty if no facts
     */
    fun formatFacts(facts: List<String>): String {
        if (facts.isEmpty()) return ""
        return FACTS_PREFIX + facts.joinToString(FACT_SEPARATOR)
    }

    /**
     * Truncate fact to max length with ellipsis.
     */
    private fun truncateFact(content: String): String {
        val trimmed = content.trim()
        return if (trimmed.length <= maxFactLength) {
            trimmed
        } else {
            trimmed.take(maxFactLength - ELLIPSIS.length) + ELLIPSIS
        }
    }

    companion object {
        /** Default max chars for RAG context (~400 tokens) */
        const val DEFAULT_MAX_CONTEXT_CHARS = 1600

        /** Default max chars per fact */
        const val DEFAULT_MAX_FACT_LENGTH = 100

        /** Prefix for formatted facts */
        const val FACTS_PREFIX = "Facts: "

        /** Separator between facts */
        const val FACT_SEPARATOR = ". "

        /** Ellipsis for truncated facts */
        private const val ELLIPSIS = "..."
    }
}
