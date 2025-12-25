package app.m1k3.ai.assistant.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * SmolLM2 Basic Functionality Tests
 *
 * Tests the real LlamaCppEngine with SmolLM2-135M-Instruct model.
 * Validates basic generation, streaming, and performance characteristics.
 *
 * Note: This test uses the REAL model and will take longer to run.
 * Use MockLlmEngine for fast UI tests.
 */
@RunWith(AndroidJUnit4::class)
class SmolLM2BasicTest {

    private lateinit var engine: LlamaCppEngine
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() = runTest(timeout = 30.seconds) {
        engine = LlamaCppEngine(context)
        engine.initialize()
    }

    @After
    fun teardown() {
        engine.release()
    }

    // ========================================
    // Test Suite A: Basic Generation
    // ========================================

    @Test
    fun testEngineInitializes() = runTest(timeout = 30.seconds) {
        // Engine should be initialized in setup()
        // Just verify we can create config
        val config = GenerationConfig(
            maxTokens = 10,
            temperature = 0.0f
        )

        assertTrue(config.maxTokens == 10)
    }

    @Test
    fun testBasicGeneration() = runTest(timeout = 30.seconds) {
        val config = GenerationConfig(
            maxTokens = 20,
            temperature = 0.0f,
            systemPrompt = "You are a helpful AI assistant."
        )

        val result = engine.generate("Say hello", config)

        // Verify response was generated
        assertTrue(result.text.isNotBlank(), "Should generate non-empty response")
        assertTrue(result.tokensGenerated > 0, "Should generate tokens")
        assertTrue(result.inferenceTimeMs > 0, "Should track inference time")
    }

    @Test
    fun testStreamingGeneration() = runTest(timeout = 30.seconds) {
        val config = GenerationConfig(
            maxTokens = 20,
            temperature = 0.0f
        )

        val tokens = mutableListOf<String>()
        engine.generateStreaming("Count to 5", config) { token ->
            tokens.add(token)
        }

        // Verify tokens were streamed
        assertTrue(tokens.isNotEmpty(), "Should stream tokens")
        assertTrue(tokens.size <= 20, "Should respect max tokens")

        // Verify tokens combine to form text
        val fullText = tokens.joinToString("")
        assertTrue(fullText.isNotBlank(), "Streamed tokens should form text")
    }

    // ========================================
    // Test Suite B: Response Quality
    // ========================================

    @Test
    fun testResponseCoherence() = runTest(timeout = 30.seconds) {
        val config = GenerationConfig(
            maxTokens = 30,
            temperature = 0.0f,
            systemPrompt = "You are a helpful AI assistant."
        )

        val result = engine.generate("What is 2+2?", config)

        // Basic coherence checks
        assertTrue(result.text.split(" ").size > 2, "Should generate multiple words")
        assertTrue(result.text.any { it.isLetterOrDigit() }, "Should contain alphanumeric characters")

        // Should not have excessive repetition (no more than 3x same word in a row)
        val words = result.text.split("\\s+".toRegex())
        var maxRepeat = 1
        var currentRepeat = 1
        for (i in 1 until words.size) {
            if (words[i] == words[i-1]) {
                currentRepeat++
                maxRepeat = maxOf(maxRepeat, currentRepeat)
            } else {
                currentRepeat = 1
            }
        }
        assertTrue(maxRepeat < 4, "Should not have excessive repetition (found $maxRepeat consecutive repeats)")
    }

    @Test
    fun testNoSpecialTokenLeakage() = runTest(timeout = 30.seconds) {
        val config = GenerationConfig(
            maxTokens = 30,
            temperature = 0.0f
        )

        val result = engine.generate("Hello", config)

        // Check for common special tokens that shouldn't appear in output
        val specialTokens = listOf("<|im_start|>", "<|im_end|>", "<|endoftext|>", "<s>", "</s>")
        for (token in specialTokens) {
            assertTrue(
                !result.text.contains(token, ignoreCase = false),
                "Should not leak special token: $token"
            )
        }
    }

