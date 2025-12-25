package app.m1k3.ai.assistant.ai

import kotlinx.coroutines.delay

/**
 * MockLlmEngine - Test implementation of BaseLlmEngine
 *
 * This mock engine simulates AI inference for testing purposes without requiring real models.
 * It's designed to:
 * 1. Validate the BaseLlmEngine interface contract
 * 2. Enable fast UI testing without model loading
 * 3. Provide deterministic responses for repeatable tests
 * 4. Support behavior verification (e.g., "was initialize() called?")
 *
 * Usage in tests:
 * ```kotlin
 * val engine = MockLlmEngine()
 * engine.initialize()
 * val result = engine.generate("Test prompt")
 * assertEquals("Mock response to: Test prompt", result.text)
 * ```
 *
 * Usage in UI development:
 * ```kotlin
 * // Replace real engine with mock for fast iteration
 * val aiEngine: BaseLlmEngine = if (BuildConfig.DEBUG) {
 *     MockLlmEngine()
 * } else {
 *     LlamaCppEngine(context)
 * }
 * ```
 *
 * @param responseDelay Simulated inference delay in milliseconds (default: 10ms for fast tests)
 * @param tokensPerResponse Number of tokens to generate per response (default: 20)
 * @param optimalMaxTokens Device-appropriate max tokens to report (default: 256)
 */
class MockLlmEngine(
    private val responseDelay: Long = 10L,
    private val tokensPerResponse: Int = 20,
    private val optimalMaxTokens: Int = 256
) : BaseLlmEngine {

    private var isInitialized = false
    var initializeCallCount = 0
        private set

    // Public accessor for tests to verify initialization state
    val isInitializedPublic: Boolean
        get() = isInitialized

    override suspend fun initialize(): Result<Unit> {
        if (isInitialized) {
            // Already initialized, skip
            return Result.success(Unit)
        }
        initializeCallCount++

        // Simulate initialization delay
        delay(5L)

        isInitialized = true
        return Result.success(Unit)
    }

    override suspend fun generate(
        prompt: String,
        config: GenerationConfig
    ): Result<GenerationResult> {
        // Check initialization
        if (!isInitialized) {
            return Result.failure(IllegalStateException("Engine not initialized. Call initialize() first."))
        }

        val startTime = System.currentTimeMillis()

        // Simulate inference delay
        delay(responseDelay)

        // Handle edge case: maxTokens = 0
        val maxTokens = config.maxTokens ?: optimalMaxTokens
        if (maxTokens == 0) {
            return Result.success(GenerationResult(
                text = "",
                tokensGenerated = 0,
                inferenceTimeMs = 0,
                tokensPerSecond = 0f
            ))
        }

        // Generate response based on configuration
        val responseText = buildMockResponse(prompt, config)
        val actualTokens = minOf(tokensPerResponse, maxTokens)

        val inferenceTime = System.currentTimeMillis() - startTime
        val tokensPerSecond = if (inferenceTime > 0) {
            (actualTokens * 1000.0f) / inferenceTime
        } else {
            actualTokens.toFloat()
        }

        return Result.success(GenerationResult(
            text = responseText,
            tokensGenerated = actualTokens,
            inferenceTimeMs = inferenceTime,
            tokensPerSecond = tokensPerSecond
        ))
    }

    override suspend fun generateStreaming(
        prompt: String,
        config: GenerationConfig,
        onToken: (String) -> Unit
    ): Result<Unit> {
        // Check initialization
        if (!isInitialized) {
            return Result.failure(IllegalStateException("Engine not initialized. Call initialize() first."))
        }

        return try {
            // Handle edge case: maxTokens = 0
            val maxTokens = config.maxTokens ?: optimalMaxTokens
            if (maxTokens == 0) {
                return Result.success(Unit)
            }

            // Generate response and emit token by token
            val responseText = buildMockResponse(prompt, config)
            val tokens = tokenizeResponse(responseText)
            val actualTokens = minOf(tokens.size, maxTokens)

            for (i in 0 until actualTokens) {
                delay(2L)  // Simulate per-token delay
                onToken(tokens[i])
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getOptimalMaxTokens(): Int {
        return optimalMaxTokens
    }

    override fun release() {
        isInitialized = false
    }

    // Helper functions

    /**
     * Build mock response based on prompt and configuration
     */
    private fun buildMockResponse(prompt: String, config: GenerationConfig): String {
        // Handle empty prompt
        if (prompt.isEmpty()) {
            return "Mock response to empty prompt"
        }

        // Build response incorporating configuration context
        val parts = mutableListOf<String>()

        // System prompt context
        if (config.systemPrompt != null) {
            when {
                config.systemPrompt.contains("pirate") -> parts.add("Ahoy matey!")
                config.systemPrompt.contains("assistant") -> parts.add("I'm here to help.")
            }
        }

        // Knowledge context (RAG)
        if (config.knowledgeContext != null) {
            when {
                config.knowledgeContext.contains("Eiffel Tower") &&
                prompt.contains("tall", ignoreCase = true) -> {
                    parts.add("The Eiffel Tower is 330 meters tall according to my knowledge.")
                }
            }
        }

        // User context personalization
        if (config.userContext != null) {
            val name = config.userContext["name"]
            if (name != null && prompt.contains("name", ignoreCase = true)) {
                parts.add("Your name is $name.")
            }
        }

        // Default response
        if (parts.isEmpty()) {
            parts.add("Mock response to: $prompt")
        }

        // Temperature affects determinism (for testing)
        if (config.temperature == 0.0f) {
            // Greedy decoding - always return same response
            parts.add("[deterministic]")
        }

        return parts.joinToString(" ")
    }

    /**
     * Tokenize response into mock tokens for streaming
     */
    private fun tokenizeResponse(text: String): List<String> {
        // Simple word-based tokenization for testing
        return text.split(" ").map { "$it " }
    }
}
