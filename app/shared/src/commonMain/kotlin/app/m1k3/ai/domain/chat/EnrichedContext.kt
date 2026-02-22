package app.m1k3.ai.domain.chat

/**
 * EnrichedContext - Combined context for AI generation.
 *
 * Output of context retrieval containing:
 * - Combined context string for prompt enrichment
 * - Conversation history for multi-turn context
 * - Metadata about what was retrieved (history, RAG, memory)
 * - Intent classification for config building
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * @property context Combined context string for prompt enrichment
 * @property conversationHistory Formatted conversation history (User: X\nAssistant: Y\n...)
 * @property intentCategory Detected intent category from RAG (e.g., "SCIENCE", "CODE_DEBUG")
 * @property ragInfo Human-readable RAG info (e.g., "SCIENCE (85%) - 3 facts")
 * @property ragSources Formatted RAG sources for display
 * @property ragConfidence RAG confidence score (0.0 - 1.0)
 * @property hasConversationHistory Whether conversation history was retrieved
 * @property hasRagContext Whether RAG context was retrieved
 * @property hasMemoryContext Whether memory context was retrieved
 */
data class EnrichedContext(
    val context: String,
    val conversationHistory: String? = null,
    val intentCategory: String,
    val ragInfo: String? = null,
    val ragSources: String? = null,
    val ragConfidence: Double? = null,
    val hasConversationHistory: Boolean = false,
    val hasRagContext: Boolean,
    val hasMemoryContext: Boolean
) {
    /**
     * Check if any context was retrieved.
     */
    val hasContext: Boolean
        get() = hasConversationHistory || hasRagContext || hasMemoryContext

    /**
     * Check if context is empty.
     */
    val isEmpty: Boolean
        get() = context.isEmpty()

    /**
     * Count of turns in conversation history.
     */
    val conversationTurnCount: Int
        get() = conversationHistory?.lines()?.size?.div(2) ?: 0

    /**
     * Summary of what context sources were used.
     */
    val contextSummary: String
        get() = buildString {
            val sources = mutableListOf<String>()
            if (hasConversationHistory) sources.add("history")
            if (hasRagContext) sources.add("RAG")
            if (hasMemoryContext) sources.add("memory")

            if (sources.isEmpty()) {
                append("No context")
            } else {
                append(sources.joinToString(", "))
            }
        }

    companion object {
        /**
         * Create an empty context with a default intent.
         */
        fun empty(intentCategory: String = "CONVERSATIONAL"): EnrichedContext {
            return EnrichedContext(
                context = "",
                intentCategory = intentCategory,
                hasRagContext = false,
                hasMemoryContext = false
            )
        }
    }
}
