package app.m1k3.ai.assistant.rag

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.domain.rag.Intent
import app.m1k3.ai.domain.rag.services.IntentClassifier
import app.m1k3.ai.domain.usecases.rag.EnrichPromptWithRAGUseCase
import app.m1k3.ai.assistant.embedding.EmbeddingEngine
import app.m1k3.ai.assistant.knowledge.SemanticRetrievalService

/**
 * 間 AI - RAG Manager
 *
 * REFACTORED: Now delegates to EnrichPromptWithRAGUseCase (Clean Architecture).
 * Thin adapter layer for backward compatibility with existing code.
 *
 * **Architecture (New):**
 * 1. RAGManager (Application Layer) - Adapter for legacy code
 *     ↓
 * 2. EnrichPromptWithRAGUseCase (Domain Layer) - Business logic orchestration
 *     ↓
 * 3. KnowledgeRepository + IntentClassifier (Domain Layer) - Pure Kotlin services
 *
 * **Migration Note:**
 * This class is kept for backward compatibility. New code should use
 * EnrichPromptWithRAGUseCase directly via Koin injection:
 * ```kotlin
 * val enrichPromptUseCase: EnrichPromptWithRAGUseCase = get()
 * val result = enrichPromptUseCase.execute(userQuery, systemPrompt, enableRAG)
 * ```
 *
 * **Old Architecture (Deprecated):**
 * - IntentClassifier → Detect query type (20 categories)
 * - SemanticRetrievalService → Fetch relevant facts (cosine similarity)
 * - RAGManager → Enrich system prompt with retrieved knowledge
 */
class RAGManager(
    private val database: MaDatabase,
    private val embeddingEngine: EmbeddingEngine
) {
    // Legacy fields - kept for initialization but not used
    private val intentClassifier = IntentClassifier()
    private val retrievalService = SemanticRetrievalService(database, embeddingEngine)

    // NEW: Domain use case handles business logic
    private val enrichPromptUseCase = EnrichPromptWithRAGUseCase(
        knowledgeRepository = app.m1k3.ai.assistant.knowledge.KnowledgeRepositoryImpl(retrievalService),
        intentClassifier = intentClassifier
    )

    /**
     * RAG Result containing enriched prompt and metadata
     */
    data class RAGResult(
        val enrichedPrompt: String,
        val intent: Intent,
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
     * REFACTORED: Delegates to EnrichPromptWithRAGUseCase.
     * Legacy method kept for backward compatibility.
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
        // Delegate to domain use case
        val domainResult = enrichPromptUseCase.execute(
            userQuery = userQuery,
            systemPrompt = systemPrompt,
            enableRAG = enableRAG
        ).getOrElse { error ->
            // Fallback on error: return original prompt
            return RAGResult(
                enrichedPrompt = systemPrompt,
                intent = Intent.GENERAL,
                confidence = 0f,
                retrievedFacts = emptyList(),
                ragApplied = false
            )
        }

        // Map domain result to RAGManager result (types are compatible)
        return RAGResult(
            enrichedPrompt = domainResult.enrichedPrompt,
            intent = domainResult.intent,
            confidence = domainResult.confidence,
            retrievedFacts = domainResult.retrievedFacts.map { domainFact ->
                RetrievedFact(
                    content = domainFact.content,
                    category = domainFact.category,
                    similarity = domainFact.similarity
                )
            },
            ragApplied = domainResult.ragApplied
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
        intent: Intent,
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
