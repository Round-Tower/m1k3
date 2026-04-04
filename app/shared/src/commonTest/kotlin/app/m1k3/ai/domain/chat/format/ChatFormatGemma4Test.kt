package app.m1k3.ai.domain.chat.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD Tests for ChatFormat.Gemma4
 *
 * Gemma 4 uses the same turn-based format as Gemma 3 but adds:
 * - System role support (<start_of_turn>system)
 * - Extended thinking tokens
 * - Function calling with tool_call tokens
 */
class ChatFormatGemma4Test {

    private val format = ChatFormat.Gemma4

    @Test
    fun `Gemma4 has correct name`() {
        assertEquals("Gemma4", format.name)
    }

    @Test
    fun `Gemma4 supports tools`() {
        assertTrue(format.supportsTools)
    }

    @Test
    fun `Gemma4 supports system role`() {
        assertTrue(format.supportsSystemRole)
    }

    @Test
    fun `Gemma4 formats user message with start and end of turn`() {
        val message = format.formatMessage(MessageRole.USER, "Hello")
        assertTrue(message.contains("<start_of_turn>user"))
        assertTrue(message.contains("Hello"))
        assertTrue(message.contains("<end_of_turn>"))
    }

    @Test
    fun `Gemma4 formats assistant message as model turn`() {
        val message = format.formatMessage(MessageRole.ASSISTANT, "Hi there")
        assertTrue(message.contains("<start_of_turn>model"))
        assertTrue(message.contains("Hi there"))
        assertTrue(message.contains("<end_of_turn>"))
    }

    @Test
    fun `Gemma4 formats system message as system turn`() {
        val message = format.formatMessage(MessageRole.SYSTEM, "You are helpful")
        assertTrue(message.contains("<start_of_turn>system"))
        assertTrue(message.contains("You are helpful"))
        assertTrue(message.contains("<end_of_turn>"))
    }

    @Test
    fun `Gemma4 stop tokens include end_of_turn and eos`() {
        val tokens = format.getStopTokens()
        assertTrue(tokens.contains("<end_of_turn>"))
        assertTrue(tokens.contains("<eos>"))
    }

    @Test
    fun `Gemma4 prompt prefix is bos`() {
        assertEquals("<bos>", format.getPromptPrefix())
    }
}
