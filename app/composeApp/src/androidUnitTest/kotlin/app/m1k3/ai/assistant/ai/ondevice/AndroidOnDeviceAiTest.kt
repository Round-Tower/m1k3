package app.m1k3.ai.assistant.ai.ondevice

import app.m1k3.ai.assistant.ai.GenerationConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * TDD Tests for AndroidOnDeviceAi - Phase 2
 *
 * AndroidOnDeviceAi is the Android-specific implementation of OnDeviceAi that:
 * 1. Checks if ML Kit GenAI (Gemini Nano) is available on the device
 * 2. Uses ML Kit GenAI for capable devices (Tensor G3+, SD 8 Gen 3+)
 * 3. Falls back to LlamaCppFallbackEngine for older devices
 *
 * These tests use mock interfaces to verify the selection logic.
 */
class AndroidOnDeviceAiTest {

    private lateinit var mockMlKitChecker: MockMlKitAvailabilityChecker
    private lateinit var mockMlKitEngine: MockMlKitGenAiEngine
    private lateinit var mockFallbackEngine: MockOnDeviceAiForAndroid
    private lateinit var androidOnDeviceAi: AndroidOnDeviceAi

    @Before
    fun setUp() {
        mockMlKitChecker = MockMlKitAvailabilityChecker()
        mockMlKitEngine = MockMlKitGenAiEngine()
        mockFallbackEngine = MockOnDeviceAiForAndroid()
        androidOnDeviceAi = AndroidOnDeviceAi(
            mlKitChecker = mockMlKitChecker,
            mlKitEngine = mockMlKitEngine,
            fallbackEngine = mockFallbackEngine
        )
    }

    // === Engine Selection Tests ===

    @Test
    fun `checkAvailability returns Available when ML Kit is available`() = runTest {
        mockMlKitChecker.isAvailable = true
        mockMlKitEngine.availability = AiAvailability.Available

        val result = androidOnDeviceAi.checkAvailability()

        assertTrue(result is AiAvailability.Available)
    }

    @Test
    fun `checkAvailability returns Fallback when ML Kit unavailable`() = runTest {
        mockMlKitChecker.isAvailable = false
        mockFallbackEngine.availability = AiAvailability.Fallback("LlamaCpp")

        val result = androidOnDeviceAi.checkAvailability()

        assertTrue(result is AiAvailability.Fallback)
        assertEquals("LlamaCpp", (result as AiAvailability.Fallback).engineName)
    }

    @Test
    fun `checkAvailability returns Downloading when ML Kit model downloading`() = runTest {
        mockMlKitChecker.isAvailable = true
        mockMlKitEngine.availability = AiAvailability.Downloading

        val result = androidOnDeviceAi.checkAvailability()

        assertTrue(result is AiAvailability.Downloading)
    }

    @Test
    fun `checkAvailability uses fallback when ML Kit check throws exception`() = runTest {
        mockMlKitChecker.throwsException = true
        mockFallbackEngine.availability = AiAvailability.Fallback("LlamaCpp")

        val result = androidOnDeviceAi.checkAvailability()

        assertTrue(result is AiAvailability.Fallback)
    }

    // === Download Tests ===

    @Test
    fun `downloadModelIfNeeded uses ML Kit when available`() = runTest {
        mockMlKitChecker.isAvailable = true
        mockMlKitEngine.downloadResult = AiResult.Success(Unit)

        val result = androidOnDeviceAi.downloadModelIfNeeded()

        assertTrue(result.isSuccess)
        assertTrue(mockMlKitEngine.downloadCalled)
        assertFalse(mockFallbackEngine.downloadCalled)
    }

    @Test
    fun `downloadModelIfNeeded uses fallback when ML Kit unavailable`() = runTest {
        mockMlKitChecker.isAvailable = false
        mockFallbackEngine.downloadResult = AiResult.Success(Unit)

        val result = androidOnDeviceAi.downloadModelIfNeeded()

        assertTrue(result.isSuccess)
        assertFalse(mockMlKitEngine.downloadCalled)
        assertTrue(mockFallbackEngine.downloadCalled)
    }

    // === Generate Tests ===

    @Test
    fun `generate uses ML Kit engine when available`() = runTest {
        mockMlKitChecker.isAvailable = true
        mockMlKitEngine.availability = AiAvailability.Available
        mockMlKitEngine.generateResult = AiResult.Success("ML Kit response")
        // Initialize first
        androidOnDeviceAi.downloadModelIfNeeded()

        val result = androidOnDeviceAi.generate("Hello", GenerationConfig())

        assertTrue(result.isSuccess)
        assertEquals("ML Kit response", result.getOrNull())
        assertEquals("Hello", mockMlKitEngine.lastPrompt)
    }

