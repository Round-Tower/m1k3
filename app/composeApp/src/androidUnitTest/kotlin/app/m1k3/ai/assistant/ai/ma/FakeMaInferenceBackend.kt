package app.m1k3.ai.assistant.ai.ma

/**
 * FakeMaInferenceBackend - Test double for [MaInferenceBackend].
 *
 * Provides controllable behavior for unit testing [LlamaCppEngine]
 * without requiring native llama.cpp code or a real model file.
 */
class FakeMaInferenceBackend : MaInferenceBackend {

    // === init() controls ===
    var initHandle: Long = 1L
    var initCalled = false
    var initCallCount = 0
    var lastInitPath: String? = null

    // === generate() controls ===
    var generateResponse: String = "Test response from fake backend"
    var generateCalled = false
    var generateCallCount = 0
    var lastGenerateHandle: Long = 0L
    var lastGeneratePrompt: String? = null
    var lastGenerateMaxTokens: Int = 0
    var lastGenerateTemperature: Float = 0f

    /** Tokens emitted one-by-one when onToken callback is provided (streaming). */
    var streamingTokens: List<String> = emptyList()

    // === release() controls ===
    var releaseCalled = false
    var releaseCallCount = 0
    var lastReleaseHandle: Long = 0L

    override fun init(modelPath: String): Long {
        initCalled = true
        initCallCount++
        lastInitPath = modelPath
        return initHandle
    }

    override fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        onToken: ((String) -> Unit)?
    ): String {
        generateCalled = true
        generateCallCount++
        lastGenerateHandle = handle
        lastGeneratePrompt = prompt
        lastGenerateMaxTokens = maxTokens
        lastGenerateTemperature = temperature

        if (onToken != null) {
            streamingTokens.forEach { onToken(it) }
        }

        return generateResponse
    }

    override fun release(handle: Long) {
        releaseCalled = true
        releaseCallCount++
        lastReleaseHandle = handle
    }

    /** Reset all recorded state between tests. */
    fun reset() {
        initCalled = false
        initCallCount = 0
        lastInitPath = null
        generateCalled = false
        generateCallCount = 0
        lastGenerateHandle = 0L
        lastGeneratePrompt = null
        releaseCalled = false
        releaseCallCount = 0
        lastReleaseHandle = 0L
    }
}
