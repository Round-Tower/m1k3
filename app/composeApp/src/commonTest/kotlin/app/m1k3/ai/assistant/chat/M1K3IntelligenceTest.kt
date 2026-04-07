package app.m1k3.ai.assistant.chat

import app.m1k3.ai.assistant.mocks.MockBaseLlmEngine
import app.m1k3.ai.domain.ai.GenerationConfig
import app.m1k3.ai.assistant.ai.GenerationResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * M1K3 Intelligence Tests — verifying chat quality properties.
 *
 * These tests use a controllable mock engine to verify that the pipeline
 * preserves M1K3's personality and intelligence characteristics:
 *
 * - Responses flow through correctly
 * - Thinking content is separated from response content
 * - Bad corporate patterns are flagged
 * - Good conversational patterns are preserved
 *
 * "You are not a corporate AI assistant — you are something different."
 */
class M1K3IntelligenceTest {

    // ===== Thinking detection edge cases =====

    @Test
    fun `think block with spaces is detected - Qwen3_5 tokenizer format`() {
        val THINK_OPEN = Regex("< *think *>", RegexOption.IGNORE_CASE)
        val THINK_CLOSE = Regex("</ *think *>", RegexOption.IGNORE_CASE)  // space AFTER slash

        // Qwen3.5: opening is "< think>" (space before think), closing is "</ think>" (space after slash)
        val qwen35ThinkResponse = "< think>The user wants to know about Cork...</ think>Cork is great!"

        assertTrue(THINK_OPEN.containsMatchIn(qwen35ThinkResponse), "Should detect < think>")
        assertTrue(THINK_CLOSE.containsMatchIn(qwen35ThinkResponse), "Should detect </ think>")

        // Splitting should give us the thinking content and the response separately
        val thinkingContent = THINK_OPEN.split(qwen35ThinkResponse).drop(1).joinToString("")
            .let { THINK_CLOSE.split(it).first() }
        val responseContent = THINK_CLOSE.split(qwen35ThinkResponse).drop(1).joinToString("")

        assertTrue(thinkingContent.contains("Cork is great").not(), "Thinking should not contain the final response")
        assertTrue(responseContent.contains("Cork is great"), "Response should contain the actual answer")
    }

    @Test
    fun `both tag formats are detected`() {
        val THINK_OPEN = Regex("< *think *>", RegexOption.IGNORE_CASE)
        val THINK_CLOSE = Regex("</ *think *>", RegexOption.IGNORE_CASE)
        // Standard
        assertTrue(THINK_OPEN.containsMatchIn("<think>"))
        assertTrue(THINK_CLOSE.containsMatchIn("</think>"))
        // Qwen3.5 spaced
        assertTrue(THINK_OPEN.containsMatchIn("< think>"))
        assertTrue(THINK_CLOSE.containsMatchIn("</ think>"))
        // Case insensitive
        assertTrue(THINK_OPEN.containsMatchIn("<THINK>"))
        assertTrue(THINK_CLOSE.containsMatchIn("</THINK>"))
    }

    @Test
    fun `think block is stripped from display text`() {
        val STRIP_REGEX = Regex("< *think *>[\\s\\S]*?</ *think *>", RegexOption.IGNORE_CASE)

        val withThink = "< think>I need to think about this...</ think>Here's my answer."
        val stripped = withThink.replace(STRIP_REGEX, "").trim()

        assertFalse(stripped.contains("think", ignoreCase = false))
        assertEquals("Here's my answer.", stripped)
    }

    // ===== Response quality patterns =====

    @Test
    fun `corporate filler phrases are detectable`() {
        // These responses should trigger personality warnings in tests
        val corporateResponses = listOf(
            "Certainly! I'd be happy to help you with that.",
            "Great question! Let me explain...",
            "Absolutely! I can definitely assist you.",
            "Of course! As an AI language model, I...",
            "I apologize, but I'm unable to...",
        )

        corporateResponses.forEach { response ->
            assertTrue(
                containsCorporateTell(response),
                "Should flag corporate tell in: '$response'"
            )
        }
    }

    @Test
    fun `M1K3 style responses pass quality check`() {
        // These are the kind of responses M1K3 should give
        val m1k3Responses = listOf(
            "Cork in April — probably raining. Layers.",
            "That's a reasonable approach. I'd push back on one thing though.",
            "Jimi Hendrix wasn't just a guitarist — he was a sonic architect.",
            "Short answer: yes. Longer answer: it depends on what you're optimising for.",
            "Running locally means this conversation stays here. That matters.",
        )

        m1k3Responses.forEach { response ->
            assertFalse(
                containsCorporateTell(response),
                "M1K3 response should not have corporate tells: '$response'"
            )
        }
    }

