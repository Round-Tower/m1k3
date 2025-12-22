package app.m1k3.ai.assistant.ai.ondevice

/**
 * Standardized error codes for on-device AI operations.
 *
 * These codes map to platform-specific errors from ML Kit and Foundation Models,
 * providing a consistent error handling interface across platforms.
 */
enum class AiErrorCode {
    /**
     * AI service is not available on this device or in current state.
     */
    UNAVAILABLE,

    /**
     * AI service is busy processing another request.
     * Retry with exponential backoff.
     */
    BUSY,

    /**
     * Rate limit exceeded. Too many requests in a short time.
     */
    QUOTA_EXCEEDED,

    /**
     * Content was filtered due to safety policies.
     * The prompt or response violated content guidelines.
     */
    CONTENT_FILTERED,

    /**
     * Input text exceeds the model's token limit.
     * Reduce the prompt length and try again.
     */
    INPUT_TOO_LONG,

    /**
     * AI inference is blocked in background mode.
     * Some platforms only allow AI in foreground.
     */
    BACKGROUND_BLOCKED,

    /**
     * Request was cancelled by the user or system.
     */
    CANCELLED,

    /**
     * Unknown or unspecified error.
     */
    UNKNOWN
}
