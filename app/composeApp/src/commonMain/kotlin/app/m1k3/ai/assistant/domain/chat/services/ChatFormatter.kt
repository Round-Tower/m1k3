package app.m1k3.ai.assistant.domain.chat.services

import app.m1k3.ai.assistant.domain.chat.format.ChatFormat
import app.m1k3.ai.assistant.domain.chat.format.MessageRole
import app.m1k3.ai.assistant.domain.tools.Tool
import app.m1k3.ai.assistant.domain.tools.ToolResult

/**
 * Chat Message - A single message in a conversation
 *
 * @property role Who sent this message
 * @property content The message text
 * @property timestamp When the message was sent (optional)
 */
data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: Long = 0
)

/**
 * Chat Formatter - Builds prompts in the correct format for target LLM
 *
 * Domain service interface - Pure Kotlin, no platform dependencies.
 *
 * Formats conversations, system prompts, and tool schemas into
 * the structure expected by different LLMs (ChatML, Llama, Gemma, etc.)
 *
 * **Usage:**
 * ```kotlin
 * val formatter: ChatFormatter = DefaultChatFormatter(ChatFormat.ChatML)
 *
 * val prompt = formatter.buildPrompt(
 *     systemPrompt = "You are helpful.",
 *     messages = conversationHistory,
 *     tools = availableTools
 * )
 * ```
 */
interface ChatFormatter {
    /**
     * The chat format being used
     */
    val format: ChatFormat

    /**
     * Build a complete prompt
     *
     * Assembles system prompt, conversation history, and optional
     * tool schema into the correct format for the LLM.
     *
     * @param systemPrompt Base instructions for the AI
     * @param messages Conversation history
     * @param tools Available tools (optional)
     * @param toolResults Recent tool results to include (optional)
     * @return Formatted prompt string
     */
    fun buildPrompt(
        systemPrompt: String,
        messages: List<ChatMessage>,
        tools: List<Tool> = emptyList(),
        toolResults: List<ToolResult> = emptyList()
    ): String

    /**
     * Format a tool result for inclusion in conversation
     *
     * @param result The tool execution result
     * @return Formatted string for the TOOL role
     */
    fun formatToolResult(result: ToolResult): String

    /**
     * Get stop tokens for generation
     *
     * @return List of tokens that should stop generation
     */
    fun getStopTokens(): List<String>
}