    @Test
    fun `generate uses fallback engine when ML Kit unavailable`() = runTest {
        mockMlKitChecker.isAvailable = false
        mockFallbackEngine.availability = AiAvailability.Fallback("LlamaCpp")
        mockFallbackEngine.generateResult = AiResult.Success("Fallback response")
        // Initialize first
        androidOnDeviceAi.downloadModelIfNeeded()

        val result = androidOnDeviceAi.generate("Hello", GenerationConfig())

        assertTrue(result.isSuccess)
        assertEquals("Fallback response", result.getOrNull())
        assertEquals("Hello", mockFallbackEngine.lastPrompt)
    }

    @Test
    fun `generate returns error when not initialized`() = runTest {
        // Don't call downloadModelIfNeeded

        val result = androidOnDeviceAi.generate("Hello", GenerationConfig())

        assertTrue(result.isError)
        assertEquals(AiErrorCode.UNAVAILABLE, (result as AiResult.Error).code)
    }

    @Test
    fun `generate passes config to active engine`() = runTest {
        mockMlKitChecker.isAvailable = true
        mockMlKitEngine.availability = AiAvailability.Available
        mockMlKitEngine.generateResult = AiResult.Success("response")
        androidOnDeviceAi.downloadModelIfNeeded()
        val config = GenerationConfig(maxTokens = 256, temperature = 0.7f)

        androidOnDeviceAi.generate("test", config)

        assertEquals(config, mockMlKitEngine.lastConfig)
    }

    // === GenerateStream Tests ===

    @Test
    fun `generateStream uses ML Kit when available`() = runTest {
        mockMlKitChecker.isAvailable = true
        mockMlKitEngine.availability = AiAvailability.Available
        mockMlKitEngine.streamTokens = listOf("Hello", " ", "World")
        androidOnDeviceAi.downloadModelIfNeeded()

        val results = androidOnDeviceAi.generateStream("Hi", GenerationConfig()).toList()

        assertEquals(3, results.size)
        assertTrue(results.all { it.isSuccess })
        assertEquals("Hello", results[0].getOrNull())
    }

    @Test
    fun `generateStream uses fallback when ML Kit unavailable`() = runTest {
        mockMlKitChecker.isAvailable = false
        mockFallbackEngine.availability = AiAvailability.Fallback("LlamaCpp")
        mockFallbackEngine.streamTokens = listOf("Fallback", " ", "response")
        androidOnDeviceAi.downloadModelIfNeeded()

        val results = androidOnDeviceAi.generateStream("Hi", GenerationConfig()).toList()

        assertEquals(3, results.size)
        assertEquals("Fallback", results[0].getOrNull())
    }

    // === Summarize Tests ===

    @Test
    fun `summarize uses ML Kit when available`() = runTest {
        mockMlKitChecker.isAvailable = true
        mockMlKitEngine.availability = AiAvailability.Available
        mockMlKitEngine.summarizeResult = AiResult.Success("ML Kit summary")
        androidOnDeviceAi.downloadModelIfNeeded()

        val result = androidOnDeviceAi.summarize("Long text here", SummaryStyle.BRIEF)

        assertTrue(result.isSuccess)
        assertEquals("ML Kit summary", result.getOrNull())
    }

    @Test
    fun `summarize uses fallback when ML Kit unavailable`() = runTest {
        mockMlKitChecker.isAvailable = false
        mockFallbackEngine.availability = AiAvailability.Fallback("LlamaCpp")
        mockFallbackEngine.summarizeResult = AiResult.Success("Fallback summary")
        androidOnDeviceAi.downloadModelIfNeeded()

        val result = androidOnDeviceAi.summarize("Long text", SummaryStyle.BULLETS)

        assertTrue(result.isSuccess)
        assertEquals("Fallback summary", result.getOrNull())
    }

    @Test
    fun `summarize passes style to active engine`() = runTest {
        mockMlKitChecker.isAvailable = true
        mockMlKitEngine.availability = AiAvailability.Available
        mockMlKitEngine.summarizeResult = AiResult.Success("summary")
        androidOnDeviceAi.downloadModelIfNeeded()

        androidOnDeviceAi.summarize("text", SummaryStyle.DETAILED)

        assertEquals(SummaryStyle.DETAILED, mockMlKitEngine.lastSummaryStyle)
    }

