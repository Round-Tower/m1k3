package app.m1k3.ai.assistant.chat.usecase

import app.m1k3.ai.assistant.config.GenerationConstants
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.memory.ContextResult
import app.m1k3.ai.assistant.memory.MemoryManager
import app.m1k3.ai.assistant.platform.DeviceInfoProviderInterface
import app.m1k3.ai.assistant.platform.PreferencesStoreInterface
import app.m1k3.ai.assistant.platform.PreferenceKeys
import app.m1k3.ai.assistant.platform.getMemoryTopK
import app.m1k3.ai.assistant.rag.RAGManager
import app.m1k3.ai.assistant.utils.Logger

private val logger = Logger.withTag("ContextRetrievalUseCase")

/**
 * ContextRetrievalUseCase - Retrieves relevant context for AI generation.
 *
 * Orchestrates context retrieval from multiple sources:
 * - Conversation History: Recent messages for multi-turn context
 * - RAG: Retrieves facts from knowledge base based on intent
 * - Memory: Retrieves semantic memories from conversation history
 * - Device: Adapts retrieval to device capabilities
 *
 * **Usage:**
 * ```kotlin
 * val useCase = ContextRetrievalUseCase(
 *     deviceInfo = deviceInfoProvider,
 *     preferences = preferencesStore,
 *     database = database,
 *     projectId = "default",
 *     ragManager = ragManager,
 *     memoryManager = memoryManager
 * )
 *
 * val result = useCase.retrieveContext("What is photosynthesis?")
 * // Returns: EnrichedContext with conversation history + RAG facts + memories
 * ```
 *
 * **Design Principles:**
 * - Single Responsibility: Only retrieves context, doesn't generate
 * - Fail-Safe: Individual retrieval failures don't break the flow
 * - Device-Adaptive: Adjusts history and memory limits based on device tier
 */
