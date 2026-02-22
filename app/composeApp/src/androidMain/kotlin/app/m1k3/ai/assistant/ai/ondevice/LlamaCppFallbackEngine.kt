package app.m1k3.ai.assistant.ai.ondevice

import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.domain.ai.GenerationConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TODO - Refactor this
 * LlamaCppFallbackEngine - OnDeviceAi adapter for BaseLlmEngine (LlamaCpp)
 *
 * This adapter wraps an existing BaseLlmEngine implementation to provide the
 * OnDeviceAi interface. It's used as a fallback when platform-native AI
 * (ML Kit GenAI / Apple Foundation Models) is not available.
 *
 * ## Why This Exists
 *
 * Platform-native AI has device requirements:
 * - **Android ML Kit GenAI**: Tensor G3+, SD 8 Gen 3+
 * - **iOS Foundation Models**: A17 Pro+, M1+
 *
 * For older devices, we fall back to LlamaCpp with GGUF models.
 * This adapter makes that fallback transparent to the rest of the app.
 *
 * ## Usage
 *
 * ```kotlin
 * val llamaCppEngine: BaseLlmEngine = LlamaCppEngine(context)
 * val fallback: OnDeviceAi = LlamaCppFallbackEngine(llamaCppEngine)
 *
 * when (val availability = fallback.checkAvailability()) {
 *     is AiAvailability.Fallback -> println("Using ${availability.engineName}")
 *     is AiAvailability.Unavailable -> println("AI not available")
 *     else -> { /* not expected for fallback */ }
 * }
 * ```
 *
 * @param engine The underlying BaseLlmEngine implementation
 */
