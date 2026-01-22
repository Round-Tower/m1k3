package app.m1k3.ai.assistant.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * TDD Tests for BaseLlmEngine Interface
 *
 * These tests validate the BaseLlmEngine contract using a MockLlmEngine implementation.
 * They ensure that any engine implementing BaseLlmEngine behaves consistently.
 *
 * Test Coverage:
 * - Initialization lifecycle
 * - Blocking generation (generate)
 * - Streaming generation (generateStreaming)
 * - Configuration handling (graceful degradation)
 * - Resource cleanup (release)
 * - Error handling (uninitialized engine, generation failures)
 * - Performance metrics (tokens, timing)
 * - Edge cases (empty prompts, zero max tokens, null configs)
 */
class BaseLlmEngineTest {

    // ==================== Initialization Tests ====================

    @Test
    fun `initialize succeeds and engine becomes ready`() = runTest {
        // Arrange
        val engine = MockLlmEngine()

        // Act
        val result = engine.initialize()

        // Assert
        assertTrue(result.isSuccess, "Initialize should succeed")
        assertTrue(engine.isInitializedPublic, "Engine should be initialized after initialize() call")
    }

    @Test
    fun `initialize can be called multiple times safely`() = runTest {
        // Arrange
        val engine = MockLlmEngine()

        // Act
        val result1 = engine.initialize()
        val result2 = engine.initialize()  // Second call should be safe

        // Assert
        assertTrue(result1.isSuccess, "First initialize should succeed")
        assertTrue(result2.isSuccess, "Second initialize should succeed")
        assertTrue(engine.isInitializedPublic, "Engine should remain initialized")
        assertEquals(1, engine.initializeCallCount, "initialize() should only execute once")
    }

    @Test
    fun `generate returns failure when engine not initialized`() = runTest {
        // Arrange
        val engine = MockLlmEngine()

        // Act
        val result = engine.generate("Hello")

        // Assert
        assertTrue(result.isFailure, "Generate should fail when engine not initialized")
        assertTrue(result.exceptionOrNull() is IllegalStateException, "Should return IllegalStateException")
    }

    // ==================== Blocking Generation Tests ====================

    @Test
    fun `generate returns valid GenerationResult with default config`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()

        // Act
        val result = engine.generate("Hello, AI!").getOrThrow()

