package app.m1k3.ai.assistant.ai.ondevice

import app.m1k3.ai.assistant.ai.GenerationConfig
import kotlinx.coroutines.flow.Flow

/**
 * OnDeviceAi - Platform-native on-device AI interface
 *
 * This interface abstracts platform-native AI capabilities:
 * - **Android**: Google ML Kit GenAI (Gemini Nano) with LlamaCpp fallback
 * - **iOS**: Apple Foundation Models (iOS 26+) with LlamaCpp fallback
 *
 * ## Relationship with BaseLlmEngine
 *
 * `OnDeviceAi` and `BaseLlmEngine` serve different purposes:
 *
 * | Aspect | OnDeviceAi | BaseLlmEngine |
 * |--------|------------|---------------|
 * | **Purpose** | Platform-native AI with availability awareness | Direct LLM inference |
 * | **Availability** | Dynamic (model download, device support checks) | Static (assumes model available) |
 * | **Features** | Summarization, content safety, platform integration | Raw text generation |
 * | **Error handling** | AiResult sealed class with typed errors | Exceptions |
 * | **Fallback** | Automatic (ML Kit → LlamaCpp) | None (single engine) |
 *
 * ## Usage Flow
 *
 * ```kotlin
 * val ai: OnDeviceAi = AndroidOnDeviceAi(context)
 *
 * // 1. Check availability (may need download)
 * when (val availability = ai.checkAvailability()) {
 *     is AiAvailability.Available -> println("Ready!")
 *     is AiAvailability.Downloading -> println("Downloading: ${availability.progress}%")
 *     is AiAvailability.Unavailable -> println("Not available: ${availability.reason}")
 *     is AiAvailability.Fallback -> println("Using fallback: ${availability.engineName}")
 * }
 *
 * // 2. Download model if needed (optional, will auto-download on first use)
 * ai.downloadModelIfNeeded().onError { code, msg ->
 *     println("Download failed: $msg")
 * }
 *
 * // 3. Generate text
 * val result = ai.generate("Explain quantum computing", GenerationConfig(maxTokens = 256))
 * result.fold(
 *     onSuccess = { response -> println(response) },
 *     onError = { code, msg -> println("Error: $code - $msg") }
 * )
 *
 * // 4. Streaming generation
 * ai.generateStream("Tell me a story", GenerationConfig()).collect { tokenResult ->
 *     tokenResult.onSuccess { token -> print(token) }
 * }
 *
 * // 5. Summarization (platform-optimized)
 * val summary = ai.summarize(longArticle, SummaryStyle.BULLETS)
 * ```
 *
 * ## Platform Implementation Notes
 *
 * ### Android (ML Kit GenAI)
 * - Requires Google Play Services AI Core
 * - Gemini Nano model (~300MB download)
 * - Automatic model management by Play Services
 * - Falls back to LlamaCpp on unsupported devices
 *
 * ### iOS (Foundation Models)
 * - Requires iOS 26+ and Apple Intelligence capability
 * - Uses on-device Apple models
 * - No download required (pre-installed)
 * - Falls back to LlamaCpp on older devices
 *
 * @see BaseLlmEngine for direct LLM operations without availability management
 * @see AiAvailability for availability state modeling
 * @see AiResult for error handling
 */
interface OnDeviceAi {

    /**
     * Check current AI availability status.
     *
     * This is a lightweight check that returns immediately with current status.
     * Does not trigger downloads or initialization.
     *
     * @return Current availability state:
     *   - [AiAvailability.Available] - Ready to use
     *   - [AiAvailability.Downloading] - Model download in progress
     *   - [AiAvailability.Unavailable] - Not available (with reason)
     *   - [AiAvailability.Fallback] - Using fallback engine
     */
    suspend fun checkAvailability(): AiAvailability

    /**
     * Download the AI model if not already available.
     *
     * On Android (ML Kit): Triggers Gemini Nano download via Play Services
     * On iOS: No-op (models are pre-installed on supported devices)
     *
     * This is optional - models will auto-download on first generate() call.
     * Use this for proactive downloading (e.g., during onboarding).
     *
     * @return Success if model is available or download started successfully,
     *         Error with UNAVAILABLE if device doesn't support AI
     */
    suspend fun downloadModelIfNeeded(): AiResult<Unit>

    /**
     * Generate text from a prompt.
     *
     * Blocks until the full response is generated.
     *
     * @param prompt User input text
     * @param config Generation configuration (max tokens, system prompt, etc.)
     * @return Success with generated text, or Error with typed error code
     */
    suspend fun generate(prompt: String, config: GenerationConfig): AiResult<String>

    /**
     * Generate text with streaming tokens.
     *
     * Emits tokens as they are generated for real-time UI updates.
     * Each emission is an AiResult to handle mid-stream errors.
     *
     * @param prompt User input text
     * @param config Generation configuration
     * @return Flow emitting AiResult<String> for each token
     */
    fun generateStream(prompt: String, config: GenerationConfig): Flow<AiResult<String>>

    /**
     * Summarize text with the given style.
     *
     * Uses platform-optimized summarization:
     * - Android ML Kit: OutputType mapping (ONE_BULLET, THREE_BULLETS, PARAGRAPH)
     * - iOS Foundation Models: Prompt engineering with style instructions
     *
     * @param text Text to summarize
     * @param style Desired summary style (BRIEF, BULLETS, DETAILED)
     * @return Success with summarized text, or Error with typed error code
     */
    suspend fun summarize(text: String, style: SummaryStyle): AiResult<String>

    /**
     * Get information about the current AI model.
     *
     * Returns human-readable string with model details:
     * - Model name and version
     * - Platform backend (ML Kit, Foundation Models, LlamaCpp)
     * - Capabilities supported
     *
     * @return Model information string
     */
    suspend fun getModelInfo(): String

    /**
     * Release AI resources.
     *
     * Cleans up native resources, inference sessions, etc.
     * Call when AI is no longer needed to free memory.
     */
    fun release()
}
