package app.m1k3.ai.assistant.chat.usecase

import app.m1k3.ai.assistant.config.GenerationConstants
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
 * - RAG: Retrieves facts from knowledge base based on intent
 * - Memory: Retrieves semantic memories from conversation history
 * - Device: Adapts retrieval to device capabilities
 *
 * **Usage:**
 * ```kotlin
 * val useCase = ContextRetrievalUseCase(
 *     deviceInfo = deviceInfoProvider,
 *     preferences = preferencesStore,
 *     ragManager = ragManager,
 *     memoryManager = memoryManager
 * )
 *
 * val result = useCase.retrieveContext("What is photosynthesis?")
 * // Returns: EnrichedContext with RAG facts + memories
 * ```
 *
 * **Design Principles:**
 * - Single Responsibility: Only retrieves context, doesn't generate
 * - Fail-Safe: Individual retrieval failures don't break the flow
 * - Device-Adaptive: Adjusts memory topK based on device tier
 */
class ContextRetrievalUseCase(
    private val deviceInfo: DeviceInfoProviderInterface,
    private val preferences: PreferencesStoreInterface,
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
        var ragContext = ""
        var intentCategory = "GENERAL"
        var ragInfo: String? = null
        var ragSources: String? = null
        var ragConfidence: Double? = null
        var memoryContext = ""

        // 1. RAG retrieval (if enabled)
        if (isRagEnabled() && ragManager != null) {
            val ragResult = retrieveRagContext(prompt)
            ragContext = ragResult.context
            intentCategory = ragResult.intentCategory
            ragInfo = ragResult.ragInfo
            ragSources = ragResult.ragSources
            ragConfidence = ragResult.ragConfidence
        }

        // 2. Memory retrieval (if available)
        if (memoryManager != null) {
            memoryContext = retrieveMemoryContext(prompt)
        }

        // 3. Combine contexts
        val combinedContext = buildCombinedContext(ragContext, memoryContext)

        return EnrichedContext(
            context = combinedContext,
            intentCategory = intentCategory,
            ragInfo = ragInfo,
            ragSources = ragSources,
            ragConfidence = ragConfidence,
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

    // ===== Private Methods =====

    private suspend fun retrieveRagContext(prompt: String): RagResult {
        return try {
            val result = ragManager!!.enrichPrompt(
                userQuery = prompt,
                systemPrompt = "",
                enableRAG = true
            )

            if (result.ragApplied && result.enrichedPrompt.isNotEmpty()) {
                // Build RAG info display string
                val ragInfo = if (result.retrievedFacts.isNotEmpty()) {
                    val avgSimilarity = result.retrievedFacts.map { it.similarity }.average().toFloat()
                    val qualityEmoji = when {
                        avgSimilarity >= GenerationConstants.Similarity.HIGH_QUALITY -> "✅"
                        avgSimilarity >= GenerationConstants.Similarity.MEDIUM_QUALITY -> "⚠️"
                        else -> "❓"
                    }
                    "$qualityEmoji ${result.intent.category} (${(result.confidence * 100).toInt()}%) • ${result.retrievedFacts.size} facts"
                } else null

                RagResult(
                    context = result.enrichedPrompt,
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

    private fun buildCombinedContext(ragContext: String, memoryContext: String): String {
        return when {
            ragContext.isNotEmpty() && memoryContext.isNotEmpty() -> {
                "$ragContext\n\n## Recent Conversation:\n$memoryContext"
            }
            ragContext.isNotEmpty() -> ragContext
            memoryContext.isNotEmpty() -> "## Recent Conversation:\n$memoryContext"
            else -> ""
        }
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
 * - Metadata about what was retrieved (RAG, memory)
 * - Intent classification for config building
 */
data class EnrichedContext(
    /** Combined context string for prompt enrichment */
    val context: String,

    /** Detected intent category from RAG (e.g., "SCIENCE", "CODE_DEBUG") */
    val intentCategory: String,

    /** Human-readable RAG info (e.g., "✅ SCIENCE (85%) • 3 facts") */
    val ragInfo: String?,

    /** Formatted RAG sources for display */
    val ragSources: String?,

    /** RAG confidence score (0.0 - 1.0) */
    val ragConfidence: Double?,

    /** Whether RAG context was retrieved */
    val hasRagContext: Boolean,

    /** Whether memory context was retrieved */
    val hasMemoryContext: Boolean
) {
    /** Check if any context was retrieved */
    val hasContext: Boolean
        get() = hasRagContext || hasMemoryContext

    /** Check if context is empty */
    val isEmpty: Boolean
        get() = context.isEmpty()
}
