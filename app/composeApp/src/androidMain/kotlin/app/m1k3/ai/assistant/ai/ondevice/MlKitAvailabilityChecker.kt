package app.m1k3.ai.assistant.ai.ondevice

import android.content.Context
import android.os.Build
import co.touchlab.kermit.Logger
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 */
interface MlKitAvailabilityChecker {
    /**
     * Check if ML Kit GenAI (Gemini Nano) is available on this device.
     *
     * @return true if ML Kit GenAI can be used, false otherwise
     */
    suspend fun isGenAiAvailable(): Boolean

    /**
     * Check if AICore Developer Preview (Gemma 4) is available.
     *
     * @return true if AICore Preview track is available, false otherwise
     */
    suspend fun isAiCorePreviewAvailable(): Boolean = false
}

/**
 * Default implementation that returns false (ML Kit GenAI not yet integrated).
 *
 * This is a placeholder for testing. In production, use RealMlKitAvailabilityChecker.
 */
class DefaultMlKitAvailabilityChecker : MlKitAvailabilityChecker {
    override suspend fun isGenAiAvailable(): Boolean = false
}

/**
 * Real implementation that checks ML Kit GenAI availability.
 *
 * Uses the ML Kit GenAI Prompt API to check if Gemini Nano is available on the device.
 *
 * ## Device Requirements
 * - **Android 14+** (API 34+) with Google Play AI Core
 * - **Tensor G3+** (Pixel 8 and newer)
 * - **Snapdragon 8 Gen 3+** (Samsung S24 and newer)
 * - **Dimensity 9300+** (select MediaTek devices)
 * - **Bootloader must be locked** (unlocked bootloaders not supported)
 *
 * ## Usage
 * ```kotlin
 * val checker = RealMlKitAvailabilityChecker(context)
 * if (checker.isGenAiAvailable()) {
 *     // Use ML Kit GenAI
 * } else {
 *     // Fall back to LlamaCpp
 * }
 * ```
 *
 * @param context Android context for ML Kit initialization
 */
class RealMlKitAvailabilityChecker(
    @Suppress("unused") private val context: Context
) : MlKitAvailabilityChecker {

    private val logger = Logger.withTag("RealMlKitAvailabilityChecker")

    /**
     * Check if ML Kit GenAI (Gemini Nano) is available on this device.
     *
     * This performs several checks:
     * 1. Android version (requires API 34+ / Android 14+)
     * 2. ML Kit GenAI feature status (AVAILABLE or DOWNLOADABLE)
     *
     * @return true if ML Kit GenAI can be used, false otherwise
     */
    override suspend fun isGenAiAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check Android version - ML Kit GenAI requires Android 14+ (API 34)
            if (Build.VERSION.SDK_INT < 34) {
                logger.d { "Device running Android ${Build.VERSION.SDK_INT}, ML Kit GenAI requires API 34+" }
                return@withContext false
            }

            // Check ML Kit GenAI availability using the Prompt API
            val generativeModel = Generation.getClient()
            // checkStatus() is a suspend function that returns @FeatureStatus Int
            val status = generativeModel.checkStatus()

            when (status) {
                FeatureStatus.AVAILABLE -> {
                    logger.i { "ML Kit GenAI is available and ready" }
                    true
                }
                FeatureStatus.DOWNLOADABLE -> {
                    logger.i { "ML Kit GenAI is downloadable (model not yet cached)" }
                    true // We can download it, so consider it available
                }
                FeatureStatus.DOWNLOADING -> {
                    logger.i { "ML Kit GenAI model is currently downloading" }
                    true // Download in progress, consider it available
                }
                else -> {
                    logger.w { "ML Kit GenAI unavailable, status: $status" }
                    false
                }
            }
        } catch (e: Exception) {
            logger.w(e) { "Error checking ML Kit GenAI availability: ${e.message}" }
            false
        }
    }

    /**
     * Check if AICore Developer Preview (Gemma 4) is available.
     *
     * TODO: Enable once mlkit-genai-prompt dependency supports ModelConfig API.
     * Will use Generation.getClient(previewConfig).checkStatus() to probe
     * whether the PREVIEW release track is available on this device.
     *
     * @return true if AICore Preview is available, false otherwise
     */
    override suspend fun isAiCorePreviewAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Requires API 34+ minimum
            if (Build.VERSION.SDK_INT < 34) return@withContext false

            val previewConfig = com.google.mlkit.genai.prompt.generationConfig {
                modelConfig = com.google.mlkit.genai.prompt.modelConfig {
                    releaseStage = com.google.mlkit.genai.prompt.ModelReleaseStage.PREVIEW
                    preference = com.google.mlkit.genai.prompt.ModelPreference.FAST
                }
            }
            val model = Generation.getClient(previewConfig)
            val status = model.checkStatus()

            val available = status == FeatureStatus.AVAILABLE ||
                status == FeatureStatus.DOWNLOADABLE ||
                status == FeatureStatus.DOWNLOADING

            logger.i { "AICore Preview available: $available (status: $status)" }
            available
        } catch (e: Exception) {
            logger.w(e) { "Error checking AICore Preview: ${e.message}" }
            false
        }
    }
}
