package app.m1k3.ai.assistant.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for Gemma3PromptBuilder.
 *
 * Validates that prompts are correctly formatted for Gemma 3 models,
 * which only support `user` and `model` roles (NO system role).
 */
class Gemma3PromptBuilderTest {

    // === Basic Format Tests ===

    @Test
    fun `build creates valid Gemma 3 format`() {
        val prompt = Gemma3PromptBuilder.build("Hello")

        assertTrue(Gemma3PromptBuilder.isValidFormat(prompt))
    }

    @Test
    fun `build starts with bos token`() {
        val prompt = Gemma3PromptBuilder.build("Hello")

        assertTrue(prompt.startsWith("<bos>"))
    }

    @Test
    fun `build ends with model turn start`() {
        val prompt = Gemma3PromptBuilder.build("Hello")

        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun `build includes user query`() {
        val query = "What is AI?"
        val prompt = Gemma3PromptBuilder.build(query)

        assertTrue(prompt.contains(query))
    }

    @Test
    fun `build trims user query whitespace`() {
        val prompt = Gemma3PromptBuilder.build("  Hello World  ")

        assertTrue(prompt.contains("Hello World"))
        assertFalse(prompt.contains("  Hello"))
    }

    // === Context/RAG Tests ===

    @Test
    fun `build includes context when provided`() {
        val context = "AI stands for Artificial Intelligence."
        val prompt = Gemma3PromptBuilder.build("What is AI?", context)

        assertTrue(prompt.contains("Facts:"))
        assertTrue(prompt.contains(context))
    }

    @Test
    fun `build places context before user query`() {
        val context = "Fact about AI"
        val query = "What is AI?"
        val prompt = Gemma3PromptBuilder.build(query, context)

        val contextIndex = prompt.indexOf(context)
        val queryIndex = prompt.indexOf(query)
        assertTrue(contextIndex < queryIndex, "Context should appear before query")
    }

    @Test
    fun `build excludes empty context`() {
        val prompt = Gemma3PromptBuilder.build("Hello", "")

        assertFalse(prompt.contains("Facts:"))
    }

    @Test
    fun `build excludes blank context`() {
        val prompt = Gemma3PromptBuilder.build("Hello", "   ")

        assertFalse(prompt.contains("Facts:"))
    }

    @Test
    fun `build excludes context with 0 facts marker`() {
        val prompt = Gemma3PromptBuilder.build("Hello", "Retrieved 0 facts")

        assertFalse(prompt.contains("Facts:"))
    }

    @Test
    fun `build trims context whitespace`() {
        val prompt = Gemma3PromptBuilder.build("Hello", "  Some context  ")

        assertTrue(prompt.contains("Some context"))
        assertFalse(prompt.contains("  Some context"))
    }

    // === No System Role Tests (Critical for Gemma 3!) ===

    @Test
    fun `build never includes system role`() {
        val prompt = Gemma3PromptBuilder.build("Hello", "Some context")

        assertFalse(prompt.contains("<start_of_turn>system"))
        assertFalse(prompt.contains("system"))
    }

    @Test
    fun `buildWithInstruction never includes system role`() {
        val prompt = Gemma3PromptBuilder.buildWithInstruction(
            "Hello",
            "Context",
            "Answer briefly"
        )

        assertFalse(prompt.contains("<start_of_turn>system"))
    }

    // === Instruction Tests ===

    @Test
    fun `buildWithInstruction includes instruction`() {
        val instruction = "Answer in one sentence"
        val prompt = Gemma3PromptBuilder.buildWithInstruction(
            "What is AI?",
            null,
            instruction
        )

        assertTrue(prompt.contains(instruction))
    }

    @Test
    fun `buildWithInstruction places instruction first`() {
        val instruction = "Be brief"
        val query = "What is AI?"
        val prompt = Gemma3PromptBuilder.buildWithInstruction(query, null, instruction)

        val instructionIndex = prompt.indexOf(instruction)
        val queryIndex = prompt.indexOf(query)
        assertTrue(instructionIndex < queryIndex, "Instruction should appear before query")
    }

    @Test
    fun `buildWithInstruction orders correctly - instruction, context, query`() {
        val instruction = "Answer briefly"
        val context = "AI is artificial intelligence"
        val query = "Explain AI"
        val prompt = Gemma3PromptBuilder.buildWithInstruction(query, context, instruction)

        val instructionIdx = prompt.indexOf(instruction)
        val contextIdx = prompt.indexOf(context)
        val queryIdx = prompt.indexOf(query)

        assertTrue(instructionIdx < contextIdx, "Instruction before context")
        assertTrue(contextIdx < queryIdx, "Context before query")
    }

    // === Validation Tests ===

    @Test
    fun `isValidFormat rejects prompt without bos`() {
        val badPrompt = "<start_of_turn>user\nHello<end_of_turn>\n<start_of_turn>model\n"

        assertFalse(Gemma3PromptBuilder.isValidFormat(badPrompt))
    }

    @Test
    fun `isValidFormat rejects prompt with system role`() {
        val badPrompt = "<bos><start_of_turn>system\nYou are helpful<end_of_turn>\n<start_of_turn>user\nHi<end_of_turn>\n<start_of_turn>model\n"

        assertFalse(Gemma3PromptBuilder.isValidFormat(badPrompt))
    }

    @Test
    fun `isValidFormat rejects prompt without end turn`() {
        val badPrompt = "<bos><start_of_turn>user\nHello<start_of_turn>model\n"

        assertFalse(Gemma3PromptBuilder.isValidFormat(badPrompt))
    }

    @Test
    fun `isValidFormat rejects prompt not ending with model turn`() {
        val badPrompt = "<bos><start_of_turn>user\nHello<end_of_turn>\n"

        assertFalse(Gemma3PromptBuilder.isValidFormat(badPrompt))
    }

    // === Stop Token Tests ===

    @Test
    fun `getStopTokens includes end_of_turn`() {
        val tokens = Gemma3PromptBuilder.getStopTokens()

        assertTrue(tokens.contains("<end_of_turn>"))
    }

    @Test
    fun `getStopTokens includes eos`() {
        val tokens = Gemma3PromptBuilder.getStopTokens()

        assertTrue(tokens.contains("<eos>"))
    }

    @Test
    fun `getStopTokens does not include ChatML tokens`() {
        val tokens = Gemma3PromptBuilder.getStopTokens()

        assertFalse(tokens.any { it.contains("im_end") })
        assertFalse(tokens.any { it.contains("endoftext") })
    }

    // === Real-World Scenario Tests ===

    @Test
    fun `build handles educational query with RAG context`() {
        val context = """
            AI (Artificial Intelligence) is the simulation of human intelligence by machines.
            Machine Learning is a subset of AI that learns from data.
        """.trimIndent()
        val query = "Teach me about AI"

        val prompt = Gemma3PromptBuilder.build(query, context)

        assertTrue(Gemma3PromptBuilder.isValidFormat(prompt))
        assertTrue(prompt.contains("AI"))
        assertTrue(prompt.contains("Machine Learning"))
        assertTrue(prompt.contains("Teach me"))
    }

    @Test
    fun `build handles simple greeting without context`() {
        val prompt = Gemma3PromptBuilder.build("Hello, how are you?")

        assertTrue(Gemma3PromptBuilder.isValidFormat(prompt))
        assertFalse(prompt.contains("Facts:"))
    }

    @Test
    fun `build handles multi-line context`() {
        val context = """
            Fact 1: AI is artificial intelligence.
            Fact 2: ML is machine learning.
            Fact 3: Deep learning uses neural networks.
        """.trimIndent()

        val prompt = Gemma3PromptBuilder.build("What are these technologies?", context)

        assertTrue(prompt.contains("Fact 1"))
        assertTrue(prompt.contains("Fact 2"))
        assertTrue(prompt.contains("Fact 3"))
    }

    @Test
    fun `build produces consistent output for same input`() {
        val prompt1 = Gemma3PromptBuilder.build("Hello", "Context")
        val prompt2 = Gemma3PromptBuilder.build("Hello", "Context")

        assertEquals(prompt1, prompt2)
    }
}