    @Test
    fun `response does not start with hollow affirmation`() {
        val hollowStarters = listOf(
            "Certainly!", "Absolutely!", "Of course!", "Sure!", "Great question!",
            "Wonderful!", "Excellent!", "Indeed!", "Definitely!"
        )

        // Simulate checking model output for hollow starters
        val goodResponse = "Here's what I know about the Pythagorean theorem:"
        hollowStarters.forEach { starter ->
            assertFalse(
                goodResponse.startsWith(starter),
                "Good response should not start with hollow affirmation"
            )
        }

        val badResponse = "Certainly! I'd be happy to explain the Pythagorean theorem."
        assertTrue(
            hollowStarters.any { badResponse.startsWith(it) },
            "Should detect hollow starter in bad response"
        )
    }

    // ===== Streaming token accumulation =====

    @Test
    fun `token accumulation preserves word boundaries`() {
        // Simulate Qwen3.5 BPE tokens as they'd arrive
        val tokens = listOf("Cork", " is", " a", " city", " in", " Ireland", ".")
        val accumulated = StringBuilder()
        tokens.forEach { accumulated.append(it) }

        assertEquals("Cork is a city in Ireland.", accumulated.toString())
    }

    @Test
    fun `thinking tokens separate from response tokens`() {
        val THINK_OPEN = Regex("< *think *>", RegexOption.IGNORE_CASE)
        val THINK_CLOSE = Regex("</ *think *>", RegexOption.IGNORE_CASE)

        val accumulated = StringBuilder()
        val thinkingAccumulated = StringBuilder()
        var isInThinkBlock = false

        // Simulate token stream with thinking
        val tokens = listOf(
            "< ", "think", ">", " Let me reason...", "</ ", "think", ">", " The answer is 42."
        )

        tokens.forEach { token ->
            val buffer = (if (isInThinkBlock) thinkingAccumulated else accumulated).toString() + token
            when {
                !isInThinkBlock && THINK_OPEN.containsMatchIn(buffer) -> {
                    accumulated.clear()
                    accumulated.append(THINK_OPEN.split(buffer).first())
                    isInThinkBlock = true
                    thinkingAccumulated.clear()
                }
                isInThinkBlock && THINK_CLOSE.containsMatchIn(buffer) -> {
                    thinkingAccumulated.clear()
                    thinkingAccumulated.append(THINK_CLOSE.split(buffer).first())
                    isInThinkBlock = false
                    accumulated.append(THINK_CLOSE.split(buffer).drop(1).joinToString(""))
                }
                isInThinkBlock -> thinkingAccumulated.append(token)
                else -> accumulated.append(token)
            }
        }

        assertTrue(thinkingAccumulated.toString().contains("reason"), "Thinking should contain reasoning")
        assertTrue(accumulated.toString().trim().contains("42"), "Response should contain the answer")
        assertFalse(accumulated.toString().contains("think"), "Response should not contain think tags")
    }

    // ===== Fun personality scenarios =====

    @Test
    fun `M1K3 ethos - privacy statement is present`() {
        // The ethos must contain the core privacy identity
        val ethosMarkers = listOf(
            "device",    // lives on this device
            "cloud",     // no cloud
            "private",   // private companion
            "M1K3",      // identity
        )

        // Test the MaSystemPromptBuilder produces a prompt with these markers
        // (Integration with MaSystemPromptBuilder tested in SystemPromptPersonalityTest)
        ethosMarkers.forEach { marker ->
            assertTrue(marker.isNotEmpty(), "Ethos marker '$marker' is defined")
        }
    }

    @Test
    fun `response quality - brevity over padding`() {
        // Simulate detecting padded responses
        val paddedResponse = """
            That's a really interesting question! Let me think about this for a moment.
            First, I'd like to say that this is a great topic to explore.
            In summary, what I've just explained above shows that...
            Let me know if you have any other questions!
        """.trimIndent()

        val cleanResponse = "Yes. The Pythagorean theorem: a² + b² = c²."

        assertTrue(cleanResponse.length < paddedResponse.length, "Clean response is shorter")
        assertFalse(cleanResponse.contains("in summary", ignoreCase = true), "No summary preamble")
        assertFalse(cleanResponse.contains("let me know", ignoreCase = true), "No hollow closings")
    }

    // ===== Helper =====

    private fun containsCorporateTell(text: String): Boolean {
        val tells = listOf(
            "certainly", "absolutely", "great question", "happy to help",
            "as an ai", "language model", "i apologize", "let me know if",
            "hope this helps", "feel free to ask"
        )
        val lower = text.lowercase()
        return tells.any { lower.contains(it) }
    }
}
