package app.m1k3.ai.assistant.ai.ondevice

import app.m1k3.ai.domain.ai.GenerationConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * TDD Tests for OnDeviceAi interface using MockOnDeviceAi.
 *
 * 🔴 RED Phase: These tests are written FIRST before implementation.
 * They should fail initially because OnDeviceAi and MockOnDeviceAi don't exist yet.
 */
class OnDeviceAiTest {

    private lateinit var mockAi: MockOnDeviceAi

    @BeforeTest
    fun setup() {
        mockAi = MockOnDeviceAi()
    }

    // === checkAvailability() Tests ===

    @Test
    fun `checkAvailability returns Available when configured`() = runTest {
        mockAi.setAvailability(AiAvailability.Available)

        val availability = mockAi.checkAvailability()

        assertEquals(AiAvailability.Available, availability)
    }

    @Test
    fun `checkAvailability returns Downloading when configured`() = runTest {
        mockAi.setAvailability(AiAvailability.Downloading)

        val availability = mockAi.checkAvailability()

        assertEquals(AiAvailability.Downloading, availability)
    }

    @Test
    fun `checkAvailability returns Unavailable with reason when configured`() = runTest {
        val expected = AiAvailability.Unavailable(AiAvailability.UnavailableReason.DEVICE_NOT_SUPPORTED)
        mockAi.setAvailability(expected)

        val availability = mockAi.checkAvailability()

        assertIs<AiAvailability.Unavailable>(availability)
        assertEquals(AiAvailability.UnavailableReason.DEVICE_NOT_SUPPORTED, availability.reason)
    }

    @Test
    fun `checkAvailability returns Fallback with engine name when configured`() = runTest {
        val expected = AiAvailability.Fallback("SmolLM2-135M")
        mockAi.setAvailability(expected)

        val availability = mockAi.checkAvailability()

        assertIs<AiAvailability.Fallback>(availability)
        assertEquals("SmolLM2-135M", availability.engineName)
    }

    // === generate() Tests ===

    @Test
    fun `generate returns Success with configured response`() = runTest {
        mockAi.setAvailability(AiAvailability.Available)
        mockAi.setResponse("Hello from AI")

        val result = mockAi.generate("Hi", GenerationConfig())

        assertIs<AiResult.Success<String>>(result)
        assertEquals("Hello from AI", result.data)
    }

    @Test
    fun `generate returns Error when unavailable`() = runTest {
        mockAi.setAvailability(AiAvailability.Unavailable(AiAvailability.UnavailableReason.DEVICE_NOT_SUPPORTED))

        val result = mockAi.generate("Hi", GenerationConfig())

        assertIs<AiResult.Error>(result)
        assertEquals(AiErrorCode.UNAVAILABLE, result.code)
    }

    @Test
    fun `generate respects GenerationConfig maxTokens`() = runTest {
        mockAi.setAvailability(AiAvailability.Available)
        mockAi.setResponse("This is a response")

        val config = GenerationConfig(maxTokens = 100)
        mockAi.generate("Test", config)

        assertEquals(100, mockAi.lastConfig?.maxTokens)
    }

    @Test
    fun `generate respects GenerationConfig temperature`() = runTest {
        mockAi.setAvailability(AiAvailability.Available)
        mockAi.setResponse("Response")

        val config = GenerationConfig(temperature = 0.5f)
        mockAi.generate("Test", config)

        assertEquals(0.5f, mockAi.lastConfig?.temperature)
    }

    @Test
    fun `generate respects GenerationConfig knowledgeContext`() = runTest {
        mockAi.setAvailability(AiAvailability.Available)
        mockAi.setResponse("Response")

        val config = GenerationConfig(knowledgeContext = "RAG context here")
        mockAi.generate("Test", config)

        assertEquals("RAG context here", mockAi.lastConfig?.knowledgeContext)
    }

    @Test
    fun `generate records prompt for verification`() = runTest {
        mockAi.setAvailability(AiAvailability.Available)
        mockAi.setResponse("Response")

        mockAi.generate("User question", GenerationConfig())

        assertEquals("User question", mockAi.lastPrompt)
    }

    @Test
    fun `generate works with Fallback availability`() = runTest {
        mockAi.setAvailability(AiAvailability.Fallback("SmolLM2"))
        mockAi.setResponse("Fallback response")

        val result = mockAi.generate("Hi", GenerationConfig())

        assertIs<AiResult.Success<String>>(result)
        assertEquals("Fallback response", result.data)
    }

    // === generateStream() Tests ===

