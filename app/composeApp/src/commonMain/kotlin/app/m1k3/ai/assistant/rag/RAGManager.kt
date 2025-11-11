package app.m1k3.ai.assistant.rag

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.embedding.EmbeddingEngine
import app.m1k3.ai.assistant.knowledge.SemanticRetrievalService

/**
 * 間 AI - RAG Manager
 *
 * Orchestrates Retrieval-Augmented Generation (RAG) with intent-aware
 * knowledge retrieval. Combines IntentClassifier + SemanticRetrievalService
 * for intelligent, context-aware responses.
 *
 * **Philosophy:**
 * Smart retrieval beats blind retrieval. Classify intent first,
 * then fetch only relevant knowledge. No noise, just signal.
 *
 * **Architecture:**
 * 1. IntentClassifier → Detect query type (20 categories)
 * 2. SemanticRetrievalService → Fetch relevant facts (cosine similarity)
 * 3. RAGManager → Enrich system prompt with retrieved knowledge
 *
 * **Example:**
 * ```kotlin
 * val ragManager = RAGManager(database, embeddingEngine)
 * val enrichedPrompt = ragManager.enrichPrompt(
 *     userQuery = "My phone battery drains quickly",
 *     systemPrompt = "You are a helpful AI assistant..."
 * )
 * // Returns: systemPrompt + device troubleshooting knowledge
 * ```
 */
