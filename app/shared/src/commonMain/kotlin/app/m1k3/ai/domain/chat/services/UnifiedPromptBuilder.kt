package app.m1k3.ai.domain.chat.services

import app.m1k3.ai.domain.chat.EnrichedContext
import app.m1k3.ai.domain.chat.format.MessageRole
import app.m1k3.ai.domain.platform.DeviceContext
import app.m1k3.ai.domain.tools.Tool

/**
 * Unified Prompt Builder - Single point for prompt construction.
 *
 * Consolidates all prompt building logic into one place:
 * - Context integration (RAG, memory, conversation history)
 * - Tool schema injection
 * - Format-aware message structure (Gemma3, ChatML, Llama)
 *
 * **Replaces:**
 * - Ad-hoc concatenation in SendMessageUseCase
 * - Inline formatting in ChatWithToolsUseCase
 * - Gemma3PromptBuilder direct usage
 *
 * **Usage:**
 * ```kotlin
 * val builder = UnifiedPromptBuilder(formatter, assembler)
 * val prompt = builder.build(
 *     userPrompt = "What time is it?",
 *     context = enrichedContext,
 *     tools = toolRegistry.getAvailableTools()
 * )
 * ```
 *
 * @property formatter ChatFormatter for format-specific message construction
 * @property contextAssembler ContextAssembler for combining context sources
 * @property deviceContextFormatter Formatter for device context strings
 */
class UnifiedPromptBuilder(
    private val formatter: ChatFormatter,
    private val contextAssembler: ContextAssembler,
    private val deviceContextFormatter: DeviceContextFormatter = DeviceContextFormatter()
) {
    companion object {
        private const val DEFAULT_SYSTEM_PROMPT =
            "You are M1k3, a helpful AI. Answer questions directly and conversationally."
    }

    /**
     * Build a complete prompt for LLM generation.
     *
     * @param userPrompt The user's current message
     * @param context Enriched context from RAG/memory retrieval
     * @param tools Available tools (empty list disables tool calling)
     * @param systemPrompt Custom system prompt (uses default if empty)
     * @param deviceContext Device/temporal context for prompt enrichment (optional)
     * @return Complete prompt string ready for LLM
     */
    fun build(
        userPrompt: String,
        context: EnrichedContext,
        tools: List<Tool> = emptyList(),
        systemPrompt: String = "",
        deviceContext: DeviceContext? = null
    ): String {
        // Build user message with context prepended
        val userMessage = buildUserMessage(userPrompt, context)

        // Convert to ChatMessage list for formatter
        val messages = listOf(
            ChatMessage(
                role = MessageRole.USER,
                content = userMessage
            )
        )

        // Build enriched system prompt with device context
        val enrichedSystemPrompt = buildSystemPrompt(
            basePrompt = systemPrompt.ifEmpty { DEFAULT_SYSTEM_PROMPT },
            deviceContext = deviceContext
        )

        // Use formatter to build format-aware prompt
        return formatter.buildPrompt(
            systemPrompt = enrichedSystemPrompt,
            messages = messages,
            tools = tools
        )
    }

    /**
     * Build system prompt with optional device context appended.
     */
    private fun buildSystemPrompt(basePrompt: String, deviceContext: DeviceContext?): String {
        return if (deviceContext != null) {
            val contextString = deviceContextFormatter.formatForSystemPrompt(deviceContext)
            "$basePrompt\n\n$contextString"
        } else {
            basePrompt
        }
    }

    /**
     * Build user message with context prepended.
     *
     * Context is placed before the user query so the model sees
     * relevant information first.
     */
    private fun buildUserMessage(userPrompt: String, context: EnrichedContext): String {
        return if (context.hasContext) {
            "${context.context}\n\n$userPrompt"
        } else {
            userPrompt
        }
    }
}
