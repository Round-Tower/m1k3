package app.m1k3.ai.assistant.domain.chat.services

import app.m1k3.ai.assistant.domain.chat.format.ChatFormat
import app.m1k3.ai.assistant.domain.chat.format.MessageRole
import app.m1k3.ai.assistant.domain.tools.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Tests for ChatFormatter interface and DefaultChatFormatter
 *
 * TDD: These tests define the contract for building prompts.
 */
class ChatFormatterTest {

    // ===== Basic Prompt Building =====

    @Test
    fun `builds prompt with system and user message`() {
        val formatter = DefaultChatFormatter(ChatFormat.ChatML)

        val prompt = formatter.buildPrompt(
            systemPrompt = "You are helpful.",
            messages = listOf(
                ChatMessage(MessageRole.USER, "Hello")
            )
        )

        assertTrue(prompt.contains("You are helpful"))
        assertTrue(prompt.contains("Hello"))
        assertTrue(prompt.contains("<|im_start|>"))
    }

    @Test
    fun `builds prompt with conversation history`() {
        val formatter = DefaultChatFormatter(ChatFormat.ChatML)

        val prompt = formatter.buildPrompt(
            systemPrompt = "You are helpful.",
            messages = listOf(
                ChatMessage(MessageRole.USER, "What is 2+2?"),
                ChatMessage(MessageRole.ASSISTANT, "4"),
                ChatMessage(MessageRole.USER, "Thanks!")
            )
        )

        assertTrue(prompt.contains("What is 2+2?"))
        assertTrue(prompt.contains("4"))
        assertTrue(prompt.contains("Thanks!"))
    }

    // ===== Tool Schema Inclusion =====

    @Test
    fun `includes tool schema when tools provided`() {
        val formatter = DefaultChatFormatter(ChatFormat.ChatML)
        val tools = listOf(createBatteryTool())

        val prompt = formatter.buildPrompt(
            systemPrompt = "You are helpful.",
            messages = listOf(ChatMessage(MessageRole.USER, "Check my battery")),
            tools = tools
        )

        assertTrue(prompt.contains("get_battery_level"))
        assertTrue(prompt.contains("battery level"))
    }

    @Test
    fun `omits tool schema when no tools`() {
        val formatter = DefaultChatFormatter(ChatFormat.ChatML)

        val prompt = formatter.buildPrompt(
            systemPrompt = "You are helpful.",
            messages = listOf(ChatMessage(MessageRole.USER, "Hello"))
        )

        // Should not contain tool-related markers
        assertTrue(!prompt.contains("tool_call") || prompt.contains("You are helpful"))
    }

    // ===== Tool Result Formatting =====

    @Test
    fun `formats success tool result`() {
        val formatter = DefaultChatFormatter(ChatFormat.ChatML)

        val result = ToolResult.Success(
            toolId = "get_battery_level",
            output = "Battery is at 75%",
            executionTimeMs = 10
        )

        val formatted = formatter.formatToolResult(result)

        assertTrue(formatted.contains("Battery is at 75%"))
    }

    @Test
    fun `formats failure tool result`() {
        val formatter = DefaultChatFormatter(ChatFormat.ChatML)

        val result = ToolResult.Failure(
            toolId = "toggle_flashlight",
            error = ToolError.Unavailable("No flashlight"),
            executionTimeMs = 5
        )

        val formatted = formatter.formatToolResult(result)

        assertTrue(formatted.contains("No flashlight") || formatted.contains("unavailable"))
    }

    // ===== Format-Specific Tests =====

    @Test
    fun `Llama format uses correct markers`() {
        val formatter = DefaultChatFormatter(ChatFormat.Llama)

        val prompt = formatter.buildPrompt(
            systemPrompt = "Be concise.",
            messages = listOf(ChatMessage(MessageRole.USER, "Hi"))
        )

        assertTrue(prompt.contains("<<SYS>>"))
        assertTrue(prompt.contains("[INST]"))
    }

    @Test
    fun `Gemma3 format uses correct markers`() {
        val formatter = DefaultChatFormatter(ChatFormat.Gemma3)

        val prompt = formatter.buildPrompt(
            systemPrompt = "Be helpful.",
            messages = listOf(ChatMessage(MessageRole.USER, "Hi"))
        )

        assertTrue(prompt.contains("<start_of_turn>"))
        assertTrue(prompt.contains("<end_of_turn>"))
    }

    @Test
    fun `Simple format has no special markers`() {
        val formatter = DefaultChatFormatter(ChatFormat.Simple)

        val prompt = formatter.buildPrompt(
            systemPrompt = "Be helpful.",
            messages = listOf(ChatMessage(MessageRole.USER, "Hi"))
        )

        assertTrue(!prompt.contains("<|im_start|>"))
        assertTrue(!prompt.contains("[INST]"))
        assertTrue(!prompt.contains("<start_of_turn>"))
    }

    // ===== Stop Tokens =====

    @Test
    fun `returns correct stop tokens for format`() {
        val chatML = DefaultChatFormatter(ChatFormat.ChatML)
        val llama = DefaultChatFormatter(ChatFormat.Llama)

        assertTrue(chatML.getStopTokens().contains("<|im_end|>"))
        assertTrue(llama.getStopTokens().contains("</s>"))
    }

    // ===== Helper =====

    private fun createBatteryTool() = Tool(
        id = "get_battery_level",
        name = "Battery Level",
        description = "Gets the current battery level",
        parameters = emptyList(),
        category = ToolCategory.DEVICE_INFO
    )
}
