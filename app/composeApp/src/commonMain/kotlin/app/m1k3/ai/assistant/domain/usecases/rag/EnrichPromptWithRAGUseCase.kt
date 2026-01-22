package app.m1k3.ai.assistant.domain.usecases.rag

import app.m1k3.ai.assistant.domain.rag.Intent
import app.m1k3.ai.assistant.domain.rag.RetrievedFact
import app.m1k3.ai.assistant.domain.rag.services.IntentClassifier
import app.m1k3.ai.assistant.domain.repositories.KnowledgeRepository

/**
 * EnrichPromptWithRAGUseCase - RAG-enhanced prompt generation
 *
 * Domain use case extracted from RAGManager.kt.
 * Pure Kotlin, no platform dependencies.
 *
 * **Philosophy:**
 * Smart retrieval beats blind retrieval. Classify intent first,
 * then fetch only relevant knowledge. Category boosting ensures
 * "ai_ml_facts" ranks higher than "casual_conversation" for AI queries.
 *
 * **Orchestration Steps:**
 * ```
 * 1. Classify intent (IntentClassifier)
 *     ↓ (20 categories: AI_ML, MATH, DEVICE_TECH, etc.)
 * 2. Check if retrieval needed
 *     ↓ (skip CONVERSATIONAL, GENERAL)
 * 3. Retrieve facts (KnowledgeRepository)
 *     ↓ (semantic search, limit * 2 for re-ranking)
 * 4. Apply category boosting
 *     ↓ (+0.15 for matching categories)
 * 5. Re-rank and filter
 *     ↓ (sort by boosted similarity, take top K)
 * 6. Build enriched prompt
 *     ↓ (knowledge section + system prompt)
 * 7. Return RAGResult with metadata
 * ```
 *
 * **Category Boosting Example:**
 * - Query: "What is machine learning?"
 * - Intent: AI_ML (category: "AI & ML")
 * - Fact 1: "ML is AI subset" (ai_ml_facts, sim: 0.65) → 0.65 + 0.15 = 0.80
 * - Fact 2: "Casual chat" (casual_conversation, sim: 0.75) → 0.75 (no boost)
 * - Result: Fact 1 ranks higher (0.80 > 0.75) ✅
 *
 * **Usage:**
 * ```kotlin
 * val enrichPrompt = EnrichPromptWithRAGUseCase(
 *     knowledgeRepository = knowledgeRepo,
 *     intentClassifier = intentClassifier
 * )
 *
 * // Enrich prompt with RAG
 * enrichPrompt.execute(
 *     userQuery = "What is machine learning?",
 *     systemPrompt = "You are an AI assistant.",
 *     enableRAG = true
 * ).onSuccess { result ->
 *     println("Intent: ${result.intent.category}")
 *     println("Facts: ${result.retrievedFacts.size}")
 *     println("Enriched prompt:\n${result.enrichedPrompt}")
 * }
 * ```
 */
class EnrichPromptWithRAGUseCase(
    private val knowledgeRepository: KnowledgeRepository,
    private val intentClassifier: IntentClassifier
) {
    /**
     * Execute RAG enrichment
     *
     * @param userQuery User's current question
     * @param systemPrompt Base system prompt
     * @param enableRAG Enable/disable RAG (default: true)
     * @return Result.success(RAGResult) or Result.failure
     */
    suspend fun execute(
        userQuery: String,
        systemPrompt: String,
        enableRAG: Boolean = true
    ): Result<RAGResult> {
        return try {
            // Early exit if RAG disabled or query is empty
            if (!enableRAG || userQuery.isBlank()) {
                return Result.success(
                    RAGResult(
                        enrichedPrompt = systemPrompt,
                        intent = Intent.GENERAL,
                        confidence = 0f,
                        retrievedFacts = emptyList(),
                        ragApplied = false
                    )
                )
            }

            // 1. Classify intent
            val (intent, confidence) = intentClassifier.classifyWithConfidence(userQuery)

            // 2. Check if retrieval needed
            if (!intentClassifier.requiresKnowledgeRetrieval(intent)) {
                return Result.success(
                    RAGResult(
                        enrichedPrompt = systemPrompt,
                        intent = intent,
                        confidence = confidence,
                        retrievedFacts = emptyList(),
                        ragApplied = false
                    )
                )
            }

            // 3. Retrieve relevant knowledge with category boosting
            val retrievalLimit = intentClassifier.getRetrievalLimit(intent)
            val candidateLimit = retrievalLimit * 2 // Get more candidates for re-ranking

            val semanticFacts = knowledgeRepository.retrieve(
                query = userQuery,
                limit = candidateLimit,
                minSimilarity = MIN_SIMILARITY_THRESHOLD
            ).getOrThrow()

            // 4. Apply category boosting
            val intentCategory = intent.category.lowercase().replace(" ", "_").replace("&", "").trim()
            val boostedFacts = semanticFacts.map { fact ->
                val factCategory = fact.category.lowercase()

                // Boost if category matches intent (e.g., "ai_ml" matches "ai_ml_facts")
                val categoryMatch = factCategory.contains(intentCategory) ||
                        intentCategory.contains(factCategory.removeSuffix("_facts"))
                val boost = if (categoryMatch) CATEGORY_BOOST else 0f
                val boostedSimilarity = (fact.similarity + boost).coerceAtMost(1.0f)

                fact to boostedSimilarity
            }

            // 5. Re-rank by boosted similarity, filter by effective threshold, take limit
            val retrievedFacts = boostedFacts
                .filter { it.second >= EFFECTIVE_MIN_SIMILARITY }
                .sortedByDescending { it.second }
                .take(retrievalLimit)
                .map { (fact, boostedSim) ->
                    RetrievedFact(
                        content = fact.content,
                        category = fact.category,
                        similarity = boostedSim // Use boosted similarity
                    )
                }

            // 6. Build enriched prompt
            val enrichedPrompt = if (retrievedFacts.isNotEmpty()) {
                buildEnrichedPrompt(systemPrompt, intent, retrievedFacts)
            } else {
                systemPrompt
            }

            Result.success(
                RAGResult(
                    enrichedPrompt = enrichedPrompt,
                    intent = intent,
                    confidence = confidence,
                    retrievedFacts = retrievedFacts,
                    ragApplied = retrievedFacts.isNotEmpty()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    companion object {
        /**
         * Category boost for facts matching the detected intent.
         * Applied to semantic similarity scores to prioritize category-relevant facts.
         * Example: AI_ML query + ai_ml_facts category → +0.15 boost
         */
        private const val CATEGORY_BOOST = 0.15f

        /**
         * Minimum similarity threshold for initial retrieval.
         * Lower threshold to capture more candidates before category boosting.
         */
        private const val MIN_SIMILARITY_THRESHOLD = 0.40f

        /**
         * Effective minimum similarity after category boosting.
         * Facts must meet this threshold to be included in results.
         */
        private const val EFFECTIVE_MIN_SIMILARITY = 0.5f
    }
}

/**
 * RAG Result containing enriched prompt and metadata
 *
 * @property enrichedPrompt System prompt + knowledge section (if facts retrieved)
 * @property intent Detected intent category
 * @property confidence Intent classification confidence (0.0-1.0)
 * @property retrievedFacts Retrieved facts with boosted similarity scores
 * @property ragApplied Whether RAG enrichment was applied (facts retrieved)
 */
data class RAGResult(
    val enrichedPrompt: String,
    val intent: Intent,
    val confidence: Float,
    val retrievedFacts: List<RetrievedFact>,
    val ragApplied: Boolean
)
