package app.m1k3.ai.domain.chat.format

import app.m1k3.ai.domain.tools.Tool

/**
 * Chat Format - LLM prompt format specification
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Different LLMs require different prompt structures. This abstraction
 * allows runtime format selection based on the target model.
 *
 * **Supported Formats:**
 * - ChatML: OpenAI-style (`<|im_start|>user`, `<|im_end|>`)
 * - Llama: Meta's format (`[INST]`, `<<SYS>>`)
 * - Gemma3: Google's format (`<start_of_turn>user`, `<end_of_turn>`)
 * - Simple: Plain text with no special tokens
 *
 * **Usage:**
 * ```kotlin
 * val format = ChatFormat.ChatML
 * val prompt = format.formatMessage(MessageRole.USER, "Hello")
 * // Result: "<|im_start|>user\nHello<|im_end|>\n"
 * ```
 */
sealed class ChatFormat {
    /**
     * Human-readable format name
     */
    abstract val name: String

    /**
     * Whether this format supports tool calling
     */
    abstract val supportsTools: Boolean

    /**
     * Whether this format supports a distinct system role.
     *
     * Formats without system role support (e.g., Gemma3) should have
     * their system prompt, tool schema, and messages consolidated into
     * a single user turn for optimal model performance.
     */
    abstract val supportsSystemRole: Boolean

    /**
     * Format a single message
     *
     * @param role The message sender's role
     * @param content The message content
     * @return Formatted message string
     */
    abstract fun formatMessage(role: MessageRole, content: String): String

    /**
     * Format tool schema for prompt inclusion
     *
     * @param tools List of available tools
     * @return Formatted tool schema (or empty if tools not supported)
     */
    abstract fun formatToolSchema(tools: List<Tool>): String

    /**
     * Get stop tokens for this format
     *
     * Stop tokens tell the LLM when to stop generating.
     *
     * @return List of stop token strings
     */
    abstract fun getStopTokens(): List<String>

    /**
     * Get prompt prefix (e.g., BOS token)
     *
     * Some models require a beginning-of-sequence token at the start
     * of the entire prompt. This is added once before all messages.
     *
     * @return Prefix string (empty if not needed)
     */
    open fun getPromptPrefix(): String = ""

    // ===== ChatML Format =====

    /**
     * ChatML format - OpenAI-style
     *
     * Used by: GPT models, Qwen, many fine-tuned models
     *
     * Format:
     * ```
     * <|im_start|>system
     * You are helpful.<|im_end|>
     * <|im_start|>user
     * Hello<|im_end|>
     * <|im_start|>assistant
     * Hi there!<|im_end|>
     * ```
     */
    data object ChatML : ChatFormat() {
        override val name = "ChatML"
        override val supportsTools = true
        override val supportsSystemRole = true

        override fun formatMessage(role: MessageRole, content: String): String =
            "<|im_start|>${role.value}\n$content<|im_end|>\n"

        override fun formatToolSchema(tools: List<Tool>): String = buildString {
            appendLine("<|im_start|>system")
            appendLine("You have access to the following tools:")
            appendLine()
            tools.forEach { append(it.toSchemaString()) }
            appendLine()
            appendLine("To use a tool, respond with: <tool_call>{\"tool\": \"tool_id\", \"args\": {...}}</tool_call>")
            appendLine("<|im_end|>")
        }

        override fun getStopTokens(): List<String> = listOf("<|im_end|>", "<|endoftext|>")
    }

    // ===== Llama Format =====

    /**
     * Llama format - Meta's Llama family
     *
     * Used by: Llama 2, Llama 3, Llama 3.1, Llama 3.2
     *
     * Format:
     * ```
     * <<SYS>>
     * You are helpful.
     * <</SYS>>
     *
     * [INST] Hello [/INST]
     * Hi there!</s>
     * ```
     */
    data object Llama : ChatFormat() {
        override val name = "Llama"
        override val supportsTools = true
        override val supportsSystemRole = true

        override fun formatMessage(role: MessageRole, content: String): String = when (role) {
            MessageRole.SYSTEM -> "<<SYS>>\n$content\n<</SYS>>\n\n"
            MessageRole.USER -> "[INST] $content [/INST]\n"
            MessageRole.ASSISTANT -> "$content\n"
            MessageRole.TOOL -> "[TOOL_RESULT] $content [/TOOL_RESULT]\n"
        }

        override fun formatToolSchema(tools: List<Tool>): String = buildString {
            appendLine("<<SYS>>")
            appendLine("Available tools:")
            appendLine()
            tools.forEach { append(it.toSchemaString()) }
            appendLine()
            appendLine("Use JSON format: {\"tool\": \"id\", \"args\": {}}")
            appendLine("<</SYS>>")
        }

        override fun getStopTokens(): List<String> = listOf("</s>", "[/INST]")
    }

    // ===== Gemma3 Format =====

    /**
     * Gemma3 format - Google's Gemma family
     *
     * Used by: Gemma 2, Gemma 3, SmolLM2 (partial compatibility)
     *
     * Note: Gemma doesn't have a distinct system role - system messages
     * are typically prepended to the first user message.
     *
     * Format:
     * ```
     * <start_of_turn>user
     * Hello<end_of_turn>
     * <start_of_turn>model
     * Hi there!<end_of_turn>
     * ```
     */
    data object Gemma3 : ChatFormat() {
        override val name = "Gemma3"
        override val supportsTools = true
        override val supportsSystemRole = false  // Gemma3 only has user/model turns

