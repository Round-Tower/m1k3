package app.m1k3.ai.domain.chat.services

import app.m1k3.ai.domain.chat.EnrichedContext
import app.m1k3.ai.domain.chat.format.MessageRole
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
 */
class UnifiedPromptBuilder(
    private val formatter: ChatFormatter,
    private val contextAssembler: ContextAssembler
) {
    companion object {
        private const val DEFAULT_SYSTEM_PROMPT =
            "You are M1k3, a pocket intelligence - always help the user complete their goal, always think step by step and be curious"
    }

    /**
     * Build a complete prompt for LLM generation.
     *
     * @param userPrompt The user's current message
     * @param context Enriched context from RAG/memory retrieval
     * @param tools Available tools (empty list disables tool calling)
     * @param systemPrompt Custom system prompt (uses default if empty)
     * @return Complete prompt string ready for LLM
     */
    fun build(
        userPrompt: String,
        context: EnrichedContext,
        tools: List<Tool> = emptyList(),
        systemPrompt: String = ""
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

        // Use formatter to build format-aware prompt
        return formatter.buildPrompt(
            systemPrompt = systemPrompt.ifEmpty { DEFAULT_SYSTEM_PROMPT },
            messages = messages,
            tools = tools
        )
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
