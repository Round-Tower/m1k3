package app.m1k3.ai.assistant.domain.chat.services

import app.m1k3.ai.assistant.domain.chat.format.ChatFormat
import app.m1k3.ai.assistant.domain.chat.format.MessageRole
import app.m1k3.ai.assistant.domain.tools.Tool
import app.m1k3.ai.assistant.domain.tools.ToolResult

/**
 * Default Chat Formatter - Builds prompts using ChatFormat specifications
 *
 * Pure Kotlin implementation with no external dependencies.
 *
 * **Prompt Structure:**
 * ```
 * [Tool Schema (if tools provided)]
 * [System Prompt]
 * [Message 1]
 * [Message 2]
 * ...
 * [Tool Results (if any)]
 * [Assistant Turn Start (for generation)]
 * ```
 *
 * @param format The ChatFormat to use for formatting
 */
class DefaultChatFormatter(
    override val format: ChatFormat
) : ChatFormatter {

    override fun buildPrompt(
        systemPrompt: String,
        messages: List<ChatMessage>,
        tools: List<Tool>,
        toolResults: List<ToolResult>
    ): String = buildString {
        // 1. Tool schema (if tools provided and format supports them)
        if (tools.isNotEmpty() && format.supportsTools) {
            append(format.formatToolSchema(tools))
        }

        // 2. System prompt
        if (systemPrompt.isNotBlank()) {
            append(format.formatMessage(MessageRole.SYSTEM, systemPrompt))
        }

        // 3. Conversation messages
        messages.forEach { message ->
            append(format.formatMessage(message.role, message.content))
        }

        // 4. Tool results (if any)
        toolResults.forEach { result ->
            val resultText = formatToolResult(result)
            append(format.formatMessage(MessageRole.TOOL, resultText))
        }

        // 5. Start assistant turn (for generation)
        // This prompts the model to generate the assistant's response
        append(getAssistantTurnStart())
    }

    override fun formatToolResult(result: ToolResult): String = when (result) {
        is ToolResult.Success -> buildString {
            append("[Tool: ${result.toolId}] ")
            append(result.output)
            if (result.data != null) {
                append(" (data: ${result.data})")
            }
        }

        is ToolResult.Failure -> buildString {
            append("[Tool: ${result.toolId}] ")
            append("Error: ${result.error.displayMessage}")
        }

        is ToolResult.RequiresConfirmation -> buildString {
            append("[Tool: ${result.toolId}] ")
            append("Awaiting confirmation: ${result.confirmationPrompt}")
        }
    }

    override fun getStopTokens(): List<String> = format.getStopTokens()

    /**
     * Get the prefix that starts an assistant turn
     *
     * This is appended at the end to prompt the model to generate.
     */
    private fun getAssistantTurnStart(): String = when (format) {
        is ChatFormat.ChatML -> "<|im_start|>assistant\n"
        is ChatFormat.Llama -> "" // Llama generates after [/INST]
        is ChatFormat.Gemma3 -> "<start_of_turn>model\n"
        is ChatFormat.Simple -> ""
    }
}
