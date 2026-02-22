package app.m1k3.ai.domain.ai

/**
 * LLM Engine Interface - Unified interface for AI inference.
 *
 * Abstracts LLM functionality across different backends:
 * - LlamaCppEngine (llama.cpp via Llamatik)
 * - MockLlmEngine (testing)
 * - Cloud engines (future)
 *
 * Domain interface - Pure Kotlin, no platform dependencies.
 *
 * **Design Principles:**
 * 1. Platform agnostic - Works across Android, iOS, Desktop
 * 2. Backend flexibility - Supports engines with varying capabilities
 * 3. Test-friendly - Easy to mock for UI testing
 * 4. Result-based error handling - No surprise exceptions
 * 5. Configuration graceful degradation - Handles limited APIs
 *
 * **Usage:**
 * ```kotlin
 * val engine: LlmEngine = // platform implementation
 * engine.initialize().onFailure { return }
 *
 * val config = GenerationConfig(maxTokens = 256, temperature = 0.7f)
 *
 * // Streaming generation
 * engine.generateStreaming("Hello!", config) { token ->
 *     print(token)
 * }
 *
 * engine.release()
 * ```
 *
 * @see GenerationConfig for configuration options
 * @see GenerationResult for generation output
 */
interface LlmEngine {

    /**
     * Initialize the AI engine.
     *
     * May include:
     * - Loading model files
     * - Creating inference sessions
     * - Initializing tokenizers
     * - Setting up device-adaptive configurations
     *
     * Must be called before any generation methods.
     *
     * @return Result.success(Unit) if initialization succeeds
     */
    suspend fun initialize(): Result<Unit>

    /**
     * Generate AI response for a prompt (blocking).
     *
     * Blocks until full response is generated.
     *
     * @param prompt User input text
     * @param config Generation configuration
     * @return Result containing GenerationResult or failure
     */
    suspend fun generate(
        prompt: String,
        config: GenerationConfig = GenerationConfig()
    ): Result<GenerationResult>

    /**
     * Generate AI response with streaming token-by-token callback.
     *
     * Provides real-time generation feedback for progressive UIs.
     *
     * @param prompt User input text
     * @param config Generation configuration
     * @param onToken Callback invoked for each generated token
     * @return Result.success(Unit) if streaming completes
     */
    suspend fun generateStreaming(
        prompt: String,
        config: GenerationConfig = GenerationConfig(),
        onToken: (String) -> Unit
    ): Result<Unit>

    /**
     * Get device-appropriate maximum tokens.
     *
     * Returns optimal max tokens based on device capabilities.
     *
     * @return Recommended max tokens (64-512 depending on device)
     */
    fun getOptimalMaxTokens(): Int

    /**
     * Release engine resources.
     *
     * Cleans up native sessions, memory-mapped files, tokenizers.
     * After calling, engine must be re-initialized before use.
     */
    fun release()

    /**
     * Close engine (alias for release).
     *
     * Allows use with `use {}` blocks.
     */
    fun close() = release()
}

/**
 * Generation Result - Output of AI generation.
 *
 * Contains generated text and performance metrics.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * @property text Generated response text
 * @property tokensGenerated Number of tokens generated
 * @property inferenceTimeMs Generation time in milliseconds
 * @property tokensPerSecond Generation speed
 */
data class GenerationResult(
    val text: String,
    val tokensGenerated: Int,
    val inferenceTimeMs: Long,
    val tokensPerSecond: Float
) {
    /**
     * Performance tier based on tokens per second.
     */
    val performanceTier: PerformanceTier
        get() = when {
            tokensPerSecond >= 20f -> PerformanceTier.EXCELLENT
            tokensPerSecond >= 10f -> PerformanceTier.GOOD
            tokensPerSecond >= 5f -> PerformanceTier.ACCEPTABLE
            else -> PerformanceTier.SLOW
        }

    override fun toString(): String = buildString {
        appendLine("Generated: \"$text\"")
        appendLine("Tokens: $tokensGenerated")
        appendLine("Time: ${inferenceTimeMs}ms")
        append("Speed: ${"%.1f".format(tokensPerSecond)} tokens/sec")
    }
}

/**
 * Performance tier classification.
 */
enum class PerformanceTier {
    /** >= 20 tokens/sec - Excellent performance */
    EXCELLENT,
    /** >= 10 tokens/sec - Good performance */
    GOOD,
    /** >= 5 tokens/sec - Acceptable performance */
    ACCEPTABLE,
    /** < 5 tokens/sec - Slow performance */
    SLOW
}
