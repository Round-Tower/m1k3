package app.m1k3.ai.assistant.ai.ondevice

import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.domain.ai.GenerationConfig
import app.m1k3.ai.assistant.ai.GenerationResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * TDD Tests for LlamaCppFallbackEngine - Phase 2
 *
 * LlamaCppFallbackEngine wraps BaseLlmEngine to provide the OnDeviceAi interface
 * for devices that don't support ML Kit GenAI or Apple Foundation Models.
 *
 * These tests use a MockBaseLlmEngine to verify the adapter behavior.
 */
class LlamaCppFallbackEngineTest {

    private lateinit var mockEngine: MockBaseLlmEngine
    private lateinit var fallbackEngine: LlamaCppFallbackEngine

    @Before
    fun setUp() {
        mockEngine = MockBaseLlmEngine()
        fallbackEngine = LlamaCppFallbackEngine(mockEngine)
    }

    // === Availability Tests ===

    @Test
    fun `checkAvailability returns Fallback when engine is initialized`() = runTest {
        mockEngine.isInitializedResult = true

        val result = fallbackEngine.checkAvailability()

        assertTrue(result is AiAvailability.Fallback)
        assertEquals("LlamaCpp", (result as AiAvailability.Fallback).engineName)
    }

    @Test
    fun `checkAvailability returns Unavailable when engine fails to initialize`() = runTest {
        mockEngine.isInitializedResult = false
        mockEngine.initializeThrows = RuntimeException("Model not found")

        val result = fallbackEngine.checkAvailability()

        assertTrue(result is AiAvailability.Unavailable)
        assertEquals(
            AiAvailability.UnavailableReason.MODEL_NOT_READY,
            (result as AiAvailability.Unavailable).reason
        )
    }

    // === Download Tests ===

    @Test
    fun `downloadModelIfNeeded returns Success when engine initializes`() = runTest {
        mockEngine.isInitializedResult = false

        val result = fallbackEngine.downloadModelIfNeeded()

        assertTrue(result.isSuccess)
        assertTrue(mockEngine.initializeCalled)
    }

    @Test
    fun `downloadModelIfNeeded returns Success when already initialized`() = runTest {
        // First initialization
        fallbackEngine.downloadModelIfNeeded()
        mockEngine.initializeCalled = false  // Reset the flag

        // Second call should not initialize again
        val result = fallbackEngine.downloadModelIfNeeded()

        assertTrue(result.isSuccess)
        // Should not call initialize again
        assertFalse(mockEngine.initializeCalled)
    }

    @Test
    fun `downloadModelIfNeeded returns Error when initialization fails`() = runTest {
        mockEngine.isInitializedResult = false
        mockEngine.initializeThrows = RuntimeException("Failed to load model")

        val result = fallbackEngine.downloadModelIfNeeded()

        assertTrue(result.isError)
        assertEquals(AiErrorCode.UNAVAILABLE, (result as AiResult.Error).code)
    }

    // === Generate Tests ===

    @Test
    fun `generate returns Success with response when engine succeeds`() = runTest {
        mockEngine.generateResult = GenerationResult(
            text = "Hello from LlamaCpp!",
            tokensGenerated = 5,
            inferenceTimeMs = 100,
            tokensPerSecond = 50f
        )
        // Initialize the fallback engine first
        fallbackEngine.downloadModelIfNeeded()

        val result = fallbackEngine.generate("Hi", GenerationConfig())

        assertTrue(result.isSuccess)
        assertEquals("Hello from LlamaCpp!", result.getOrNull())
    }

    @Test
    fun `generate returns Error when engine not initialized`() = runTest {
        mockEngine.isInitializedResult = false

        val result = fallbackEngine.generate("Hi", GenerationConfig())

        assertTrue(result.isError)
        assertEquals(AiErrorCode.UNAVAILABLE, (result as AiResult.Error).code)
    }

    @Test
    fun `generate returns Error when engine throws exception`() = runTest {
        // Initialize first
        fallbackEngine.downloadModelIfNeeded()
        mockEngine.generateThrows = RuntimeException("Inference failed")

        val result = fallbackEngine.generate("Hi", GenerationConfig())

        assertTrue(result.isError)
        assertEquals(AiErrorCode.UNKNOWN, (result as AiResult.Error).code)
    }

    @Test
    fun `generate passes config to underlying engine`() = runTest {
        mockEngine.generateResult = GenerationResult("response", 1, 10, 10f)
        // Initialize first
        fallbackEngine.downloadModelIfNeeded()
        val config = GenerationConfig(maxTokens = 256, temperature = 0.5f)

        fallbackEngine.generate("test", config)

        assertEquals(config, mockEngine.lastConfig)
    }

    // === GenerateStream Tests ===

