package app.m1k3.ai.domain.chat.services

/**
 * Context Assembler
 *
 * Combines multiple context sources (conversation history, RAG facts, semantic memories)
 * into a single context string for AI prompt enrichment.
 *
 * Domain service - Pure Kotlin, no platform dependencies.
 *
 * **Philosophy:**
 * Context assembly should be deterministic and order-preserving:
 * 1. Conversation history (most recent exchanges)
 * 2. RAG knowledge (retrieved facts)
 * 3. Semantic memories (long-term context)
 *
 * **Usage:**
 * ```kotlin
 * val assembler = ContextAssembler()
 * val context = assembler.assembleContext(
 *     conversationHistory = "User: What is AI?\nAssistant: AI is...",
 *     ragContext = "- AI stands for Artificial Intelligence\n- Machine learning is...",
 *     memoryContext = "User previously asked about neural networks"
 * )
 * // Returns combined context with newline separation
 * ```
 */
class ContextAssembler {

    /**
     * Assemble context from multiple sources
     *
     * Combines conversation history, RAG-retrieved facts, and semantic memories
     * into a single context string. Empty/whitespace-only sources are filtered out.
     *
     * @param conversationHistory Recent conversation exchanges
     * @param ragContext Retrieved knowledge base facts (bullet points)
     * @param memoryContext Semantic memory chunks
     * @return Combined context string with newline separation
     */
    fun assembleContext(
        conversationHistory: String,
        ragContext: String,
        memoryContext: String
    ): String {
        val parts = mutableListOf<String>()

        // Add conversation history (first - most recent context)
        if (conversationHistory.isNotBlank()) {
            parts.add(conversationHistory.trim())
        }

        // Add RAG context (second - retrieved knowledge)
        if (ragContext.isNotBlank()) {
            parts.add(ragContext.trim())
        }

        // Add memory context (third - long-term context)
        if (memoryContext.isNotBlank()) {
            parts.add(memoryContext.trim())
        }

        return parts.joinToString("\n")
    }
}