class RAGManager(
    private val database: MaDatabase,
    private val embeddingEngine: EmbeddingEngine
) {
    private val intentClassifier = IntentClassifier()
    private val retrievalService = SemanticRetrievalService(database, embeddingEngine)

    /**
     * RAG Result containing enriched prompt and metadata
     */
    data class RAGResult(
        val enrichedPrompt: String,
        val intent: IntentClassifier.Intent,
        val confidence: Float,
        val retrievedFacts: List<RetrievedFact>,
        val ragApplied: Boolean
    )

    /**
     * Retrieved fact with source and relevance
     */
    data class RetrievedFact(
        val content: String,
        val category: String,
        val similarity: Float
    )

    /**
     * Enrich system prompt with RAG knowledge
     *
     * Detects intent, retrieves relevant facts, and prepends
     * knowledge to system prompt for context-aware responses.
     *
     * @param userQuery User's current question
     * @param systemPrompt Base system prompt
     * @param enableRAG Enable/disable RAG (default: true)
     * @return RAGResult with enriched prompt and metadata
     */
    suspend fun enrichPrompt(
        userQuery: String,
        systemPrompt: String,
        enableRAG: Boolean = true
    ): RAGResult {
        // Early exit if RAG disabled or query is empty
        if (!enableRAG || userQuery.isBlank()) {
            return RAGResult(
                enrichedPrompt = systemPrompt,
                intent = IntentClassifier.Intent.GENERAL,
                confidence = 0f,
                retrievedFacts = emptyList(),
                ragApplied = false
            )
        }

        // 1. Classify intent
        val (intent, confidence) = intentClassifier.classifyWithConfidence(userQuery)

        // 2. Check if retrieval needed
        if (!intentClassifier.requiresKnowledgeRetrieval(intent)) {
            return RAGResult(
                enrichedPrompt = systemPrompt,
                intent = intent,
                confidence = confidence,
                retrievedFacts = emptyList(),
                ragApplied = false
            )
        }

        // 3. Retrieve relevant knowledge
        val retrievalLimit = intentClassifier.getRetrievalLimit(intent)
        val semanticFacts = retrievalService.retrieve(
            query = userQuery,
            limit = retrievalLimit,
            minSimilarity = 0.6f  // 60% minimum similarity for quality facts (filters noise)
        )

        // Convert to RetrievedFact
        val retrievedFacts = semanticFacts.map { semanticFact ->
            RetrievedFact(
                content = semanticFact.fact.answer,
                category = semanticFact.fact.category,
                similarity = semanticFact.similarityScore
            )
        }

        // 4. Build enriched prompt
        val enrichedPrompt = if (retrievedFacts.isNotEmpty()) {
            buildEnrichedPrompt(systemPrompt, intent, retrievedFacts)
        } else {
            systemPrompt
        }

        return RAGResult(
            enrichedPrompt = enrichedPrompt,
            intent = intent,
            confidence = confidence,
            retrievedFacts = retrievedFacts,
            ragApplied = retrievedFacts.isNotEmpty()
        )
    }

    /**
     * Build enriched prompt with retrieved knowledge
     *
     * Formats retrieved facts into a knowledge section that's
     * prepended to the system prompt.
     *
     * @param systemPrompt Base system prompt
     * @param intent Detected intent
     * @param facts Retrieved facts
     * @return Enriched system prompt
     */
    private fun buildEnrichedPrompt(
        systemPrompt: String,
        intent: IntentClassifier.Intent,
        facts: List<RetrievedFact>
    ): String {
        val knowledgeSection = buildString {
            appendLine()
            appendLine("**Relevant Knowledge (${intent.category}):**")
            appendLine()

            facts.forEachIndexed { index, fact ->
                appendLine("${index + 1}. ${fact.content}")
                appendLine("   [Category: ${fact.category}, Relevance: ${(fact.similarity * 100).toInt()}%]")
                appendLine()
            }

            appendLine("**RAG Instructions:**")
            appendLine("1. **Relevance:** Facts 80%+ = high confidence, 60-79% = use with caution, <60% = ignore")
            appendLine("2. **Usage:** Only use directly relevant facts. If no facts fit, respond from general knowledge only.")
            appendLine("3. **Quality:** Cite sources when using facts (e.g., \"According to device troubleshooting...\"). Be honest if knowledge insufficient.")
            appendLine("4. **Priority:** One highly relevant fact (85%) beats three moderately relevant facts (65%).")
            appendLine()
        }

        return knowledgeSection + systemPrompt
    }

    /**
     * Get RAG sources for database tracking
     *
     * Formats retrieved facts into JSON-compatible string for
     * storage in message.rag_sources field.
     *
     * Shows preview of fact content (first 60 chars) with similarity score.
     *
     * @param retrievedFacts Facts to serialize
     * @return JSON string of sources
     */
    fun formatRAGSources(retrievedFacts: List<RetrievedFact>): String? {
        if (retrievedFacts.isEmpty()) return null

        return retrievedFacts.mapIndexed { index, fact ->
            // Show preview of fact content (first 60 chars)
            val preview = if (fact.content.length > 60) {
                fact.content.take(60) + "..."
            } else {
                fact.content
            }
            "${index + 1}. ${preview} (${(fact.similarity * 100).toInt()}%)"
        }.joinToString(separator = "\n")
    }

    /**
     * Get average RAG confidence
     *
     * @param retrievedFacts Facts with similarity scores
     * @return Average confidence (0.0-1.0)
     */
    fun calculateRAGConfidence(retrievedFacts: List<RetrievedFact>): Double? {
        if (retrievedFacts.isEmpty()) return null

        return retrievedFacts.map { it.similarity.toDouble() }
            .average()
    }

    /**
     * Test RAG system with example queries
     *
     * Useful for debugging and verification.
     *
     * @param queries Test queries
     * @return Map of query → RAG result summary
     */
    suspend fun testRAG(queries: List<String>): Map<String, String> {
        return queries.associate { query ->
            val result = enrichPrompt(query, "You are a helpful AI assistant.")
            val summary = buildString {
                append("Intent: ${result.intent.category} (${(result.confidence * 100).toInt()}%)")
                if (result.retrievedFacts.isNotEmpty()) {
                    append(" | Facts: ${result.retrievedFacts.size}")
                    append(" | Avg Similarity: ${(result.retrievedFacts.map { it.similarity }.average() * 100).toInt()}%")
                } else {
                    append(" | No retrieval")
                }
            }
            query to summary
        }
    }
}