    // === Model Info Tests ===

    @Test
    fun `getModelInfo returns ML Kit info when available`() = runTest {
        mockMlKitChecker.isAvailable = true
        mockMlKitEngine.modelInfo = "Gemini Nano via ML Kit"
        androidOnDeviceAi.downloadModelIfNeeded()

        val info = androidOnDeviceAi.getModelInfo()

        assertTrue(info.contains("Gemini") || info.contains("ML Kit"))
    }

    @Test
    fun `getModelInfo returns fallback info when ML Kit unavailable`() = runTest {
        mockMlKitChecker.isAvailable = false
        mockFallbackEngine.modelInfo = "LlamaCpp Fallback"
        androidOnDeviceAi.downloadModelIfNeeded()

        val info = androidOnDeviceAi.getModelInfo()

        assertTrue(info.contains("LlamaCpp") || info.contains("Fallback"))
    }

    // === Release Tests ===

    @Test
    fun `release calls release on active engine`() = runTest {
        mockMlKitChecker.isAvailable = true
        androidOnDeviceAi.downloadModelIfNeeded()

        androidOnDeviceAi.release()

        assertTrue(mockMlKitEngine.releaseCalled)
    }

    @Test
    fun `release calls release on fallback when ML Kit unavailable`() = runTest {
        mockMlKitChecker.isAvailable = false
        androidOnDeviceAi.downloadModelIfNeeded()

        androidOnDeviceAi.release()

        assertTrue(mockFallbackEngine.releaseCalled)
    }

    // === Edge Cases ===

    @Test
    fun `handles ML Kit becoming unavailable after initialization`() = runTest {
        // Start with ML Kit available
        mockMlKitChecker.isAvailable = true
        mockMlKitEngine.availability = AiAvailability.Available
        mockMlKitEngine.generateResult = AiResult.Success("ML Kit response")
        androidOnDeviceAi.downloadModelIfNeeded()

        // First call works with ML Kit
        val result1 = androidOnDeviceAi.generate("First", GenerationConfig())
        assertEquals("ML Kit response", result1.getOrNull())

        // ML Kit engine starts failing (simulating model unload or issue)
        mockMlKitEngine.generateResult = AiResult.Error(
            AiErrorCode.UNAVAILABLE,
            "Model unloaded by system"
        )

        // Should return the error (doesn't auto-switch to fallback mid-session)
        val result2 = androidOnDeviceAi.generate("Second", GenerationConfig())
        assertTrue(result2.isError)
    }

    @Test
    fun `concurrent calls are handled safely`() = runTest {
        mockMlKitChecker.isAvailable = true
        mockMlKitEngine.availability = AiAvailability.Available
        mockMlKitEngine.downloadResult = AiResult.Success(Unit)

        // Actually concurrent calls using async/awaitAll
        val deferred = (1..10).map {
            async { androidOnDeviceAi.downloadModelIfNeeded() }
        }
        val results = deferred.awaitAll()

        // All should succeed
        assertTrue(results.all { it.isSuccess })
        // Download should only be called once due to mutex protection
        assertEquals(1, mockMlKitEngine.downloadCallCount)
    }

    @Test
    fun `rapid init and release cycles do not corrupt state`() = runTest {
        mockMlKitChecker.isAvailable = false
        mockFallbackEngine.downloadResult = AiResult.Success(Unit)

        repeat(10) {
            androidOnDeviceAi.downloadModelIfNeeded()
            androidOnDeviceAi.release()
        }

        // Final state should be clean - can re-initialize
        val result = androidOnDeviceAi.downloadModelIfNeeded()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `concurrent release calls only release engine once`() = runTest {
        mockMlKitChecker.isAvailable = true
        mockMlKitEngine.downloadResult = AiResult.Success(Unit)
        androidOnDeviceAi.downloadModelIfNeeded()

        // Reset the counter to count releases
        mockMlKitEngine.releaseCallCount.set(0)

        // Call release concurrently from multiple coroutines
        val deferred = (1..10).map {
            async { androidOnDeviceAi.release() }
        }
        deferred.awaitAll()

        // Engine should only be released once due to atomic CAS loop
        assertEquals(1, mockMlKitEngine.releaseCallCount.get())
    }

    @Test
    fun `concurrent download with delay only initializes once`() = runTest {
        mockMlKitChecker.isAvailable = true
        mockMlKitEngine.availability = AiAvailability.Available
        mockMlKitEngine.downloadResult = AiResult.Success(Unit)
        mockMlKitEngine.downloadDelayMs = 10 // Add delay to force interleaving

        // Launch many concurrent download requests
        val deferred = (1..20).map {
            async { androidOnDeviceAi.downloadModelIfNeeded() }
        }
        val results = deferred.awaitAll()

        // All should succeed
        assertTrue(results.all { it.isSuccess })
        // Download should only be called once due to mutex protection
        assertEquals(1, mockMlKitEngine.downloadCallCount)
    }
}

// === Mock Classes ===

/**
 * Mock for checking ML Kit GenAI availability on the device.
 */
class MockMlKitAvailabilityChecker : MlKitAvailabilityChecker {
    var isAvailable = false
    var throwsException = false

