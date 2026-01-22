package app.m1k3.ai.assistant.domain.chat.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for ContextAssembler domain service
 *
 * TDD: Phase 2 - Domain Services
 */
class ContextAssemblerTest {

    private val assembler = ContextAssembler()

    @Test
    fun `assembles all context sources`() {
        val result = assembler.assembleContext(
            conversationHistory = "Previous: AI basics",
            ragContext = "- AI is artificial intelligence",
            memoryContext = "Memory: User asked about ML"
        )

        assertTrue(result.contains("AI basics"), "Should contain conversation history")
        assertTrue(result.contains("artificial intelligence"), "Should contain RAG context")
        assertTrue(result.contains("User asked about ML"), "Should contain memory context")
    }

    @Test
    fun `handles empty context gracefully`() {
        val result = assembler.assembleContext("", "", "")
        assertEquals("", result, "Empty contexts should produce empty result")
    }

    @Test
    fun `handles partial context`() {
        val result = assembler.assembleContext(
            conversationHistory = "User: What is AI?",
            ragContext = "",
            memoryContext = ""
        )

        assertTrue(result.contains("What is AI?"), "Should contain conversation history")
        assertEquals("User: What is AI?", result.trim(), "Should only contain non-empty context")
    }

    @Test
    fun `separates context with newlines`() {
        val result = assembler.assembleContext(
            conversationHistory = "Part 1",
            ragContext = "Part 2",
            memoryContext = "Part 3"
        )

        val lines = result.split("\n").filter { it.isNotBlank() }
        assertEquals(3, lines.size, "Should have 3 separate lines")
        assertEquals("Part 1", lines[0])
        assertEquals("Part 2", lines[1])
        assertEquals("Part 3", lines[2])
    }

    @Test
    fun `handles whitespace-only context as empty`() {
        val result = assembler.assembleContext(
            conversationHistory = "   ",
            ragContext = "\n\n",
            memoryContext = "\t"
        )

        assertEquals("", result, "Whitespace-only contexts should be treated as empty")
    }

    @Test
    fun `preserves context order - conversation, RAG, memory`() {
        val result = assembler.assembleContext(
            conversationHistory = "FIRST",
            ragContext = "SECOND",
            memoryContext = "THIRD"
        )

        val firstIndex = result.indexOf("FIRST")
        val secondIndex = result.indexOf("SECOND")
        val thirdIndex = result.indexOf("THIRD")

        assertTrue(firstIndex < secondIndex, "Conversation should come before RAG")
        assertTrue(secondIndex < thirdIndex, "RAG should come before memory")
    }
}
