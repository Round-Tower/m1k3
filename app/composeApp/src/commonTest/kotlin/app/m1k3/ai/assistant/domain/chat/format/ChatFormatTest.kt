package app.m1k3.ai.assistant.domain.chat.format

import app.m1k3.ai.assistant.domain.tools.Tool
import app.m1k3.ai.assistant.domain.tools.ToolCategory
import app.m1k3.ai.assistant.domain.tools.ToolParameter
import app.m1k3.ai.assistant.domain.tools.ParameterType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for ChatFormat sealed class hierarchy
 *
 * TDD: These tests define the contract for ChatFormat and MessageRole.
 */
class ChatFormatTest {

    // ===== MessageRole Tests =====

    @Test
    fun `MessageRole has expected roles`() {
        val roles = MessageRole.entries

        assertEquals(4, roles.size)
        assertTrue(roles.any { it.name == "SYSTEM" })
        assertTrue(roles.any { it.name == "USER" })
        assertTrue(roles.any { it.name == "ASSISTANT" })
        assertTrue(roles.any { it.name == "TOOL" })
    }

    @Test
    fun `MessageRole has correct values`() {
        assertEquals("system", MessageRole.SYSTEM.value)
        assertEquals("user", MessageRole.USER.value)
        assertEquals("assistant", MessageRole.ASSISTANT.value)
        assertEquals("tool", MessageRole.TOOL.value)
    }

    // ===== ChatML Format Tests =====

    @Test
    fun `ChatML formats user message correctly`() {
        val format = ChatFormat.ChatML
        val message = format.formatMessage(MessageRole.USER, "Hello")

        assertTrue(message.startsWith("<|im_start|>user"))
        assertTrue(message.contains("Hello"))
        assertTrue(message.contains("<|im_end|>"))
    }

    @Test
    fun `ChatML formats assistant message correctly`() {
        val format = ChatFormat.ChatML
        val message = format.formatMessage(MessageRole.ASSISTANT, "Hi there!")

        assertTrue(message.startsWith("<|im_start|>assistant"))
        assertTrue(message.contains("Hi there!"))
        assertTrue(message.contains("<|im_end|>"))
    }

    @Test
    fun `ChatML formats system message correctly`() {
        val format = ChatFormat.ChatML
        val message = format.formatMessage(MessageRole.SYSTEM, "You are helpful.")

        assertTrue(message.startsWith("<|im_start|>system"))
        assertTrue(message.contains("You are helpful."))
        assertTrue(message.contains("<|im_end|>"))
    }

    @Test
    fun `ChatML supports tools`() {
        assertTrue(ChatFormat.ChatML.supportsTools)
    }

    @Test
    fun `ChatML name is correct`() {
        assertEquals("ChatML", ChatFormat.ChatML.name)
    }

    @Test
    fun `ChatML has correct stop tokens`() {
        val stopTokens = ChatFormat.ChatML.getStopTokens()

        assertTrue(stopTokens.contains("<|im_end|>"))
        assertTrue(stopTokens.contains("<|endoftext|>"))
    }

    // ===== Llama Format Tests =====

    @Test
    fun `Llama formats user message correctly`() {
        val format = ChatFormat.Llama
        val message = format.formatMessage(MessageRole.USER, "What is 2+2?")

        assertTrue(message.contains("[INST]"))
        assertTrue(message.contains("What is 2+2?"))
        assertTrue(message.contains("[/INST]"))
    }

    @Test
    fun `Llama formats system message correctly`() {
        val format = ChatFormat.Llama
        val message = format.formatMessage(MessageRole.SYSTEM, "Be concise.")

        assertTrue(message.contains("<<SYS>>"))
        assertTrue(message.contains("Be concise."))
        assertTrue(message.contains("<</SYS>>"))
    }

    @Test
    fun `Llama formats tool result correctly`() {
        val format = ChatFormat.Llama
        val message = format.formatMessage(MessageRole.TOOL, "Battery: 75%")

        assertTrue(message.contains("[TOOL_RESULT]"))
        assertTrue(message.contains("Battery: 75%"))
        assertTrue(message.contains("[/TOOL_RESULT]"))
    }

    @Test
    fun `Llama supports tools`() {
        assertTrue(ChatFormat.Llama.supportsTools)
    }