    override suspend fun isGenAiAvailable(): Boolean {
        if (throwsException) {
            throw RuntimeException("ML Kit check failed")
        }
        return isAvailable
    }
}

/**
 * Mock ML Kit GenAI engine for testing.
 */
class MockMlKitGenAiEngine : MlKitGenAiEngine {
    var availability: AiAvailability = AiAvailability.Available
    var downloadResult: AiResult<Unit> = AiResult.Success(Unit)
    var downloadCalled = false
    var downloadCallCount = 0
    var downloadDelayMs: Long = 0  // Add delay to force interleaving in concurrent tests
    var generateResult: AiResult<String> = AiResult.Success("Mock response")
    var summarizeResult: AiResult<String> = AiResult.Success("Mock summary")
    var streamTokens: List<String> = listOf("Mock", " ", "tokens")
    var modelInfo = "Mock ML Kit Engine"
    var lastPrompt: String? = null
    var lastConfig: GenerationConfig? = null
    var lastSummaryStyle: SummaryStyle? = null
    var releaseCalled = false
    val releaseCallCount = AtomicInteger(0)  // Thread-safe counter for concurrent release tests

    override suspend fun checkAvailability(): AiAvailability = availability

    override suspend fun downloadModelIfNeeded(): AiResult<Unit> {
        downloadCalled = true
        downloadCallCount++
        if (downloadDelayMs > 0) {
            delay(downloadDelayMs)
        }
        return downloadResult
    }

    override suspend fun generate(prompt: String, config: GenerationConfig): AiResult<String> {
        lastPrompt = prompt
        lastConfig = config
        return generateResult
    }

    override fun generateStream(prompt: String, config: GenerationConfig): Flow<AiResult<String>> {
        lastPrompt = prompt
        lastConfig = config
        return flowOf(*streamTokens.map { AiResult.Success(it) }.toTypedArray())
    }

    override suspend fun summarize(text: String, style: SummaryStyle): AiResult<String> {
        lastPrompt = text
        lastSummaryStyle = style
        return summarizeResult
    }

    override suspend fun getModelInfo(): String = modelInfo

    override fun release() {
        releaseCalled = true
        releaseCallCount.incrementAndGet()
    }
}

/**
 * Mock OnDeviceAi for testing fallback scenarios.
 */
class MockOnDeviceAiForAndroid : OnDeviceAi {
    var availability: AiAvailability = AiAvailability.Fallback("LlamaCpp")
    var downloadResult: AiResult<Unit> = AiResult.Success(Unit)
    var downloadCalled = false
    var generateResult: AiResult<String> = AiResult.Success("Fallback response")
    var summarizeResult: AiResult<String> = AiResult.Success("Fallback summary")
    var streamTokens: List<String> = listOf("Fallback", " ", "tokens")
    var modelInfo = "Fallback Engine"
    var lastPrompt: String? = null
    var lastConfig: GenerationConfig? = null
    var lastSummaryStyle: SummaryStyle? = null
    var releaseCalled = false

    override suspend fun checkAvailability(): AiAvailability = availability

    override suspend fun downloadModelIfNeeded(): AiResult<Unit> {
        downloadCalled = true
        return downloadResult
    }

    override suspend fun generate(prompt: String, config: GenerationConfig): AiResult<String> {
        lastPrompt = prompt
        lastConfig = config
        return generateResult
    }

    override fun generateStream(prompt: String, config: GenerationConfig): Flow<AiResult<String>> {
        lastPrompt = prompt
        lastConfig = config
        return flowOf(*streamTokens.map { AiResult.Success(it) }.toTypedArray())
    }

    override suspend fun summarize(text: String, style: SummaryStyle): AiResult<String> {
        lastPrompt = text
        lastSummaryStyle = style
        return summarizeResult
    }

    override suspend fun getModelInfo(): String = modelInfo

    override fun release() {
        releaseCalled = true
    }
}