        override fun formatMessage(role: MessageRole, content: String): String = when (role) {
            MessageRole.USER -> "<start_of_turn>user\n$content<end_of_turn>\n"
            MessageRole.ASSISTANT -> "<start_of_turn>model\n$content<end_of_turn>\n"
            // Gemma has no system role - map to user turn
            MessageRole.SYSTEM -> "<start_of_turn>user\n$content<end_of_turn>\n"
            MessageRole.TOOL -> "<start_of_turn>user\n[Tool Result] $content<end_of_turn>\n"
        }

        override fun formatToolSchema(tools: List<Tool>): String = buildString {
            appendLine("<start_of_turn>user")
            appendLine("You can use these tools by responding with JSON:")
            appendLine()
            tools.forEach { append(it.toSchemaString()) }
            appendLine()
            appendLine("Format: {\"tool\": \"tool_id\", \"args\": {...}}")
            appendLine("<end_of_turn>")
        }

        override fun getStopTokens(): List<String> = listOf("<end_of_turn>", "<eos>")

        override fun getPromptPrefix(): String = "<bos>"
    }

    // ===== FalconH1 Format =====

    /**
     * FalconH1 format - TII's Falcon-H1 hybrid Mamba2/Attention model
     *
     * Used by: Falcon-H1-Tiny-90M-Instruct, Falcon-H1 family
     *
     * Uses Llama 3-style header tokens with end-of-turn markers.
     *
     * Format:
     * ```
     * <|begin_of_text|><|start_header_id|>system<|end_header_id|>
     * You are helpful.<|eot_id|>
     * <|start_header_id|>user<|end_header_id|>
     * Hello<|eot_id|>
     * <|start_header_id|>assistant<|end_header_id|>
     * Hi there!<|eot_id|>
     * ```
     */
    data object FalconH1 : ChatFormat() {
        override val name = "FalconH1"
        override val supportsTools = true
        override val supportsSystemRole = true

        override fun formatMessage(role: MessageRole, content: String): String {
            val headerRole = when (role) {
                MessageRole.SYSTEM -> "system"
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.TOOL -> "user" // Tool results go in user turn
            }
            val prefix = if (role == MessageRole.TOOL) "[Tool Result] " else ""
            return "<|start_header_id|>$headerRole<|end_header_id|>\n$prefix$content<|eot_id|>\n"
        }

        override fun formatToolSchema(tools: List<Tool>): String = buildString {
            appendLine("<|start_header_id|>system<|end_header_id|>")
            appendLine("You have access to the following tools:")
            appendLine()
            tools.forEach { append(it.toSchemaString()) }
            appendLine()
            appendLine("To use a tool, respond with: <tool_call>{\"tool\": \"tool_id\", \"args\": {...}}</tool_call>")
            appendLine("<|eot_id|>")
        }

        override fun getStopTokens(): List<String> = listOf("<|eot_id|>", "<|end_of_text|>")

        override fun getPromptPrefix(): String = "<|begin_of_text|>"
    }

    // ===== Gemma4 Format =====

    /**
     * Gemma4 format - Google's Gemma 4 family
     *
     * Used by: Gemma 4 E2B, Gemma 4 E4B, Gemma 4 26B, Gemma 4 31B
     *
     * Extends Gemma 3 format with:
     * - System role support (<start_of_turn>system)
     * - Extended thinking (<start_of_thinking> / <end_of_thinking>)
     * - Function calling (<tool_call> / <tool_response>)
     *
     * Format:
     * ```
     * <bos><start_of_turn>system
     * You are helpful.<end_of_turn>
     * <start_of_turn>user
     * Hello<end_of_turn>
     * <start_of_turn>model
     * Hi there!<end_of_turn>
     * ```
     */
    data object Gemma4 : ChatFormat() {
        override val name = "Gemma4"
        override val supportsTools = true
        override val supportsSystemRole = true

        override fun formatMessage(role: MessageRole, content: String): String = when (role) {
            MessageRole.SYSTEM -> "<start_of_turn>system\n$content<end_of_turn>\n"
            MessageRole.USER -> "<start_of_turn>user\n$content<end_of_turn>\n"
            MessageRole.ASSISTANT -> "<start_of_turn>model\n$content<end_of_turn>\n"
            MessageRole.TOOL -> "<start_of_turn>user\n<tool_response>$content</tool_response><end_of_turn>\n"
        }

        override fun formatToolSchema(tools: List<Tool>): String = buildString {
            appendLine("<start_of_turn>system")
            appendLine("You have access to the following tools:")
            appendLine()
            tools.forEach { append(it.toSchemaString()) }
            appendLine()
            appendLine("To use a tool, respond with: <tool_call>{\"tool\": \"tool_id\", \"args\": {...}}</tool_call>")
            appendLine("<end_of_turn>")
        }

        override fun getStopTokens(): List<String> = listOf("<end_of_turn>", "<eos>")

        override fun getPromptPrefix(): String = "<bos>"
    }

    // ===== Simple Format =====

    /**
     * Simple format - Plain text with no special tokens
     *
     * Used for: Basic completion models, testing, fallback
     *
     * No formatting is applied - just raw text with newlines.
     */
    data object Simple : ChatFormat() {
        override val name = "Simple"
        override val supportsTools = false
        override val supportsSystemRole = false  // Plain text, no roles

        override fun formatMessage(role: MessageRole, content: String): String = "$content\n"

        override fun formatToolSchema(tools: List<Tool>): String = ""

        override fun getStopTokens(): List<String> = emptyList()
    }
}
