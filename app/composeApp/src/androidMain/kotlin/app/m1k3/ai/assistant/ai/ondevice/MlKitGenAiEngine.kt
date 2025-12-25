package app.m1k3.ai.assistant.ai.ondevice

import android.content.Context
import app.m1k3.ai.assistant.ai.GenerationConfig
import co.touchlab.kermit.Logger
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * Interface for ML Kit GenAI engine operations.
 *
 * This interface mirrors OnDeviceAi but is specific to ML Kit GenAI (Gemini Nano).
 * It allows us to:
 * 1. Test AndroidOnDeviceAi without actual ML Kit dependencies
 * 2. Swap implementations for different ML Kit versions
 * 3. Eventually integrate with actual ML Kit GenAI SDK
 *
 * ## ML Kit GenAI Features
 * - **Gemini Nano** - On-device LLM optimized for mobile
 * - **Prompt API** - General text generation (alpha)
 * - **Summarization API** - Built-in summarization with styles (beta)
 *
 * ## Device Requirements
 * - Android 14+ (API 34+)
 * - Pixel 8+, Samsung S24+, or equivalent
 * - Locked bootloader
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
 * This is used for testing when ML Kit GenAI is not available.
 */
class StubMlKitGenAiEngine : MlKitGenAiEngine {
    override suspend fun checkAvailability(): AiAvailability =
        AiAvailability.Unavailable(AiAvailability.UnavailableReason.DEVICE_NOT_SUPPORTED)

    override suspend fun downloadModelIfNeeded(): AiResult<Unit> =
        AiResult.Error(AiErrorCode.UNAVAILABLE, "ML Kit GenAI not integrated yet")

    override suspend fun generate(prompt: String, config: GenerationConfig): AiResult<String> =
        AiResult.Error(AiErrorCode.UNAVAILABLE, "ML Kit GenAI not integrated yet")

    override fun generateStream(prompt: String, config: GenerationConfig): Flow<AiResult<String>> =
        flowOf(AiResult.Error(AiErrorCode.UNAVAILABLE, "ML Kit GenAI not integrated yet"))

    override suspend fun summarize(text: String, style: SummaryStyle): AiResult<String> =
        AiResult.Error(AiErrorCode.UNAVAILABLE, "ML Kit GenAI not integrated yet")

    override suspend fun getModelInfo(): String = "ML Kit GenAI (not integrated)"

    override fun release() {
        // Nothing to release in stub
    }
}

