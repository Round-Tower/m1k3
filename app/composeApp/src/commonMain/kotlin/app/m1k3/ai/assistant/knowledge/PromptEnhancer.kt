package app.m1k3.ai.assistant.knowledge

/**
 * PromptEnhancer - Injects retrieved knowledge into AI prompts
 *
 * Formats retrieved facts into the system context for RAG-enhanced generation.
 * Maintains clean separation between knowledge and user query.
 */
object PromptEnhancer {

    /**
     * Enhance user prompt with retrieved knowledge - PHASE1.5-006 (Updated)
     *
     * Creates a structured prompt that includes:
     * - Retrieved facts as context (with similarity scores for transparency)
     * - Original user query
     * - Relevance guardrails (allows model to ignore irrelevant knowledge)
     *
     * **Changes from v1:**
     * 1. Shows similarity scores (transparency)
     * 2. Pre-filters facts <0.6 similarity before injection
     * 3. Instructs model to IGNORE irrelevant knowledge
     * 4. Prioritizes answering user's question over using database
     *
     * @param userQuery Original user question
     * @param retrievedFacts Facts from knowledge base (now with similarity scores)
     * @param minSimilarity Minimum similarity to include (default: 0.6)
     * @return Enhanced prompt for AI generation
     */
    fun enhancePrompt(
        userQuery: String,
        retrievedFacts: List<RetrievedFact>,
        minSimilarity: Float = 0.6f
    ): EnhancedPrompt {
        if (retrievedFacts.isEmpty()) {
            // No knowledge found - use original query
            return EnhancedPrompt(
                enhancedQuery = userQuery,
                ragSources = emptyList(),
                hasKnowledge = false
            )
        }

        // PHASE1.5: Pre-filter facts by similarity threshold
        val relevantFacts: List<RetrievedFact> = if (retrievedFacts.firstOrNull() is SemanticRetrievedFact) {
            // Semantic retrieval: filter by similarity
            retrievedFacts.mapNotNull { fact ->
                val semanticFact = fact as? SemanticRetrievedFact
                if (semanticFact != null && semanticFact.similarityScore >= minSimilarity) {
                    semanticFact as RetrievedFact
                } else null
            }
        } else {
            // Keyword retrieval: no filtering (legacy support)
            retrievedFacts
        }

        if (relevantFacts.isEmpty()) {
            // All facts below threshold - don't inject any knowledge
            return EnhancedPrompt(
                enhancedQuery = userQuery,
                ragSources = emptyList(),
                hasKnowledge = false
            )
        }

        // Format retrieved facts with similarity scores (PHASE1.5: transparency)
        val knowledgeContext = buildString {
            appendLine("Retrieved knowledge from database:")
            appendLine()
            relevantFacts.forEachIndexed { index, retrieved ->
                // Show similarity score if available (semantic retrieval)
                val semanticFact = retrieved as? SemanticRetrievedFact
                if (semanticFact != null) {
                    val similarityPercent = (semanticFact.similarityScore * 100).toInt()
                    appendLine("${index + 1}. [Relevance: $similarityPercent%]")
                } else {
                    appendLine("${index + 1}.")
                }
                appendLine("   Q: ${retrieved.fact.question}")
                appendLine("   A: ${retrieved.fact.answer}")
                if (index < relevantFacts.size - 1) {
                    appendLine()
                }
            }
        }

        // PHASE1.5: New prompt template with relevance guardrails
        val enhancedQuery = buildString {
            append(knowledgeContext)
            appendLine()
            appendLine("User question: $userQuery")
            appendLine()
            appendLine("""
                Please provide a helpful, accurate answer using the knowledge above **only if it is directly relevant to the user's question**.

                Guidelines:
                - If the retrieved knowledge addresses the question, use it in your response
                - If the knowledge is tangentially related but not directly relevant, acknowledge it briefly then answer using your general knowledge
                - If the knowledge is not relevant, ignore it completely and answer using your general knowledge
                - Be honest about the limitations of the provided knowledge
                - Always prioritize answering the user's actual question over using the database

                Focus on being helpful and accurate above all else.
            """.trimIndent())
        }

        // Extract source IDs for tracking
        val ragSources = relevantFacts.map { it.fact.id }

        return EnhancedPrompt(
            enhancedQuery = enhancedQuery,
            ragSources = ragSources,
            hasKnowledge = true,
            knowledgePreview = relevantFacts.take(3).map { retrieved ->
                KnowledgePreview(
                    category = retrieved.fact.category,
                    question = retrieved.fact.question,
                    relevanceScore = retrieved.relevanceScore,
                    similarityScore = (retrieved as? SemanticRetrievedFact)?.similarityScore // PHASE1.5: optional similarity
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
 * Preview of knowledge used for UI display - PHASE1.5-006 (Updated)
 */
data class KnowledgePreview(
    val category: String,
    val question: String,
    val relevanceScore: Double,
    val similarityScore: Float? = null // PHASE1.5: optional similarity for semantic retrieval
)