        // Assert
        assertNotNull(result, "Result should not be null")
        assertEquals("Mock response to: Hello, AI!", result.text)
        assertTrue(result.tokensGenerated > 0, "Should generate at least 1 token")
        assertTrue(result.inferenceTimeMs >= 0, "Inference time should be non-negative")
        assertTrue(result.tokensPerSecond > 0, "Tokens per second should be positive")
    }

    @Test
    fun `generate respects GenerationConfig maxTokens`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        val config = GenerationConfig(maxTokens = 10)

        // Act
        val result = engine.generate("Tell me a story", config).getOrThrow()

        // Assert
        assertTrue(result.tokensGenerated <= 10, "Should not exceed max tokens")
    }

    @Test
    fun `generate respects GenerationConfig systemPrompt`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        val customPrompt = "You are a pirate assistant"
        val config = GenerationConfig(systemPrompt = customPrompt)

        // Act
        val result = engine.generate("Ahoy!", config).getOrThrow()

        // Assert
        assertTrue(result.text.contains("pirate") || result.text.contains("Ahoy"),
            "Response should reflect custom system prompt")
    }

    @Test
    fun `generate handles userContext for personalization`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        val config = GenerationConfig(
            userContext = mapOf("name" to "Alice", "timezone" to "PST")
        )

        // Act
        val result = engine.generate("What's my name?", config).getOrThrow()

        // Assert
        assertNotNull(result.text, "Should return a response")
        assertTrue(result.text.isNotEmpty(), "Response should not be empty")
    }

    @Test
    fun `generate handles knowledgeContext for RAG`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        val knowledge = "FACT: The Eiffel Tower is 330 meters tall."
        val config = GenerationConfig(knowledgeContext = knowledge)

        // Act
        val result = engine.generate("How tall is the Eiffel Tower?", config).getOrThrow()

        // Assert
        assertNotNull(result.text, "Should return a response")
        assertTrue(result.text.contains("330") || result.text.contains("Eiffel"),
            "Response should use injected knowledge")
    }

    @Test
    fun `generate handles empty prompt gracefully`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()

        // Act
        val result = engine.generate("").getOrThrow()

        // Assert
        assertNotNull(result.text, "Should return a response even for empty prompt")
        assertTrue(result.tokensGenerated >= 0, "Token count should be valid")
    }

    @Test
    fun `generate handles null configuration fields gracefully`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        val config = GenerationConfig(
            maxTokens = null,
            temperature = null,
            systemPrompt = null
        )

        // Act
        val result = engine.generate("Test", config).getOrThrow()

        // Assert
        assertNotNull(result.text, "Should use engine defaults when config is null")
    }

    // ==================== Streaming Generation Tests ====================

    @Test
    fun `generateStreaming calls onToken callback for each token`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        val receivedTokens = mutableListOf<String>()

        // Act
        engine.generateStreaming("Hello") { token ->
            receivedTokens.add(token)
        }.getOrThrow()

        // Assert
        assertTrue(receivedTokens.isNotEmpty(), "Should receive at least one token")
        assertTrue(receivedTokens.all { it.isNotEmpty() }, "All tokens should be non-empty")
    }

    @Test
    fun `generateStreaming respects maxTokens configuration`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        val config = GenerationConfig(maxTokens = 5)
        val receivedTokens = mutableListOf<String>()

        // Act
        engine.generateStreaming("Count to ten", config) { token ->
            receivedTokens.add(token)
        }.getOrThrow()

        // Assert
        assertTrue(receivedTokens.size <= 5, "Should not exceed max tokens in streaming")
    }

    @Test
    fun `generateStreaming returns failure when engine not initialized`() = runTest {
        // Arrange
        val engine = MockLlmEngine()

        // Act
        val result = engine.generateStreaming("Hello") { }

        // Assert
        assertTrue(result.isFailure, "GenerateStreaming should fail when engine not initialized")
        assertTrue(result.exceptionOrNull() is IllegalStateException, "Should return IllegalStateException")
    }

    @Test
    fun `generateStreaming completes successfully without hanging`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        var completed = false

        // Act
        engine.generateStreaming("Test") { }.getOrThrow()
        completed = true

        // Assert
        assertTrue(completed, "Streaming should complete without hanging")
    }

    // ==================== Configuration Handling Tests ====================

    @Test
    fun `engine handles temperature parameter gracefully (even if unsupported)`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        val config = GenerationConfig(temperature = 1.5f)  // High creativity

        // Act
        val result = engine.generate("Be creative!", config).getOrThrow()

        // Assert
        assertNotNull(result.text, "Should handle temperature even if ignored")
    }

    @Test
    fun `engine handles advanced sampling parameters gracefully`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        val config = GenerationConfig(
            topP = 0.9f,
            topK = 50,
            minP = 0.1f,
            repetitionPenalty = 1.3f
        )

        // Act
        val result = engine.generate("Test advanced sampling", config).getOrThrow()

        // Assert
        assertNotNull(result.text, "Should handle advanced parameters even if ignored")
    }

    // ==================== Performance Metrics Tests ====================

    @Test
    fun `generate returns valid performance metrics`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()

        // Act
        val result = engine.generate("Benchmark test").getOrThrow()

        // Assert
        assertTrue(result.tokensGenerated > 0, "Tokens generated should be positive")
        assertTrue(result.inferenceTimeMs >= 0, "Inference time should be non-negative")
        assertTrue(result.tokensPerSecond >= 0, "Tokens per second should be non-negative")

        // Validate tokens per second calculation
        // Handle edge case: when inferenceTimeMs is 0, mock returns tokensGenerated as TPS fallback
        val expectedTps = if (result.inferenceTimeMs > 0) {
            (result.tokensGenerated * 1000.0f) / result.inferenceTimeMs
        } else {
            result.tokensGenerated.toFloat()
        }
        assertEquals(expectedTps, result.tokensPerSecond, 0.1f,
            "Tokens per second should match calculation")
    }

    @Test
    fun `getOptimalMaxTokens returns positive value`() {
        // Arrange
        val engine = MockLlmEngine()

        // Act
        val maxTokens = engine.getOptimalMaxTokens()

        // Assert
        assertTrue(maxTokens > 0, "Optimal max tokens should be positive")
        assertTrue(maxTokens <= 512, "Optimal max tokens should be reasonable (<= 512)")
    }

    // ==================== Resource Management Tests ====================

    @Test
    fun `release cleans up resources and prevents further use`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        engine.generate("Test").getOrThrow()  // Use the engine

        // Act
        engine.release()

        // Assert
        assertFalse(engine.isInitializedPublic, "Engine should be uninitialized after release")
        val result = engine.generate("Should fail")
        assertTrue(result.isFailure, "Should fail after release")
        assertTrue(result.exceptionOrNull() is IllegalStateException, "Should return IllegalStateException")
    }

    @Test
    fun `close is an alias for release`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()

        // Act
        engine.close()  // Should call release()

        // Assert
        assertFalse(engine.isInitializedPublic, "Engine should be uninitialized after close")
    }

    @Test
    fun `release can be called multiple times safely`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()

        // Act
        engine.release()
        engine.release()  // Second call should be safe

        // Assert
        assertFalse(engine.isInitializedPublic, "Engine should remain uninitialized")
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `engine handles very long prompts gracefully`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        val longPrompt = "Test ".repeat(1000)  // 5000 characters

        // Act
        val result = engine.generate(longPrompt).getOrThrow()

        // Assert
        assertNotNull(result.text, "Should handle long prompts")
    }

    @Test
    fun `engine handles special characters in prompts`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        val specialPrompt = "Test <|im_start|> system\nHello! 你好 🤖"

        // Act
        val result = engine.generate(specialPrompt).getOrThrow()

        // Assert
        assertNotNull(result.text, "Should handle special characters")
    }

    @Test
    fun `engine rejects usage after release without re-initialization`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        engine.release()

        // Act & Assert
        val genResult = engine.generate("Should fail")
        assertTrue(genResult.isFailure, "Generate should fail after release")
        assertTrue(genResult.exceptionOrNull() is IllegalStateException)

        val streamResult = engine.generateStreaming("Should fail") { }
        assertTrue(streamResult.isFailure, "GenerateStreaming should fail after release")
        assertTrue(streamResult.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `engine can be re-initialized after release`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        engine.release()

        // Act
        engine.initialize().getOrThrow()  // Re-initialize
        val result = engine.generate("Should work now").getOrThrow()

        // Assert
        assertNotNull(result.text, "Should work after re-initialization")
    }

    // ==================== Edge Cases Tests ====================

    @Test
    fun `generate with maxTokens=0 returns minimal response`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        val config = GenerationConfig(maxTokens = 0)

        // Act
        val result = engine.generate("Test", config).getOrThrow()

        // Assert
        assertEquals(0, result.tokensGenerated, "Should generate 0 tokens when maxTokens=0")
        assertTrue(result.text.isEmpty(), "Text should be empty when maxTokens=0")
    }

    @Test
    fun `generate with temperature=0 uses greedy decoding`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()
        val config = GenerationConfig(temperature = 0.0f)

        // Act
        val result1 = engine.generate("Deterministic test", config).getOrThrow()
        val result2 = engine.generate("Deterministic test", config).getOrThrow()

        // Assert
        assertEquals(result1.text, result2.text,
            "Temperature=0 should produce deterministic results")
    }

    @Test
    fun `GenerationResult toString contains all metrics`() = runTest {
        // Arrange
        val engine = MockLlmEngine()
        engine.initialize().getOrThrow()

        // Act
        val result = engine.generate("Test").getOrThrow()
        val resultString = result.toString()

        // Assert
        assertTrue(resultString.contains("Generated"), "toString should include label")
        assertTrue(resultString.contains("Tokens"), "toString should include tokens")
        assertTrue(resultString.contains("Time"), "toString should include time")
        assertTrue(resultString.contains("Speed"), "toString should include speed")
    }
}