class ContextRetrievalUseCase(
    private val deviceInfo: DeviceInfoProviderInterface,
    private val preferences: PreferencesStoreInterface,
    private val database: MaDatabase? = null,
    private val projectId: String? = null,
    private val ragManager: RAGManager? = null,
    private val memoryManager: MemoryManager? = null
) {
    /**
     * Retrieve context for a given prompt.
     *
     * @param prompt The user's query
     * @return EnrichedContext containing all retrieved context
     */
    suspend fun retrieveContext(prompt: String): EnrichedContext {
        var conversationHistory = ""
        var ragContext = ""
        var intentCategory = "GENERAL"
        var ragInfo: String? = null
        var ragSources: String? = null
        var ragConfidence: Double? = null
        var memoryContext = ""

        // 1. Conversation history retrieval (for multi-turn context)
        if (database != null && projectId != null) {
            conversationHistory = retrieveConversationHistory()
        }

        // 2. RAG retrieval (if enabled)
        if (isRagEnabled() && ragManager != null) {
            val ragResult = retrieveRagContext(prompt)
            ragContext = ragResult.context
            intentCategory = ragResult.intentCategory
            ragInfo = ragResult.ragInfo
            ragSources = ragResult.ragSources
            ragConfidence = ragResult.ragConfidence
        }

        // 3. Semantic memory retrieval (if available)
        if (memoryManager != null) {
            memoryContext = retrieveMemoryContext(prompt)
        }

        // 4. Combine contexts
        val combinedContext = buildCombinedContext(conversationHistory, ragContext, memoryContext)

        return EnrichedContext(
            context = combinedContext,
            conversationHistory = conversationHistory,
            intentCategory = intentCategory,
            ragInfo = ragInfo,
            ragSources = ragSources,
            ragConfidence = ragConfidence,
            hasConversationHistory = conversationHistory.isNotEmpty(),
            hasRagContext = ragContext.isNotEmpty(),
            hasMemoryContext = memoryContext.isNotEmpty()
        )
    }

    /**
     * Check if RAG is enabled in preferences.
     */
    fun isRagEnabled(): Boolean {
        return preferences.getBoolean(PreferenceKeys.RAG_ENABLED, true)
    }

    /**
     * Get the memory topK value based on device tier.
     */
    fun getMemoryTopK(): Int {
        return deviceInfo.getMemoryTopK()
    }

    /**
     * Get the number of conversation history messages to include based on device tier.
     * Higher-end devices can handle more context.
     */
    fun getConversationHistoryLimit(): Int {
        val ramGb = deviceInfo.getDeviceRamGB()
        return when {
            ramGb >= 12 -> 10  // Flagship: last 10 messages (5 turns)
            ramGb >= 8 -> 8    // High-end: last 8 messages (4 turns)
            ramGb >= 6 -> 6    // Mid-range: last 6 messages (3 turns)
            else -> 4          // Budget: last 4 messages (2 turns)
        }
    }

    // ===== Private Methods =====

    /**
     * Retrieve recent conversation history for multi-turn context.
     *
     * **Important:** Small models (Gemma 270M) get confused by transcript format.
     * We only extract key topics from USER messages to provide context.
     *
     * **Why no assistant responses?**
     * Including previous assistant responses creates a feedback loop - if the model
     * gives a bad/refusing response, that gets fed back as context and the model
     * continues the refusal pattern. User topics are sufficient for follow-ups.
     */
    private fun retrieveConversationHistory(): String {
        return try {
            val limit = getConversationHistoryLimit()
            val messages = database!!.messageQueries
                .getRecentMessagesForProject(projectId!!, limit.toLong())
                .executeAsList()
                .reversed() // Reverse to get chronological order (oldest first)

            if (messages.isEmpty()) {
                return ""
            }

            // Only extract topics from USER messages (not assistant responses)
            val userMessages = messages.filter { it.role == "user" }

            if (userMessages.isEmpty()) {
                return ""
            }

            // Get meaningful topics (skip greetings)
            val topics = userMessages.mapNotNull { msg ->
                val content = msg.content.trim()
                if (content.length > 10 && !content.lowercase().matches(Regex("^(hi|hey|hello|what's up|sup).*"))) {
                    content.take(100)
                } else null
            }.takeLast(3) // Last 3 meaningful queries

            if (topics.isEmpty()) {
                return ""
            }

            val history = "Recent topics discussed: ${topics.joinToString("; ")}"
            logger.d { "Retrieved context from ${userMessages.size} user messages" }
            history
        } catch (e: Exception) {
            logger.w(e) { "Conversation history retrieval failed" }
            ""
        }
    }

    private suspend fun retrieveRagContext(prompt: String): RagResult {
        return try {
            val result = ragManager!!.enrichPrompt(
                userQuery = prompt,
                systemPrompt = "",
                enableRAG = true
            )

            if (result.ragApplied && result.retrievedFacts.isNotEmpty()) {
                // Build simple facts-only context (NO RAG instructions!)
                // The enrichedPrompt contains instructions that confuse small models.
                // Just give the model the raw facts as simple bullet points.
                val factsContext = result.retrievedFacts.joinToString("\n") { fact ->
                    "- ${fact.content}"
                }

                // Build RAG info display string for UI
                val avgSimilarity = result.retrievedFacts.map { it.similarity }.average().toFloat()
                val qualityEmoji = when {
                    avgSimilarity >= GenerationConstants.Similarity.HIGH_QUALITY -> "✅"
                    avgSimilarity >= GenerationConstants.Similarity.MEDIUM_QUALITY -> "⚠️"
                    else -> "❓"
                }
                val ragInfo = "$qualityEmoji ${result.intent.category} (${(result.confidence * 100).toInt()}%) • ${result.retrievedFacts.size} facts"

                RagResult(
                    context = factsContext,  // Facts only, no instructions
                    intentCategory = result.intent.category,
                    ragInfo = ragInfo,
                    ragSources = ragManager.formatRAGSources(result.retrievedFacts),
                    ragConfidence = ragManager.calculateRAGConfidence(result.retrievedFacts)
                )
            } else {
                RagResult(
                    context = "",
                    intentCategory = result.intent.category,
                    ragInfo = null,
                    ragSources = null,
                    ragConfidence = null
                )
            }
        } catch (e: Exception) {
            logger.w(e) { "RAG enrichment failed" }
            RagResult(
                context = "",
                intentCategory = "GENERAL",
                ragInfo = null,
                ragSources = null,
                ragConfidence = null
            )
        }
    }

    private suspend fun retrieveMemoryContext(prompt: String): String {
        return try {
            val topK = deviceInfo.getMemoryTopK()
            val result = memoryManager!!.retrieveRelevantMemories(prompt, topK)

            result.getOrNull()?.let { contextResult ->
                if (contextResult.selectedMemories.isNotEmpty()) {
                    contextResult.selectedMemories
                        .take(topK)
                        .joinToString("\n") { memory ->
                            val truncatedContent = memory.content.take(GenerationConstants.MemoryPreview.MAX_CONTENT_LENGTH)
                            val suffix = if (memory.content.length > GenerationConstants.MemoryPreview.MAX_CONTENT_LENGTH) "..." else ""
                            "Memory: $truncatedContent$suffix"
                        }.also {
                            logger.d { "Added ${contextResult.selectedMemories.size} memories to context" }
                        }
                } else {
                    ""
                }
            } ?: ""
        } catch (e: Exception) {
            logger.w(e) { "Memory retrieval failed" }
            ""
        }
    }

    private fun buildCombinedContext(
        conversationHistory: String,
        ragContext: String,
        memoryContext: String
    ): String {
        val parts = mutableListOf<String>()

        // Add conversation context (simplified for small models)
        // Note: Using plain text, NOT "User:"/"Assistant:" format
        if (conversationHistory.isNotEmpty()) {
            parts.add(conversationHistory)
        }

        // Add RAG facts as simple bullet points
        if (ragContext.isNotEmpty()) {
            parts.add(ragContext)
        }

        // Add semantic memories
        if (memoryContext.isNotEmpty()) {
            parts.add(memoryContext)
        }

        return parts.joinToString("\n")
    }

    /**
     * Internal result from RAG retrieval.
     */
    private data class RagResult(
        val context: String,
        val intentCategory: String,
        val ragInfo: String?,
        val ragSources: String?,
        val ragConfidence: Double?
    )
}

