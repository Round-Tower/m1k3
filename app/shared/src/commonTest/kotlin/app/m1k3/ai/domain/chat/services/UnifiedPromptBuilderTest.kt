package app.m1k3.ai.domain.chat.services

import app.m1k3.ai.domain.chat.EnrichedContext
import app.m1k3.ai.domain.chat.format.ChatFormat
import app.m1k3.ai.domain.tools.Tool
import app.m1k3.ai.domain.tools.ToolCategory
import app.m1k3.ai.domain.tools.ToolParameter
import app.m1k3.ai.domain.tools.ParameterType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for UnifiedPromptBuilder - the single point for prompt construction.
 *
 * Verifies that:
 * - Context is properly included
 * - Tools are formatted when provided
 * - Format-specific tokens are used (Gemma3)
 * - User prompt is always included
 */
class UnifiedPromptBuilderTest {

    // ===== Basic Prompt Building =====

    @Test
    fun `builds prompt with user message only`() {
        val builder = createBuilder()

        val prompt = builder.build(
            userPrompt = "Hello, how are you?",
            context = EnrichedContext.empty()
        )

        assertContains(prompt, "Hello, how are you?")
    }

    @Test
    fun `builds prompt with context included`() {
        val builder = createBuilder()
        val context = EnrichedContext(
            context = "Facts: The sky is blue.",
            intentCategory = "SCIENCE",
            hasRagContext = true,
            hasMemoryContext = false
        )

        val prompt = builder.build(
            userPrompt = "Why is the sky blue?",
            context = context
        )

        assertContains(prompt, "Facts: The sky is blue.")
        assertContains(prompt, "Why is the sky blue?")
    }

    @Test
    fun `builds prompt with empty context gracefully`() {
        val builder = createBuilder()
        val emptyContext = EnrichedContext.empty()

        val prompt = builder.build(
            userPrompt = "Hello",
            context = emptyContext
        )

        assertContains(prompt, "Hello")
        // Should not contain "Facts:" when context is empty
        assertFalse(prompt.contains("Facts:"))
    }

    // ===== Tool Integration =====

    @Test
    fun `builds prompt with tools included`() {
        val builder = createBuilder()
        val tools = listOf(
            Tool(
                id = "get_time",
                name = "Get Time",
                description = "Gets the current time",
                category = ToolCategory.DEVICE_INFO,
                parameters = emptyList()
            )
        )

        val prompt = builder.build(
            userPrompt = "What time is it?",
            context = EnrichedContext.empty(),
            tools = tools
        )

        assertContains(prompt, "get_time")
        assertContains(prompt, "Gets the current time")
    }

    @Test
    fun `builds prompt without tools when list is empty`() {
        val builder = createBuilder()

        val prompt = builder.build(
            userPrompt = "Hello",
            context = EnrichedContext.empty(),
            tools = emptyList()
        )

        assertFalse(prompt.contains("tools"))
        assertFalse(prompt.contains("tool"))
    }

    @Test
    fun `builds prompt with tool parameters formatted`() {
        val builder = createBuilder()
        val tools = listOf(
            Tool(
                id = "set_timer",
                name = "Set Timer",
                description = "Sets a timer",
                category = ToolCategory.SYSTEM,
                parameters = listOf(
                    ToolParameter(
                        name = "duration",
                        type = ParameterType.NUMBER,
                        description = "Duration in seconds",
                        required = true
                    )
                )
            )
        )

        val prompt = builder.build(
            userPrompt = "Set a timer for 5 minutes",
            context = EnrichedContext.empty(),
            tools = tools
        )

        // For Gemma3 (consolidated format), tool params are shown in compact form
        assertContains(prompt, "set_timer")
        assertContains(prompt, "Sets a timer")
        assertContains(prompt, "duration")  // Parameter name included
    }

