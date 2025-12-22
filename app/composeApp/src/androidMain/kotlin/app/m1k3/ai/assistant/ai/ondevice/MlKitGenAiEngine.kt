package app.m1k3.ai.assistant.ai.ondevice

import app.m1k3.ai.assistant.ai.GenerationConfig
import kotlinx.coroutines.flow.Flow

/**
 * Interface for ML Kit GenAI engine operations.
 *
 * This interface mirrors OnDeviceAi but is specific to ML Kit GenAI (Gemini Nano).
 * It allows us to:
 * 1. Test AndroidOnDeviceAi without actual ML Kit dependencies
 * 2. Swap implementations for different ML Kit versions
 * 3. Eventually integrate with actual ML Kit GenAI SDK
 *
 * ## ML Kit GenAI Features (when available)
 * - **Gemini Nano** - On-device LLM optimized for mobile
 * - **Prompt API** - General text generation
 * - **Summarization API** - Built-in summarization with styles
 * - **Proofreading API** - Grammar and style correction
 *
 * ## Current Status
 * ML Kit GenAI is in alpha (1.0.0-alpha1 as of Dec 2024).
 * This interface is designed for future integration.
 */
interface MlKitGenAiEngine {
    /**
     * Check if the ML Kit model is available and ready.
     */
    suspend fun checkAvailability(): AiAvailability

    /**
     * Download/prepare the model if needed.
     */
    suspend fun downloadModelIfNeeded(): AiResult<Unit>

    /**
     * Generate text from a prompt using Gemini Nano.
     */
    suspend fun generate(prompt: String, config: GenerationConfig): AiResult<String>

    /**
     * Generate text with streaming tokens.
     */
    fun generateStream(prompt: String, config: GenerationConfig): Flow<AiResult<String>>

    /**
     * Summarize text using ML Kit's Summarization API.
     *
     * ML Kit has built-in summarization styles:
     * - ONE_BULLET → BRIEF
     * - THREE_BULLETS → BULLETS
     * - PARAGRAPH → DETAILED
     */
    suspend fun summarize(text: String, style: SummaryStyle): AiResult<String>

    /**
     * Get model information.
     */
    suspend fun getModelInfo(): String

    /**
     * Release resources.
     */
    fun release()
}

/**
 * Stub implementation that returns unavailable.
 *
 * This is used when ML Kit GenAI is not integrated yet.
 * When ML Kit GenAI SDK is stable, this will be replaced with real implementation.
 */
class StubMlKitGenAiEngine : MlKitGenAiEngine {
    override suspend fun checkAvailability(): AiAvailability =
        AiAvailability.Unavailable(AiAvailability.UnavailableReason.DEVICE_NOT_SUPPORTED)

    override suspend fun downloadModelIfNeeded(): AiResult<Unit> =
        AiResult.Error(AiErrorCode.UNAVAILABLE, "ML Kit GenAI not integrated yet")

    override suspend fun generate(prompt: String, config: GenerationConfig): AiResult<String> =
        AiResult.Error(AiErrorCode.UNAVAILABLE, "ML Kit GenAI not integrated yet")

    override fun generateStream(prompt: String, config: GenerationConfig): Flow<AiResult<String>> =
        kotlinx.coroutines.flow.flowOf(
            AiResult.Error(AiErrorCode.UNAVAILABLE, "ML Kit GenAI not integrated yet")
        )

    override suspend fun summarize(text: String, style: SummaryStyle): AiResult<String> =
        AiResult.Error(AiErrorCode.UNAVAILABLE, "ML Kit GenAI not integrated yet")

    override suspend fun getModelInfo(): String = "ML Kit GenAI (not integrated)"

    override fun release() {
        // Nothing to release in stub
    }
}
