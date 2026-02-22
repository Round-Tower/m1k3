package app.m1k3.ai.domain.rag.services

import app.m1k3.ai.domain.rag.SemanticRetrievedFact

/**
 * PromptEnhancer - Injects retrieved knowledge into AI prompts
 *
 * Formats retrieved facts into the system context for RAG-enhanced generation.
 * Maintains clean separation between knowledge and user query.
 *
 * Domain service - Pure Kotlin, no platform dependencies.
 */
object PromptEnhancer {

    /**
     * Default minimum similarity threshold for including facts.
     */
    const val DEFAULT_MIN_SIMILARITY = 0.6f

    /**
     * Enhance user prompt with retrieved knowledge.
     *
     * Creates a structured prompt that includes:
     * - Retrieved facts as context (with similarity scores for transparency)
     * - Original user query
     * - Relevance guardrails (allows model to ignore irrelevant knowledge)
     *
     * @param userQuery Original user question
     * @param retrievedFacts Facts from knowledge base
     * @param minSimilarity Minimum similarity to include (default: 0.6)
     * @return Enhanced prompt for AI generation
     */
    fun enhancePrompt(
        userQuery: String,
        retrievedFacts: List<SemanticRetrievedFact>,
        minSimilarity: Float = DEFAULT_MIN_SIMILARITY
    ): EnhancedPrompt {
        if (retrievedFacts.isEmpty()) {
            return EnhancedPrompt(
                enhancedQuery = userQuery,
                ragSources = emptyList(),
                hasKnowledge = false
            )
        }

        // Pre-filter facts by similarity threshold
        val relevantFacts = retrievedFacts.filter { it.similarityScore >= minSimilarity }

        if (relevantFacts.isEmpty()) {
            return EnhancedPrompt(
                enhancedQuery = userQuery,
                ragSources = emptyList(),
                hasKnowledge = false
            )
        }

        // Format retrieved facts with similarity scores (transparency)
        val knowledgeContext = buildString {
            appendLine("Retrieved knowledge from database:")
            appendLine()
            relevantFacts.forEachIndexed { index, fact ->
                val similarityPercent = (fact.similarityScore * 100).toInt()
                appendLine("${index + 1}. [Relevance: $similarityPercent%]")
                appendLine("   Q: ${fact.question}")
                appendLine("   A: ${fact.answer}")
                if (index < relevantFacts.size - 1) {
                    appendLine()
                }
            }
        }

        // Prompt template with relevance guardrails
        val enhancedQuery = buildString {
            append(knowledgeContext)
            appendLine()
            appendLine("User question: $userQuery")
            appendLine()
            appendLine(PROMPT_GUIDELINES)
        }

        // Extract source IDs for tracking
        val ragSources = relevantFacts.map { it.id }

        return EnhancedPrompt(
            enhancedQuery = enhancedQuery,
            ragSources = ragSources,
            hasKnowledge = true,
            knowledgePreview = relevantFacts.take(3).map { fact ->
                KnowledgePreview(
                    category = fact.category,
                    question = fact.question,
                    relevanceScore = fact.relevanceScore,
                    similarityScore = fact.similarityScore
                )
            }
        )
    }

    /**
     * Create a compact knowledge summary for UI display.
     */
    fun formatKnowledgeSummary(retrievedFacts: List<SemanticRetrievedFact>): String {
        if (retrievedFacts.isEmpty()) return "No knowledge found"

        val categories = retrievedFacts.map { it.category }.distinct()
        val factCount = retrievedFacts.size

        return buildString {
            append("Using $factCount fact${if (factCount > 1) "s" else ""}")
            if (categories.isNotEmpty()) {
                append(" from: ${categories.joinToString(", ") { formatCategory(it) }}")
            }
        }
    }

    /**
     * Format category for display (snake_case to Title Case).
     */
    fun formatCategory(category: String): String {
        return category
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }

    private val PROMPT_GUIDELINES = """
        Please provide a helpful, accurate answer using the knowledge above **only if it is directly relevant to the user's question**.

        Guidelines:
        - If the retrieved knowledge addresses the question, use it in your response
        - If the knowledge is tangentially related but not directly relevant, acknowledge it briefly then answer using your general knowledge
        - If the knowledge is not relevant, ignore it completely and answer using your general knowledge
        - Be honest about the limitations of the provided knowledge
        - Always prioritize answering the user's actual question over using the database

        Focus on being helpful and accurate above all else.
    """.trimIndent()
}

/**
 * Enhanced prompt with RAG metadata.
 *
 * Domain entity representing the result of prompt enhancement.
 */
data class EnhancedPrompt(
    /** The enhanced query string with injected knowledge */
    val enhancedQuery: String,
    /** IDs of RAG sources used */
    val ragSources: List<String>,
    /** Whether knowledge was injected */
    val hasKnowledge: Boolean,
    /** Preview of knowledge for UI display */
    val knowledgePreview: List<KnowledgePreview> = emptyList()
)

/**
 * Preview of knowledge used for UI display.
 */
data class KnowledgePreview(
    val category: String,
    val question: String,
    val relevanceScore: Double,
    val similarityScore: Float? = null
)