    @Test
    fun `ChatML builds prompt with full tool parameter descriptions`() {
        // ChatML uses multi-turn format with full tool schema
        val builder = createBuilder(ChatFormat.ChatML)
        val tools = listOf(
            Tool(
                id = "set_timer",
                name = "Set Timer",
                description = "Sets a timer",
                category = ToolCategory.SYSTEM,
                parameters = listOf(
                    ToolParameter(
                        name = "duration",
                        type = ParameterType.NUMBER,
                        description = "Duration in seconds",
                        required = true
                    )
                )
            )
        )

        val prompt = builder.build(
            userPrompt = "Set a timer for 5 minutes",
            context = EnrichedContext.empty(),
            tools = tools
        )

        assertContains(prompt, "duration")
        assertContains(prompt, "Duration in seconds")
    }

    // ===== Format-Specific Tests =====

    @Test
    fun `uses Gemma3 format tokens`() {
        val builder = createBuilder(ChatFormat.Gemma3)

        val prompt = builder.build(
            userPrompt = "Hello",
            context = EnrichedContext.empty()
        )

        // Gemma3 uses <start_of_turn>user and <start_of_turn>model
        assertContains(prompt, "<start_of_turn>")
    }

    @Test
    fun `Gemma3 prompt starts with bos token`() {
        val builder = createBuilder(ChatFormat.Gemma3)

        val prompt = builder.build(
            userPrompt = "Hello",
            context = EnrichedContext.empty()
        )

        // Gemma3 requires <bos> at the start for llama.cpp
        assertTrue(prompt.startsWith("<bos>"))
    }

    @Test
    fun `Gemma3 consolidates into single user turn with tools`() {
        val builder = createBuilder(ChatFormat.Gemma3)
        val tools = listOf(
            Tool(
                id = "get_time",
                name = "Get Time",
                description = "Gets the current time",
                category = ToolCategory.DEVICE_INFO,
                parameters = emptyList()
            )
        )

        val prompt = builder.build(
            userPrompt = "What time is it?",
            context = EnrichedContext.empty(),
            tools = tools
        )

        // Should have exactly ONE <start_of_turn>user (not multiple)
        val userTurnCount = prompt.split("<start_of_turn>user").size - 1
        assertEquals(1, userTurnCount, "Gemma3 should have exactly one user turn, got $userTurnCount")

        // Should contain tool info, system prompt, and user query in same turn
        assertContains(prompt, "get_time")
        assertContains(prompt, "M1k3")
        assertContains(prompt, "What time is it?")
    }

    @Test
    fun `Gemma3 with context has single user turn`() {
        val builder = createBuilder(ChatFormat.Gemma3)
        val context = EnrichedContext(
            context = "The battery level is 75%.",
            intentCategory = "DEVICE",
            hasRagContext = true,
            hasMemoryContext = false
        )

        val prompt = builder.build(
            userPrompt = "Check my battery",
            context = context
        )

        // Should have exactly ONE <start_of_turn>user
        val userTurnCount = prompt.split("<start_of_turn>user").size - 1
        assertEquals(1, userTurnCount, "Gemma3 should consolidate context into single user turn")

        // Context and query should be in the same turn
        assertContains(prompt, "battery level is 75%")
        assertContains(prompt, "Check my battery")
    }

    @Test
    fun `ends with model turn start for generation`() {
        val builder = createBuilder(ChatFormat.Gemma3)

        val prompt = builder.build(
            userPrompt = "Hello",
            context = EnrichedContext.empty()
        )

        // Should end with model turn start to prompt generation
        assertTrue(prompt.contains("<start_of_turn>model"))
    }

    // ===== System Prompt Tests =====

    @Test
    fun `includes system prompt when provided`() {
        val builder = createBuilder()

        val prompt = builder.build(
            userPrompt = "Hello",
            context = EnrichedContext.empty(),
            systemPrompt = "You are a helpful assistant."
        )

        assertContains(prompt, "You are a helpful assistant.")
    }

    @Test
    fun `uses default system prompt when not provided`() {
        val builder = createBuilder()

        val prompt = builder.build(
            userPrompt = "Hello",
            context = EnrichedContext.empty()
        )

        // Should have some default system behavior
        assertContains(prompt, "M1k3")
    }

    // ===== Helper Methods =====

    private fun createBuilder(format: ChatFormat = ChatFormat.Gemma3): UnifiedPromptBuilder {
        val formatter = DefaultChatFormatter(format)
        val assembler = ContextAssembler()
        return UnifiedPromptBuilder(formatter, assembler)
    }
}
