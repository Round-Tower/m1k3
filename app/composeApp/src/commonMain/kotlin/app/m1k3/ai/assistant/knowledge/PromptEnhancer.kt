package app.m1k3.ai.assistant.knowledge

/**
 * PromptEnhancer - Injects retrieved knowledge into AI prompts
 *
 * Formats retrieved facts into the system context for RAG-enhanced generation.
 * Maintains clean separation between knowledge and user query.
 */
object PromptEnhancer {

    /**
     * Enhance user prompt with retrieved knowledge
     *
     * Creates a structured prompt that includes:
     * - Retrieved facts as context
     * - Original user query
     * - Instructions to use knowledge accurately
     *
     * @param userQuery Original user question
     * @param retrievedFacts Facts from knowledge base
     * @return Enhanced prompt for AI generation
     */
    fun enhancePrompt(
        userQuery: String,
        retrievedFacts: List<RetrievedFact>
    ): EnhancedPrompt {
        if (retrievedFacts.isEmpty()) {
            // No knowledge found - use original query
            return EnhancedPrompt(
                enhancedQuery = userQuery,
                ragSources = emptyList(),
                hasKnowledge = false
            )
        }

        // Format retrieved facts into context
        val knowledgeContext = buildString {
            appendLine("Relevant knowledge from my database:")
            appendLine()
            retrievedFacts.forEachIndexed { index, retrieved ->
                appendLine("${index + 1}. ${retrieved.fact.question}")
                appendLine("   ${retrieved.fact.answer}")
                if (index < retrievedFacts.size - 1) {
                    appendLine()
                }
            }
        }

        // Create enhanced query with knowledge context
        val enhancedQuery = buildString {
            append(knowledgeContext)
            appendLine()
            appendLine("User question: $userQuery")
            appendLine()
            appendLine("Please provide a helpful, accurate answer using the knowledge above. If the knowledge doesn't fully address the question, you may supplement with your general knowledge, but prioritize the database information.")
        }

        // Extract source IDs for tracking
        val ragSources = retrievedFacts.map { it.fact.id }

        return EnhancedPrompt(
            enhancedQuery = enhancedQuery,
            ragSources = ragSources,
            hasKnowledge = true,
            knowledgePreview = retrievedFacts.take(3).map {
                KnowledgePreview(
                    category = it.fact.category,
                    question = it.fact.question,
                    relevanceScore = it.relevanceScore
                )
            }
        )
    }

    /**
     * Create a compact knowledge summary for UI display
     */
    fun formatKnowledgeSummary(retrievedFacts: List<RetrievedFact>): String {
        if (retrievedFacts.isEmpty()) return "No knowledge found"

        val categories = retrievedFacts.map { it.fact.category }.distinct()
        val factCount = retrievedFacts.size

        return buildString {
            append("📚 Using $factCount fact${if (factCount > 1) "s" else ""}")
            if (categories.isNotEmpty()) {
                append(" from: ${categories.joinToString(", ") { formatCategory(it) }}")
            }
        }
    }

    /**
     * Format category for display
     */
    private fun formatCategory(category: String): String {
        return category
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }
}

/**
 * Enhanced prompt with RAG metadata
 */
data class EnhancedPrompt(
    val enhancedQuery: String,
    val ragSources: List<String>,
    val hasKnowledge: Boolean,
    val knowledgePreview: List<KnowledgePreview> = emptyList()
)

/**
 * Preview of knowledge used for UI display
 */
data class KnowledgePreview(
    val category: String,
    val question: String,
    val relevanceScore: Double
)