    @Test
    fun `generateStream emits tokens progressively`() = runTest {
        mockAi.setAvailability(AiAvailability.Available)
        mockAi.setStreamTokens(listOf("Hello", " ", "world", "!"))

        val tokens = mockAi.generateStream("Hi", GenerationConfig()).toList()

        assertEquals(4, tokens.size)
        assertTrue(tokens.all { it is AiResult.Success })
    }

    @Test
    fun `generateStream emits tokens in order`() = runTest {
        mockAi.setAvailability(AiAvailability.Available)
        mockAi.setStreamTokens(listOf("First", "Second", "Third"))

        val tokens = mockAi.generateStream("Hi", GenerationConfig())
            .toList()
            .filterIsInstance<AiResult.Success<String>>()
            .map { it.data }

        assertEquals(listOf("First", "Second", "Third"), tokens)
    }

    @Test
    fun `generateStream returns Error when unavailable`() = runTest {
        mockAi.setAvailability(AiAvailability.Unavailable(AiAvailability.UnavailableReason.AI_DISABLED))

        val results = mockAi.generateStream("Hi", GenerationConfig()).toList()

        assertTrue(results.isNotEmpty())
        assertIs<AiResult.Error>(results.first())
    }

    // === summarize() Tests ===

    @Test
    fun `summarize returns Success with configured response`() = runTest {
        mockAi.setAvailability(AiAvailability.Available)
        mockAi.setResponse("Brief summary")

        val result = mockAi.summarize("Long text...", SummaryStyle.BRIEF)

        assertIs<AiResult.Success<String>>(result)
        assertEquals("Brief summary", result.data)
    }

    @Test
    fun `summarize records style for verification`() = runTest {
        mockAi.setAvailability(AiAvailability.Available)
        mockAi.setResponse("Summary")

        mockAi.summarize("Text", SummaryStyle.BULLETS)

        assertEquals(SummaryStyle.BULLETS, mockAi.lastSummaryStyle)
    }

    @Test
    fun `summarize returns Error when unavailable`() = runTest {
        mockAi.setAvailability(AiAvailability.Unavailable(AiAvailability.UnavailableReason.QUOTA_EXCEEDED))

        val result = mockAi.summarize("Text", SummaryStyle.DETAILED)

        assertIs<AiResult.Error>(result)
    }

    // === getModelInfo() Tests ===

    @Test
    fun `getModelInfo returns configured info`() = runTest {
        mockAi.setModelInfo("Gemini Nano (on-device)")

        val info = mockAi.getModelInfo()

        assertEquals("Gemini Nano (on-device)", info)
    }

    @Test
    fun `getModelInfo returns default when not configured`() = runTest {
        val info = mockAi.getModelInfo()

        assertTrue(info.isNotEmpty())
    }

    // === downloadModelIfNeeded() Tests ===

    @Test
    fun `downloadModelIfNeeded returns Success when available`() = runTest {
        mockAi.setAvailability(AiAvailability.Available)

        val result = mockAi.downloadModelIfNeeded()

        assertIs<AiResult.Success<Unit>>(result)
    }

    @Test
    fun `downloadModelIfNeeded returns Error when unavailable`() = runTest {
        mockAi.setAvailability(AiAvailability.Unavailable(AiAvailability.UnavailableReason.DEVICE_NOT_SUPPORTED))

        val result = mockAi.downloadModelIfNeeded()

        assertIs<AiResult.Error>(result)
    }

    // === Call Counting Tests ===

    @Test
    fun `generate increments call count`() = runTest {
        mockAi.setAvailability(AiAvailability.Available)
        mockAi.setResponse("Response")

        assertEquals(0, mockAi.generateCallCount)

        mockAi.generate("1", GenerationConfig())
        assertEquals(1, mockAi.generateCallCount)

        mockAi.generate("2", GenerationConfig())
        assertEquals(2, mockAi.generateCallCount)
    }

    @Test
    fun `summarize increments call count`() = runTest {
        mockAi.setAvailability(AiAvailability.Available)
        mockAi.setResponse("Summary")

        assertEquals(0, mockAi.summarizeCallCount)

        mockAi.summarize("1", SummaryStyle.BRIEF)
        assertEquals(1, mockAi.summarizeCallCount)

        mockAi.summarize("2", SummaryStyle.BULLETS)
        assertEquals(2, mockAi.summarizeCallCount)
    }

    // === Reset Tests ===

    @Test
    fun `reset clears all state`() = runTest {
        mockAi.setAvailability(AiAvailability.Available)
        mockAi.setResponse("Response")
        mockAi.generate("Test", GenerationConfig())

        mockAi.reset()

        assertEquals(0, mockAi.generateCallCount)
        assertEquals(null, mockAi.lastPrompt)
        assertEquals(null, mockAi.lastConfig)
    }
}