/**
 * Real implementation of ML Kit GenAI engine using Gemini Nano.
 *
 * Provides on-device AI generation using Google's ML Kit GenAI Prompt API.
 *
 * ## Features
 * - **Prompt API** - General text generation with Gemini Nano
 * - **Streaming support** - Token-by-token generation for real-time UI updates
 *
 * ## Device Requirements
 * - Android 14+ (API 34+) with Google Play AI Core
 * - Pixel 8+, Samsung S24+, or equivalent chipset
 * - Locked bootloader (unlocked not supported)
 *
 * ## Usage
 * ```kotlin
 * val engine = RealMlKitGenAiEngine(context)
 *
 * // Check availability
 * when (engine.checkAvailability()) {
 *     is AiAvailability.Available -> { /* Ready */ }
 *     is AiAvailability.Downloading -> { /* Wait */ }
 *     else -> { /* Use fallback */ }
 * }
 *
 * // Generate text
 * val result = engine.generate("Explain quantum computing", GenerationConfig())
 * result.onSuccess { println(it) }
 * ```
 *
 * @param context Android context for ML Kit initialization
 */
class RealMlKitGenAiEngine(
    @Suppress("unused") private val context: Context
) : MlKitGenAiEngine {

    private val logger = Logger.withTag("RealMlKitGenAiEngine")

    // Lazily initialized ML Kit client
    private var generativeModel: GenerativeModel? = null

    /**
     * Check ML Kit GenAI availability status.
     *
     * @return Current availability state
     */
    override suspend fun checkAvailability(): AiAvailability = withContext(Dispatchers.IO) {
        try {
            val model = getOrCreateGenerativeModel()
            // checkStatus() is a suspend function returning @FeatureStatus Int
            val status = model.checkStatus()

            when (status) {
                FeatureStatus.AVAILABLE -> AiAvailability.Available
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> AiAvailability.Downloading
                else -> AiAvailability.Unavailable(AiAvailability.UnavailableReason.DEVICE_NOT_SUPPORTED)
            }
        } catch (e: Exception) {
            logger.w(e) { "Error checking ML Kit availability: ${e.message}" }
            AiAvailability.Unavailable(AiAvailability.UnavailableReason.UNKNOWN)
        }
    }

    /**
     * Download the ML Kit GenAI model if needed.
     *
     * @return Success if model is ready or download started, Error otherwise
     */
    override suspend fun downloadModelIfNeeded(): AiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val model = getOrCreateGenerativeModel()
            val status = model.checkStatus()

            when (status) {
                FeatureStatus.AVAILABLE -> {
                    logger.i { "ML Kit GenAI model already available" }
                    AiResult.Success(Unit)
                }
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                    logger.i { "Downloading ML Kit GenAI model..." }
                    // download() returns Flow<DownloadStatus>, collect it to completion
                    model.download().collect { downloadStatus ->
                        logger.d { "Download status: $downloadStatus" }
                    }
                    logger.i { "ML Kit GenAI model download complete" }
                    AiResult.Success(Unit)
                }
                else -> {
                    logger.w { "ML Kit GenAI not available, status: $status" }
                    AiResult.Error(AiErrorCode.UNAVAILABLE, "ML Kit GenAI not available on this device")
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Error downloading ML Kit model: ${e.message}" }
            AiResult.Error(AiErrorCode.UNAVAILABLE, "Download failed: ${e.message}")
        }
    }

    /**
     * Generate text using the Gemini Nano Prompt API.
     *
     * Uses the simple string-based API for broader compatibility.
     *
     * @param prompt The user prompt
     * @param config Generation configuration (not all options may be supported)
     * @return Generated text or error
     */
    override suspend fun generate(prompt: String, config: GenerationConfig): AiResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val model = getOrCreateGenerativeModel()

                // Use the simple string-based API
                // Note: ML Kit GenAI may not support all GenerationConfig options
                val response = model.generateContent(prompt)

                // Extract text from response
                val text = response.candidates.firstOrNull()?.text
                if (text != null) {
                    logger.d { "Generated ${text.length} characters" }
                    AiResult.Success(text)
                } else {
                    logger.w { "No text in ML Kit response" }
                    AiResult.Error(AiErrorCode.UNKNOWN, "No response generated")
                }
            } catch (e: Exception) {
                logger.e(e) { "Generation error: ${e.message}" }
                mapExceptionToAiResult(e)
            }
        }

    /**
     * Generate text with streaming tokens.
     *
     * Emits tokens as they are generated for real-time UI updates.
     *
     * @param prompt The user prompt
     * @param config Generation configuration
     * @return Flow emitting tokens or errors
     */
    override fun generateStream(prompt: String, config: GenerationConfig): Flow<AiResult<String>> =
        callbackFlow {
            try {
                val model = getOrCreateGenerativeModel()

                // Use the Flow-based streaming API with simple string prompt
                model.generateContentStream(prompt).collect { response ->
                    val text = response.candidates.firstOrNull()?.text
                    if (text != null) {
                        trySend(AiResult.Success(text))
                    }
                }

                logger.d { "Streaming generation complete" }
            } catch (e: Exception) {
                logger.e(e) { "Streaming error: ${e.message}" }
                trySend(mapExceptionToAiResult(e))
            }

            awaitClose {
                logger.d { "Streaming flow closed" }
            }
        }

    /**
     * Summarize text using prompt engineering.
     *
     * Note: ML Kit's Summarization API uses a different builder pattern
     * that requires testing on a real device. For now, we use prompt engineering
     * to achieve summarization via the Prompt API.
     *
     * @param text Text to summarize
     * @param style Summary style (BRIEF, BULLETS, DETAILED)
     * @return Summarized text or error
     */
    override suspend fun summarize(text: String, style: SummaryStyle): AiResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val model = getOrCreateGenerativeModel()

                // Build summarization prompt based on style
                val summaryPrompt = when (style) {
                    SummaryStyle.BRIEF ->
                        "Summarize the following text in 1-2 sentences:\n\n$text"
                    SummaryStyle.BULLETS ->
                        "Summarize the following text as 3-5 bullet points:\n\n$text"
                    SummaryStyle.DETAILED ->
                        "Provide a detailed paragraph summary of the following text:\n\n$text"
                }

                val response = model.generateContent(summaryPrompt)

                val summary = response.candidates.firstOrNull()?.text
                if (summary != null) {
                    logger.d { "Summarized to ${summary.length} characters" }
                    AiResult.Success(summary)
                } else {
                    AiResult.Error(AiErrorCode.UNKNOWN, "No summary generated")
                }
            } catch (e: Exception) {
                logger.e(e) { "Summarization error: ${e.message}" }
                mapExceptionToAiResult(e)
            }
        }

    /**
     * Get model information string.
     */
    override suspend fun getModelInfo(): String {
        return try {
            val model = getOrCreateGenerativeModel()
            val status = model.checkStatus()
            val modelName = model.getBaseModelName()
            "ML Kit GenAI ($modelName) - Status: $status"
        } catch (e: Exception) {
            "ML Kit GenAI (Gemini Nano) - Error: ${e.message}"
        }
    }

    /**
     * Release ML Kit resources.
     */
    override fun release() {
        logger.d { "Releasing ML Kit GenAI resources" }
        generativeModel?.close()
        generativeModel = null
    }

    // --- Private Helpers ---

    private fun getOrCreateGenerativeModel(): GenerativeModel {
        return generativeModel ?: Generation.getClient().also {
            generativeModel = it
        }
    }

    private fun <T> mapExceptionToAiResult(e: Exception): AiResult<T> {
        val message = e.message ?: "Unknown error"
        val errorCode = when {
            message.contains("quota", ignoreCase = true) -> AiErrorCode.QUOTA_EXCEEDED
            message.contains("safety", ignoreCase = true) -> AiErrorCode.CONTENT_FILTERED
            message.contains("too long", ignoreCase = true) -> AiErrorCode.INPUT_TOO_LONG
            message.contains("cancel", ignoreCase = true) -> AiErrorCode.CANCELLED
            message.contains("busy", ignoreCase = true) -> AiErrorCode.BUSY
            message.contains("unavailable", ignoreCase = true) -> AiErrorCode.UNAVAILABLE
            else -> AiErrorCode.UNKNOWN
        }
        return AiResult.Error(errorCode, message)
    }
}