    @Test
    fun testMaxTokensRespected() = runTest(timeout = 30.seconds) {
        val maxTokens = 10
        val config = GenerationConfig(
            maxTokens = maxTokens,
            temperature = 0.0f
        )

        val result = engine.generate("Tell me a long story", config)

        // Allow small margin (±5 tokens) for tokenization differences
        assertTrue(
            result.tokensGenerated <= maxTokens + 5,
            "Should respect max tokens (generated ${result.tokensGenerated}, max $maxTokens)"
        )
    }

    @Test
    fun testDeterminism() = runTest(timeout = 60.seconds) {
        val config = GenerationConfig(
            maxTokens = 15,
            temperature = 0.0f  // Greedy decoding should be deterministic
        )

        val prompt = "Count from 1 to 3"

        // Generate 3 times with same config
        val result1 = engine.generate(prompt, config)
        val result2 = engine.generate(prompt, config)
        val result3 = engine.generate(prompt, config)

        // All results should be identical with temperature=0
        assertEquals(result1.text, result2.text, "Results 1 and 2 should match with temp=0")
        assertEquals(result2.text, result3.text, "Results 2 and 3 should match with temp=0")
    }

    // ========================================
    // Test Suite C: Performance Benchmarks
    // ========================================

    @Test
    fun testInferenceSpeed() = runTest(timeout = 30.seconds) {
        val config = GenerationConfig(
            maxTokens = 30,
            temperature = 0.0f
        )

        val result = engine.generate("Count from 1 to 10", config)

        // Calculate tokens per second
        val tokensPerSecond = if (result.inferenceTimeMs > 0) {
            (result.tokensGenerated * 1000.0) / result.inferenceTimeMs
        } else {
            0.0
        }

        // On emulator, expect at least 10 tok/s (conservative target)
        // On device, expect 20-40 tok/s
        assertTrue(
            tokensPerSecond >= 10.0,
            "Should achieve at least 10 tok/s (got ${"%.2f".format(tokensPerSecond)} tok/s)"
        )

        println("✅ Inference speed: ${"%.2f".format(tokensPerSecond)} tok/s")
        println("   Tokens: ${result.tokensGenerated}, Time: ${result.inferenceTimeMs}ms")
    }

    @Test
    fun testOptimalMaxTokens() = runTest(timeout = 10.seconds) {
        val optimalTokens = engine.getOptimalMaxTokens()

        // Should return reasonable device-appropriate value
        assertTrue(optimalTokens > 0, "Should return positive max tokens")
        assertTrue(optimalTokens <= 512, "Should be reasonable for mobile (≤512)")

        println("✅ Optimal max tokens for device: $optimalTokens")
    }

    // ========================================
    // Test Suite D: Edge Cases
    // ========================================

    @Test
    fun testEmptyPrompt() = runTest(timeout = 30.seconds) {
        val config = GenerationConfig(
            maxTokens = 10,
            temperature = 0.0f
        )

        val result = engine.generate("", config)

        // Should handle empty prompt gracefully (may return empty or default response)
        assertTrue(result.tokensGenerated >= 0, "Should handle empty prompt")
    }

    @Test
    fun testZeroMaxTokens() = runTest(timeout = 30.seconds) {
        val config = GenerationConfig(
            maxTokens = 0,
            temperature = 0.0f
        )

        val result = engine.generate("Hello", config)

        // Should return empty result
        assertEquals("", result.text, "Should return empty text with maxTokens=0")
        assertEquals(0, result.tokensGenerated, "Should generate 0 tokens")
    }

    @Test
    fun testVeryLongPrompt() = runTest(timeout = 30.seconds) {
        // Create a long prompt (1000+ characters)
        val longPrompt = "This is a test. ".repeat(100)

        val config = GenerationConfig(
            maxTokens = 10,
            temperature = 0.0f
        )

        val result = engine.generate(longPrompt, config)

        // Should handle long prompt without crashing
        assertTrue(result.tokensGenerated >= 0, "Should handle long prompt")
    }
}