/**
 * Enriched context result containing all retrieved context.
 *
 * This is the output of ContextRetrievalUseCase and contains:
 * - Combined context string for prompt enrichment
 * - Conversation history for multi-turn context
 * - Metadata about what was retrieved (history, RAG, memory)
 * - Intent classification for config building
 */
data class EnrichedContext(
    /** Combined context string for prompt enrichment */
    val context: String,

    /** Formatted conversation history (User: X\nAssistant: Y\n...) */
    val conversationHistory: String? = null,

    /** Detected intent category from RAG (e.g., "SCIENCE", "CODE_DEBUG") */
    val intentCategory: String,

    /** Human-readable RAG info (e.g., "✅ SCIENCE (85%) • 3 facts") */
    val ragInfo: String?,

    /** Formatted RAG sources for display */
    val ragSources: String?,

    /** RAG confidence score (0.0 - 1.0) */
    val ragConfidence: Double?,

    /** Whether conversation history was retrieved */
    val hasConversationHistory: Boolean = false,

    /** Whether RAG context was retrieved */
    val hasRagContext: Boolean,

    /** Whether memory context was retrieved */
    val hasMemoryContext: Boolean
) {
    /** Check if any context was retrieved */
    val hasContext: Boolean
        get() = hasConversationHistory || hasRagContext || hasMemoryContext

    /** Check if context is empty */
    val isEmpty: Boolean
        get() = context.isEmpty()

    /** Count of turns in conversation history */
    val conversationTurnCount: Int
        get() = conversationHistory?.lines()?.size?.div(2) ?: 0
}
