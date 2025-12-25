package app.m1k3.ai.assistant.ai.ondevice

/**
 * Summary style options for the summarize() function.
 *
 * Maps to platform-specific summarization options:
 * - Android ML Kit: OutputType (ONE_BULLET, THREE_BULLETS, PARAGRAPH)
 * - iOS Foundation Models: Prompt engineering with style instructions
 */
enum class SummaryStyle {
    /**
     * Brief summary in 1-2 sentences.
     * Best for quick overviews.
     */
    BRIEF,

    /**
     * Summary as 3-5 bullet points.
     * Best for key takeaways.
     */
    BULLETS,

    /**
     * Detailed paragraph summary.
     * Best for comprehensive understanding.
     */
    DETAILED
}