    @Test
    fun `Llama has correct stop tokens`() {
        val stopTokens = ChatFormat.Llama.getStopTokens()

        assertTrue(stopTokens.contains("</s>"))
        assertTrue(stopTokens.contains("[/INST]"))
    }

    // ===== Gemma3 Format Tests =====

    @Test
    fun `Gemma3 formats user message correctly`() {
        val format = ChatFormat.Gemma3
        val message = format.formatMessage(MessageRole.USER, "Tell me a joke")

        assertTrue(message.contains("<start_of_turn>user"))
        assertTrue(message.contains("Tell me a joke"))
        assertTrue(message.contains("<end_of_turn>"))
    }

    @Test
    fun `Gemma3 formats assistant message correctly`() {
        val format = ChatFormat.Gemma3
        val message = format.formatMessage(MessageRole.ASSISTANT, "Why did...")

        assertTrue(message.contains("<start_of_turn>model"))
        assertTrue(message.contains("Why did..."))
        assertTrue(message.contains("<end_of_turn>"))
    }

    @Test
    fun `Gemma3 maps system to user turn`() {
        // Gemma3 doesn't have a system role - maps to user
        val format = ChatFormat.Gemma3
        val message = format.formatMessage(MessageRole.SYSTEM, "You are helpful.")

        assertTrue(message.contains("<start_of_turn>user"))
        assertFalse(message.contains("system"))
    }

    @Test
    fun `Gemma3 supports tools`() {
        assertTrue(ChatFormat.Gemma3.supportsTools)
    }

    @Test
    fun `Gemma3 has correct stop tokens`() {
        val stopTokens = ChatFormat.Gemma3.getStopTokens()

        assertTrue(stopTokens.contains("<end_of_turn>"))
        assertTrue(stopTokens.contains("<eos>"))
    }

    // ===== Simple Format Tests =====

    @Test
    fun `Simple format just returns content`() {
        val format = ChatFormat.Simple
        val message = format.formatMessage(MessageRole.USER, "Hello world")

        assertEquals("Hello world\n", message)
    }

    @Test
    fun `Simple format does not support tools`() {
        assertFalse(ChatFormat.Simple.supportsTools)
    }

    @Test
    fun `Simple format has no stop tokens`() {
        assertTrue(ChatFormat.Simple.getStopTokens().isEmpty())
    }

    @Test
    fun `Simple format returns empty tool schema`() {
        val tools = listOf(createTestTool())
        val schema = ChatFormat.Simple.formatToolSchema(tools)

        assertEquals("", schema)
    }

    // ===== Tool Schema Tests =====

    @Test
    fun `ChatML formatToolSchema includes tool descriptions`() {
        val tools = listOf(
            createTestTool(),
            Tool(
                id = "get_battery_level",
                name = "Battery",
                description = "Gets battery level",
                parameters = emptyList(),
                category = ToolCategory.DEVICE_INFO
            )
        )

        val schema = ChatFormat.ChatML.formatToolSchema(tools)

        assertTrue(schema.contains("toggle_flashlight"))
        assertTrue(schema.contains("get_battery_level"))
        assertTrue(schema.contains("tool_call"))
    }

    @Test
    fun `Llama formatToolSchema includes tool descriptions`() {
        val tools = listOf(createTestTool())

        val schema = ChatFormat.Llama.formatToolSchema(tools)

        assertTrue(schema.contains("toggle_flashlight"))
        assertTrue(schema.contains("<<SYS>>"))
        assertTrue(schema.contains("<</SYS>>"))
    }

    @Test
    fun `Gemma3 formatToolSchema includes tool descriptions`() {
        val tools = listOf(createTestTool())

        val schema = ChatFormat.Gemma3.formatToolSchema(tools)

        assertTrue(schema.contains("toggle_flashlight"))
        assertTrue(schema.contains("<start_of_turn>"))
    }

    // ===== Helper =====

    private fun createTestTool() = Tool(
        id = "toggle_flashlight",
        name = "Flashlight",
        description = "Toggle device flashlight",
        parameters = listOf(
            ToolParameter(
                name = "enable",
                type = ParameterType.BOOLEAN,
                description = "Turn on or off",
                required = true
            )
        ),
        category = ToolCategory.SYSTEM
    )
}
