package app.m1k3.ai.assistant.ai.ondevice

/**
 * Represents the availability status of on-device AI.
 *
 * This sealed class models the different states that platform-native AI engines
 * (ML Kit GenAI on Android, Foundation Models on iOS) can be in.
 *
 * @see AiAvailability.Available - AI is ready for inference
 * @see AiAvailability.Downloading - AI model is being downloaded
 * @see AiAvailability.Unavailable - AI is not available (with reason)
 * @see AiAvailability.Fallback - Primary AI unavailable, using fallback engine
 */
sealed class AiAvailability {

    /**
     * On-device AI is fully available and ready for inference.
     * This is the happy path state.
     */
    data object Available : AiAvailability()

    /**
     * AI model is currently being downloaded.
     * UI should show a progress indicator and wait for completion.
     */
    data object Downloading : AiAvailability()

    /**
     * On-device AI is not available on this device.
     *
     * @property reason The specific reason why AI is unavailable
     */
    data class Unavailable(val reason: UnavailableReason) : AiAvailability()

    /**
     * Primary on-device AI is not available, but a fallback engine is being used.
     * This allows the app to function on older devices using bundled models.
     *
     * @property engineName The name of the fallback engine (e.g., "SmolLM2-135M")
     */
    data class Fallback(val engineName: String) : AiAvailability()

    /**
     * Reasons why on-device AI might be unavailable.
     */
    enum class UnavailableReason {
        /**
         * Device hardware doesn't support on-device AI.
         * Examples: older chipsets, insufficient RAM
         */
        DEVICE_NOT_SUPPORTED,

        /**
         * AI model is not yet ready (still initializing or downloading).
         */
        MODEL_NOT_READY,

        /**
         * User has disabled AI features in system settings.
         * On iOS: Apple Intelligence not enabled
         * On Android: AI Core disabled
         */
        AI_DISABLED,

        /**
         * Rate limiting - too many requests in a short time.
         */
        QUOTA_EXCEEDED,

        /**
         * AI inference blocked because app is in background.
         * Some platforms restrict AI to foreground use only.
         */
        BACKGROUND_BLOCKED,

        /**
         * Unknown or unspecified reason.
         */
        UNKNOWN
    }
}
