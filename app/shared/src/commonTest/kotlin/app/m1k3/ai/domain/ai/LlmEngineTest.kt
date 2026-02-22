package app.m1k3.ai.domain.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for LlmEngine interface and GenerationResult.
 */
class LlmEngineTest {

    private class MockLlmEngine : LlmEngine {
        var initialized = false
        var released = false

        override suspend fun initialize(): Result<Unit> {
            initialized = true
            return Result.success(Unit)
        }

        override suspend fun generate(
            prompt: String,
            config: GenerationConfig
        ): Result<GenerationResult> {
            if (!initialized) {
                return Result.failure(IllegalStateException("Not initialized"))
            }
            return Result.success(
                GenerationResult(
                    text = "Mock response to: $prompt",
                    tokensGenerated = 10,
                    inferenceTimeMs = 100L,
                    tokensPerSecond = 100f
                )
            )
        }

        override suspend fun generateStreaming(
            prompt: String,
            config: GenerationConfig,
            onToken: (String) -> Unit
        ): Result<Unit> {
            if (!initialized) {
                return Result.failure(IllegalStateException("Not initialized"))
            }
            listOf("Hello", " ", "world").forEach { onToken(it) }
            return Result.success(Unit)
        }

        override fun getOptimalMaxTokens(): Int = 256

        override fun release() {
            released = true
            initialized = false
        }
    }

    @Test
    fun `initialize returns success`() = runSuspendTest {
        val engine = MockLlmEngine()

        val result = engine.initialize()

        assertTrue(result.isSuccess)
        assertTrue(engine.initialized)
    }

    @Test
    fun `generate returns result when initialized`() = runSuspendTest {
        val engine = MockLlmEngine()
        engine.initialize()

        val result = engine.generate("Hello", GenerationConfig())

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.text.contains("Hello"))
    }

    @Test
    fun `generate fails when not initialized`() = runSuspendTest {
        val engine = MockLlmEngine()

        val result = engine.generate("Hello", GenerationConfig())

        assertTrue(result.isFailure)
    }

    @Test
    fun `generateStreaming calls onToken callback`() = runSuspendTest {
        val engine = MockLlmEngine()
        engine.initialize()

        val tokens = mutableListOf<String>()
        val result = engine.generateStreaming("Hello", GenerationConfig()) { token ->
            tokens.add(token)
        }

        assertTrue(result.isSuccess)
        assertEquals(listOf("Hello", " ", "world"), tokens)
    }

    @Test
    fun `release clears initialized state`() = runSuspendTest {
        val engine = MockLlmEngine()
        engine.initialize()
        assertTrue(engine.initialized)

        engine.release()

        assertTrue(engine.released)
        assertTrue(!engine.initialized)
    }

    @Test
    fun `close calls release`() = runSuspendTest {
        val engine = MockLlmEngine()
        engine.initialize()

        engine.close()

        assertTrue(engine.released)
    }

    @Test
    fun `GenerationResult toString formats correctly`() {
        val result = GenerationResult(
            text = "Hello",
            tokensGenerated = 5,
            inferenceTimeMs = 100L,
            tokensPerSecond = 50f
        )

        val str = result.toString()

        assertTrue(str.contains("Hello"))
        assertTrue(str.contains("5"))
        assertTrue(str.contains("100"))
    }

    @Test
    fun `GenerationResult tokensPerSecond calculated property works`() {
        val result = GenerationResult(
            text = "Test",
            tokensGenerated = 100,
            inferenceTimeMs = 1000L,
            tokensPerSecond = 100f
        )

        assertEquals(100f, result.tokensPerSecond)
    }

    private fun runSuspendTest(block: suspend () -> Unit) {
        kotlinx.coroutines.test.runTest { block() }
    }
}
