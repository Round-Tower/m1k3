package app.m1k3.ai.assistant.ai.ondevice

/**
 * Interface for checking ML Kit GenAI availability.
 *
 * This abstraction allows us to:
 * 1. Test AndroidOnDeviceAi without actual ML Kit dependencies
 * 2. Support different availability checking strategies
 * 3. Eventually integrate with actual ML Kit GenAI SDK
 *
 * ## Device Requirements (ML Kit GenAI)
 * - **Android 14+** with Google Play AI Core
 * - **Tensor G3+** (Pixel 8 and newer)
 * - **Snapdragon 8 Gen 3+** (Samsung S24 and newer)
 * - **Dimensity 9300+** (select MediaTek devices)
 *
 * ## Future Implementation
 * When ML Kit GenAI leaves alpha, this will check:
 * - `GenerativeModel.checkFeatureStatus()` returns `AVAILABLE`
 * - Device meets hardware requirements
 * - Model is downloaded and ready
 */
interface MlKitAvailabilityChecker {
    /**
     * Check if ML Kit GenAI (Gemini Nano) is available on this device.
     *
     * @return true if ML Kit GenAI can be used, false otherwise
     */
    suspend fun isGenAiAvailable(): Boolean
}

/**
 * Default implementation that returns false (ML Kit GenAI not yet integrated).
 *
 * This is a placeholder until ML Kit GenAI SDK is stable and integrated.
 * When ML Kit GenAI is ready, this will be replaced with actual SDK checks.
 */
class DefaultMlKitAvailabilityChecker : MlKitAvailabilityChecker {
    /**
     * Currently always returns false since ML Kit GenAI is not yet integrated.
     *
     * TODO: Implement actual ML Kit GenAI availability check when SDK is stable
     * ```kotlin
     * val generativeModel = GenerativeModel.newBuilder()
     *     .setModelName("gemini-nano")
     *     .build()
     * return generativeModel.checkFeatureStatus() == FeatureStatus.AVAILABLE
     * ```
     */
    override suspend fun isGenAiAvailable(): Boolean = false
}