class LlamaCppFallbackEngine(
    private val engine: BaseLlmEngine
) : OnDeviceAi {

    // Thread-safety: Use @Volatile for visibility across threads
    // and Mutex to prevent race conditions during initialization
    @Volatile
    private var isInitialized = false

    @Volatile
    private var initializationError: Exception? = null

    private val initMutex = Mutex()

    /**
     * Check if fallback engine is available.
     *
     * Returns Fallback when engine is initialized, Unavailable otherwise.
     * Never returns Available (reserved for platform-native AI).
     *
     * Thread-safe: Uses Mutex to prevent race conditions during initialization.
     */
    override suspend fun checkAvailability(): AiAvailability {
        // Fast path: already initialized
        if (isInitialized) {
            return AiAvailability.Fallback("LlamaCpp")
        }

        // Slow path: need to initialize with lock
        return initMutex.withLock {
            // Double-check after acquiring lock
            if (isInitialized) {
                return@withLock AiAvailability.Fallback("LlamaCpp")
            }

            engine.initialize().fold(
                onSuccess = {
                    isInitialized = true
                    AiAvailability.Fallback("LlamaCpp")
                },
                onFailure = { e ->
                    initializationError = e as? Exception ?: Exception(e.message)
                    AiAvailability.Unavailable(AiAvailability.UnavailableReason.MODEL_NOT_READY)
                }
            )
        }
    }

    /**
     * Download/initialize the model if needed.
     *
     * For LlamaCpp, this initializes the engine (copies model from assets).
     *
     * Thread-safe: Uses Mutex to prevent race conditions during initialization.
     */
    override suspend fun downloadModelIfNeeded(): AiResult<Unit> {
        // Fast path: already initialized
        if (isInitialized) {
            return AiResult.Success(Unit)
        }

        // Slow path: need to initialize with lock
        return initMutex.withLock {
            // Double-check after acquiring lock
            if (isInitialized) {
                return@withLock AiResult.Success(Unit)
            }

            engine.initialize().fold(
                onSuccess = {
                    isInitialized = true
                    AiResult.Success(Unit)
                },
                onFailure = { e ->
                    initializationError = e as? Exception ?: Exception(e.message)
                    AiResult.Error(
                        AiErrorCode.UNAVAILABLE,
                        "Failed to initialize LlamaCpp: ${e.message}"
                    )
                }
            )
        }
    }

    /**
     * Generate text from prompt.
     *
     * Delegates to underlying BaseLlmEngine.generate().
     */
    override suspend fun generate(prompt: String, config: GenerationConfig): AiResult<String> {
        if (!isInitialized) {
            return AiResult.Error(
                AiErrorCode.UNAVAILABLE,
                "Engine not initialized. Call downloadModelIfNeeded() first."
            )
        }

        return try {
            val result = engine.generate(prompt, config).getOrThrow()
            AiResult.Success(result.text)
        } catch (e: Exception) {
            AiResult.Error(
                AiErrorCode.UNKNOWN,
                "Generation failed: ${e.message}"
            )
        }
    }

    /**
     * Generate text with streaming tokens.
     *
     * Converts BaseLlmEngine's callback-based streaming to Flow<AiResult>.
     * Uses callbackFlow to properly bridge callback and Flow.
     */
    override fun generateStream(prompt: String, config: GenerationConfig): Flow<AiResult<String>> =
        callbackFlow {
            if (!isInitialized) {
                trySend(AiResult.Error(
                    AiErrorCode.UNAVAILABLE,
                    "Engine not initialized. Call downloadModelIfNeeded() first."
                ))
                close()
                return@callbackFlow
            }

            try {
                engine.generateStreaming(prompt, config) { token ->
                    trySend(AiResult.Success(token))
                }
                close()
            } catch (e: Exception) {
                trySend(AiResult.Error(
                    AiErrorCode.UNKNOWN,
                    "Streaming failed: ${e.message}"
                ))
                close(e)
            }

            awaitClose {
                // BaseLlmEngine.generateStreaming is a suspend function that naturally
                // respects cancellation via coroutine cancellation. No explicit cleanup needed.
            }
        }

    /**
     * Summarize text using prompt engineering.
     *
     * LlamaCpp doesn't have a dedicated summarization API, so we use
     * carefully crafted prompts to achieve similar results.
     */
    override suspend fun summarize(text: String, style: SummaryStyle): AiResult<String> {
        if (!isInitialized) {
            return AiResult.Error(
                AiErrorCode.UNAVAILABLE,
                "Engine not initialized. Call downloadModelIfNeeded() first."
            )
        }

        val summaryPrompt = buildSummaryPrompt(text, style)

        return try {
            val config = GenerationConfig(
                maxTokens = when (style) {
                    SummaryStyle.BRIEF -> 64
                    SummaryStyle.BULLETS -> 128
                    SummaryStyle.DETAILED -> 256
                },
                temperature = 0.3f  // Lower temperature for factual summary
            )
            val result = engine.generate(summaryPrompt, config).getOrThrow()
            AiResult.Success(result.text)
        } catch (e: Exception) {
            AiResult.Error(
                AiErrorCode.UNKNOWN,
                "Summarization failed: ${e.message}"
            )
        }
    }

    /**
     * Build summary prompt based on style.
     */
    private fun buildSummaryPrompt(text: String, style: SummaryStyle): String {
        return when (style) {
            SummaryStyle.BRIEF -> {
                "Summarize the following text in 1-2 sentences. Be brief and concise.\n\nText: $text\n\nSummary:"
            }
            SummaryStyle.BULLETS -> {
                "Summarize the following text as 3-5 bullet points. Focus on key takeaways.\n\nText: $text\n\nBullet points:"
            }
            SummaryStyle.DETAILED -> {
                "Provide a detailed and comprehensive summary of the following text in a paragraph.\n\nText: $text\n\nDetailed summary:"
            }
        }
    }

    /**
     * Get model information.
     */
    override suspend fun getModelInfo(): String {
        return "Gemma 3 270M (IQ3_XXS, 176MB, 32K context)"
    }

    /**
     * Release engine resources.
     */
    override fun release() {
        engine.release()
        isInitialized = false
        initializationError = null
    }
}
