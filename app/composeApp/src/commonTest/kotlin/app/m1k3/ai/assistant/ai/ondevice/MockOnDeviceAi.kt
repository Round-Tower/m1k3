package app.m1k3.ai.assistant.ai.ondevice

import app.m1k3.ai.assistant.ai.GenerationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Mock implementation of OnDeviceAi for testing.
 *
 * This mock allows tests to:
 * - Configure availability state
 * - Set expected responses
 * - Verify calls were made with correct parameters
 * - Control streaming token emission
 *
 * Usage:
 * ```kotlin
 * val mockAi = MockOnDeviceAi()
 * mockAi.setAvailability(AiAvailability.Available)
 * mockAi.setResponse("Hello from AI")
 *
 * val result = mockAi.generate("Hi", GenerationConfig())
 * assertEquals("Hello from AI", result.getOrNull())
 * assertEquals("Hi", mockAi.lastPrompt)
 * ```
 */
class MockOnDeviceAi : OnDeviceAi {

    // === Configuration ===

    private var availability: AiAvailability = AiAvailability.Available
    private var response: String = "Mock response"
    private var streamTokens: List<String> = listOf("Mock", " ", "response")
    private var modelInfo: String = "MockOnDeviceAi (test)"

    // === Verification State ===

    /** The last prompt passed to generate() */
    var lastPrompt: String? = null
        private set

    /** The last config passed to generate() */
    var lastConfig: GenerationConfig? = null
        private set

    /** The last style passed to summarize() */
    var lastSummaryStyle: SummaryStyle? = null
        private set

    /** Number of times generate() was called */
    var generateCallCount: Int = 0
        private set

    /** Number of times summarize() was called */
    var summarizeCallCount: Int = 0
        private set

    // === Configuration Methods ===

    /**
     * Set the availability state that checkAvailability() will return.
     */
    fun setAvailability(availability: AiAvailability) {
        this.availability = availability
    }

    /**
     * Set the response that generate() and summarize() will return.
     */
    fun setResponse(response: String) {
        this.response = response
    }

    /**
     * Set the tokens that generateStream() will emit.
     */
    fun setStreamTokens(tokens: List<String>) {
        this.streamTokens = tokens
    }

    /**
     * Set the model info that getModelInfo() will return.
     */
    fun setModelInfo(info: String) {
        this.modelInfo = info
    }

    /**
     * Reset all state to defaults.
     */
    fun reset() {
        availability = AiAvailability.Available
        response = "Mock response"
        streamTokens = listOf("Mock", " ", "response")
        modelInfo = "MockOnDeviceAi (test)"
        lastPrompt = null
        lastConfig = null
        lastSummaryStyle = null
        generateCallCount = 0
        summarizeCallCount = 0
    }

    // === OnDeviceAi Interface Implementation ===

    /**
     * Check AI availability.
     * Returns the configured availability state.
     */
    override suspend fun checkAvailability(): AiAvailability {
        return availability
    }

    /**
     * Download model if needed.
     * Returns Success if available or fallback, Error otherwise.
     */
    override suspend fun downloadModelIfNeeded(): AiResult<Unit> {
        return when (availability) {
            is AiAvailability.Available,
            is AiAvailability.Fallback,
            is AiAvailability.Downloading -> AiResult.Success(Unit)
            is AiAvailability.Unavailable -> AiResult.Error(
                AiErrorCode.UNAVAILABLE,
                "Model not available: ${(availability as AiAvailability.Unavailable).reason}"
            )
        }
    }

    /**
     * Generate text from a prompt.
     * Returns the configured response or an error based on availability.
     */
    override suspend fun generate(prompt: String, config: GenerationConfig): AiResult<String> {
        lastPrompt = prompt
        lastConfig = config
        generateCallCount++

        return when (availability) {
            is AiAvailability.Available,
            is AiAvailability.Fallback -> AiResult.Success(response)
            is AiAvailability.Downloading -> AiResult.Error(
                AiErrorCode.UNAVAILABLE,
                "Model is downloading"
            )
            is AiAvailability.Unavailable -> AiResult.Error(
                AiErrorCode.UNAVAILABLE,
                "AI unavailable: ${(availability as AiAvailability.Unavailable).reason}"
            )
        }
    }

    /**
     * Generate text with streaming tokens.
     * Emits configured tokens or an error based on availability.
     */
    override fun generateStream(prompt: String, config: GenerationConfig): Flow<AiResult<String>> = flow {
        lastPrompt = prompt
        lastConfig = config
        generateCallCount++

        when (availability) {
            is AiAvailability.Available,
            is AiAvailability.Fallback -> {
                for (token in streamTokens) {
                    emit(AiResult.Success(token))
                }
            }
            is AiAvailability.Downloading,
            is AiAvailability.Unavailable -> {
                emit(AiResult.Error(
                    AiErrorCode.UNAVAILABLE,
                    "AI unavailable"
                ))
            }
        }
    }

    /**
     * Summarize text with the given style.
     * Returns the configured response or an error based on availability.
     */
    override suspend fun summarize(text: String, style: SummaryStyle): AiResult<String> {
        lastPrompt = text
        lastSummaryStyle = style
        summarizeCallCount++

        return when (availability) {
            is AiAvailability.Available,
            is AiAvailability.Fallback -> AiResult.Success(response)
            is AiAvailability.Downloading -> AiResult.Error(
                AiErrorCode.UNAVAILABLE,
                "Model is downloading"
            )
            is AiAvailability.Unavailable -> AiResult.Error(
                AiErrorCode.UNAVAILABLE,
                "AI unavailable: ${(availability as AiAvailability.Unavailable).reason}"
            )
        }
    }

    /**
     * Get model information.
     * Returns the configured model info string.
     */
    override suspend fun getModelInfo(): String {
        return modelInfo
    }

    /**
     * Release mock resources.
     * In this mock, just resets state.
     */
    override fun release() {
        reset()
    }
}
