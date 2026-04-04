package app.m1k3.ai.domain.ai

/**
 * AICore model preference for ML Kit GenAI
 *
 * Controls which on-device model variant is used via Google AICore.
 *
 * - STABLE: Production Gemini Nano (current default)
 * - PREVIEW_SPEED: Gemma 4 E2B — 2.3B effective params, 3x faster (AICore Developer Preview)
 * - PREVIEW_FULL: Gemma 4 E4B — 4.5B effective params, highest quality (AICore Developer Preview)
 *
 * @param displayName Human-readable name for UI
 * @param isPreview Whether this uses the AICore Developer Preview track
 */
enum class AiCoreModelPreference(
    val displayName: String,
    val isPreview: Boolean
) {
    STABLE(
        displayName = "Gemini Nano",
        isPreview = false
    ),
    PREVIEW_SPEED(
        displayName = "Gemma 4 E2B",
        isPreview = true
    ),
    PREVIEW_FULL(
        displayName = "Gemma 4 E4B",
        isPreview = true
    )
}