    @Test
    fun `generateStream emits tokens as Success results`() = runTest {
        mockEngine.streamTokens = listOf("Hello", " ", "World")
        // Initialize first
        fallbackEngine.downloadModelIfNeeded()

        val results = fallbackEngine.generateStream("Hi", GenerationConfig()).toList()

        assertEquals(3, results.size)
        assertTrue(results.all { it.isSuccess })
        assertEquals("Hello", results[0].getOrNull())
        assertEquals(" ", results[1].getOrNull())
        assertEquals("World", results[2].getOrNull())
    }

    @Test
    fun `generateStream emits Error when engine not initialized`() = runTest {
        mockEngine.isInitializedResult = false

        val results = fallbackEngine.generateStream("Hi", GenerationConfig()).toList()

        assertEquals(1, results.size)
        assertTrue(results[0].isError)
        assertEquals(AiErrorCode.UNAVAILABLE, (results[0] as AiResult.Error).code)
    }

    // === Summarize Tests ===

    @Test
    fun `summarize returns Success with summary`() = runTest {
        mockEngine.generateResult = GenerationResult(
            text = "This is a summary.",
            tokensGenerated = 5,
            inferenceTimeMs = 100,
            tokensPerSecond = 50f
        )
        // Initialize first
        fallbackEngine.downloadModelIfNeeded()

        val result = fallbackEngine.summarize("Long text to summarize", SummaryStyle.BRIEF)

        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
    }

    @Test
    fun `summarize uses BRIEF style prompt`() = runTest {
        mockEngine.generateResult = GenerationResult("summary", 1, 10, 10f)
        // Initialize first
        fallbackEngine.downloadModelIfNeeded()

        fallbackEngine.summarize("text", SummaryStyle.BRIEF)

        assertTrue(mockEngine.lastPrompt?.contains("1-2 sentence") == true ||
                   mockEngine.lastPrompt?.contains("brief") == true ||
                   mockEngine.lastPrompt?.lowercase()?.contains("brief") == true)
    }

    @Test
    fun `summarize uses BULLETS style prompt`() = runTest {
        mockEngine.generateResult = GenerationResult("- point 1\n- point 2", 1, 10, 10f)
        // Initialize first
        fallbackEngine.downloadModelIfNeeded()

        fallbackEngine.summarize("text", SummaryStyle.BULLETS)

        assertTrue(mockEngine.lastPrompt?.contains("bullet") == true ||
                   mockEngine.lastPrompt?.contains("points") == true)
    }

    @Test
    fun `summarize uses DETAILED style prompt`() = runTest {
        mockEngine.generateResult = GenerationResult("detailed summary", 1, 10, 10f)
        // Initialize first
        fallbackEngine.downloadModelIfNeeded()

        fallbackEngine.summarize("text", SummaryStyle.DETAILED)

        assertTrue(mockEngine.lastPrompt?.contains("detailed") == true ||
                   mockEngine.lastPrompt?.contains("comprehensive") == true ||
                   mockEngine.lastPrompt?.lowercase()?.contains("detailed") == true)
    }

    // === Model Info Tests ===

    @Test
    fun `getModelInfo returns model info string`() = runTest {
        val info = fallbackEngine.getModelInfo()

        // Gemma 3 270M is the current fallback model used via LlamaCpp
        assertTrue(info.contains("Gemma") || info.contains("llama") || info.contains("LlamaCpp"))
        assertTrue(info.contains("context") || info.contains("MB"))
    }

    // === Release Tests ===

    @Test
    fun `release calls underlying engine release`() {
        fallbackEngine.release()

        assertTrue(mockEngine.releaseCalled)
    }
}

/**
 * Mock implementation of BaseLlmEngine for testing LlamaCppFallbackEngine.
 */
class MockBaseLlmEngine : BaseLlmEngine {

    var isInitializedResult = false
    var initializeCalled = false
    var initializeThrows: Exception? = null

    var generateResult: GenerationResult? = null
    var generateThrows: Exception? = null
    var lastPrompt: String? = null
    var lastConfig: GenerationConfig? = null

    var streamTokens: List<String> = emptyList()

    var releaseCalled = false

    override suspend fun initialize(): Result<Unit> {
        initializeCalled = true
        initializeThrows?.let { return Result.failure(it) }
        isInitializedResult = true
        return Result.success(Unit)
    }

    override suspend fun generate(prompt: String, config: GenerationConfig): Result<GenerationResult> {
        lastPrompt = prompt
        lastConfig = config
        generateThrows?.let { return Result.failure(it) }
        return Result.success(generateResult ?: GenerationResult("", 0, 0, 0f))
    }

    override suspend fun generateStreaming(
        prompt: String,
        config: GenerationConfig,
        onToken: (String) -> Unit
    ): Result<Unit> {
        lastPrompt = prompt
        lastConfig = config
        for (token in streamTokens) {
            onToken(token)
        }
        return Result.success(Unit)
    }

    override fun getOptimalMaxTokens(): Int = 256

    override fun release() {
        releaseCalled = true
        isInitializedResult = false
    }

    fun isInitialized(): Boolean = isInitializedResult
}
