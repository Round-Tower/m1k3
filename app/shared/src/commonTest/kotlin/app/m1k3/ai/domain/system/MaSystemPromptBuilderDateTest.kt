package app.m1k3.ai.domain.system

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Dedicated tests for injecting the current date into the system prompt.
 *
 * Context: Qwen3.5 0.8B on 2026-04-19 was emitting `web_search` queries like
 * `"latest news from United States 2024"` — defaulting to its training-cutoff
 * year because nothing in the prompt anchored "today." Pixel 9a logcat
 * confirmed the hallucination. Fix: pass the current date through
 * SystemPromptInput.currentDate so the model always knows what year it is.
 */
class MaSystemPromptBuilderDateTest {
    private val builder = MaSystemPromptBuilder()

    @Test
    fun `full prompt includes current date when provided`() {
        val prompt =
            builder.build(
                SystemPromptInput(
                    tier = SystemPromptTier.FULL,
                    currentDate = "April 19, 2026",
                ),
            )

        assertTrue(
            prompt.contains("April 19, 2026"),
            "FULL prompt should mention the current date. Got:\n$prompt",
        )
    }

    @Test
    fun `compact prompt includes current date when provided`() {
        val prompt =
            builder.build(
                SystemPromptInput(
                    tier = SystemPromptTier.COMPACT,
                    currentDate = "April 19, 2026",
                ),
            )

        assertTrue(
            prompt.contains("April 19, 2026"),
            "COMPACT prompt should mention the current date. Got:\n$prompt",
        )
    }

    @Test
    fun `full prompt omits date section when currentDate is null`() {
        val prompt =
            builder.build(
                SystemPromptInput(
                    tier = SystemPromptTier.FULL,
                    currentDate = null,
                ),
            )

        assertFalse(
            prompt.contains("Today is", ignoreCase = true),
            "FULL prompt must not invent a date when none is provided. Got:\n$prompt",
        )
    }

    @Test
    fun `compact prompt omits date section when currentDate is null`() {
        val prompt =
            builder.build(
                SystemPromptInput(
                    tier = SystemPromptTier.COMPACT,
                    currentDate = null,
                ),
            )

        assertFalse(
            prompt.contains("Today is", ignoreCase = true),
            "COMPACT prompt must not invent a date when none is provided. Got:\n$prompt",
        )
    }
}
