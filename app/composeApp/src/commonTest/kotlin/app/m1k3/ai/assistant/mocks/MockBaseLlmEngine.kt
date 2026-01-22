package app.m1k3.ai.assistant.mocks

import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.domain.ai.GenerationConfig
import app.m1k3.ai.assistant.ai.GenerationResult

/**
 * Mock implementation of BaseLlmEngine for testing.
 *
 * Provides predictable AI responses without requiring
 * actual ONNX runtime or model files.
 *
 * **Usage:**
 * ```kotlin
 * val mockEngine = MockBaseLlmEngine()
 *
 * // Configure response
 * mockEngine.setResponse("Hello! How can I help?")
 *
 * // Or configure streaming tokens
 * mockEngine.setStreamingTokens(listOf("Hello", "world", "!"))
 *
 * // Or simulate error
 * mockEngine.setError(RuntimeException("Model crashed"))
 * ```
 */
class MockBaseLlmEngine : BaseLlmEngine {
    // Configurable responses
    private var responseText: String = "Mock response"
    private var streamingTokens: List<String> = listOf("Mock", "response")
    private var optimalMaxTokens: Int = 256

    // Configurable behavior
    private var initializeResult: Result<Unit> = Result.success(Unit)
    private var generateResult: Result<GenerationResult>? = null
    private var streamingResult: Result<Unit>? = null

    // Tracking
    var initializeCallCount = 0
        private set
    var generateCallCount = 0
        private set
    var streamingCallCount = 0
        private set
    var releaseCallCount = 0
        private set
    var lastPrompt: String? = null
        private set
    var lastConfig: GenerationConfig? = null
        private set

    // ===== Configuration Methods =====

    /**
     * Set the response text for generate() and streaming tokens.
     */
    fun setResponse(text: String) {
        responseText = text
        streamingTokens = text.split(" ")
    }

    /**
     * Set specific streaming tokens.
     */
    fun setStreamingTokens(tokens: List<String>) {
        streamingTokens = tokens
        responseText = tokens.joinToString(" ")
    }

    /**
     * Set initialize() to fail with error.
     */
    fun setInitializeError(error: Exception) {
        initializeResult = Result.failure(error)
    }

    /**
     * Set generate() to fail with error.
     */
    fun setGenerateError(error: Exception) {
        generateResult = Result.failure(error)
    }

    /**
     * Set generateStreaming() to fail with error.
     */
    fun setStreamingError(error: Exception) {
        streamingResult = Result.failure(error)
    }

    /**
     * Set optimal max tokens.
     */
    fun setOptimalMaxTokens(tokens: Int) {
        optimalMaxTokens = tokens
    }

    /**
     * Reset all state.
     */
    fun reset() {
        responseText = "Mock response"
        streamingTokens = listOf("Mock", "response")
        optimalMaxTokens = 256
        initializeResult = Result.success(Unit)
        generateResult = null
        streamingResult = null
        initializeCallCount = 0
        generateCallCount = 0
        streamingCallCount = 0
        releaseCallCount = 0
        lastPrompt = null
        lastConfig = null
    }

    // ===== BaseLlmEngine Implementation =====

    override suspend fun initialize(): Result<Unit> {
        initializeCallCount++
        return initializeResult
    }

    override suspend fun generate(prompt: String, config: GenerationConfig): Result<GenerationResult> {
        generateCallCount++
        lastPrompt = prompt
        lastConfig = config

        // Return error if configured
        generateResult?.let { return it }

        // Return successful result
        return Result.success(
            GenerationResult(
                text = responseText,
                tokensGenerated = responseText.split(" ").size,
                inferenceTimeMs = 100,
                tokensPerSecond = 10f
            )
        )
    }

    override suspend fun generateStreaming(
        prompt: String,
        config: GenerationConfig,
        onToken: (String) -> Unit
    ): Result<Unit> {
        streamingCallCount++
        lastPrompt = prompt
        lastConfig = config

        // Return error if configured
        streamingResult?.let { return it }

        // Stream tokens
        streamingTokens.forEach { token ->
            onToken(token)
        }

        return Result.success(Unit)
    }

    override fun getOptimalMaxTokens(): Int = optimalMaxTokens

    override fun release() {
        releaseCallCount++
    }

    companion object {
        /**
         * Create mock with a simple greeting response.
         */
        fun withGreeting() = MockBaseLlmEngine().apply {
            setResponse("Hello! How can I help you today?")
        }

        /**
         * Create mock with a science explanation.
         */
        fun withScienceResponse() = MockBaseLlmEngine().apply {
            setResponse("Photosynthesis is the process by which plants convert sunlight into energy.")
        }

        /**
         * Create mock that fails on streaming.
         */
        fun withStreamingError() = MockBaseLlmEngine().apply {
            setStreamingError(RuntimeException("Model inference failed"))
        }
    }
}
