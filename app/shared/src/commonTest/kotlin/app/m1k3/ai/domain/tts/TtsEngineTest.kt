package app.m1k3.ai.domain.tts

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * TDD Tests for TtsEngine interface
 *
 * Tests the contract that all TTS engines must follow,
 * using a MockTtsEngine implementation.
 */
class TtsEngineTest {

    // ===== Mock Implementation =====

    private class MockTtsEngine : TtsEngine {
        override val sampleRate: Int = 24000
        override var isLoaded: Boolean = false
            private set

        var loadModelCalled = false
        var releaseCalled = false
        var lastSynthesizeText: String? = null
        var lastSynthesizeVoice: Voice? = null
        var shouldFailLoad = false
        var shouldFailSynthesize = false

        override suspend fun loadModel(): Result<Unit> {
            loadModelCalled = true
            return if (shouldFailLoad) {
                Result.failure(RuntimeException("Failed to load model"))
            } else {
                isLoaded = true
                Result.success(Unit)
            }
        }

        override suspend fun synthesize(
            text: String,
            voice: Voice,
            speed: Float
        ): TtsResult {
            lastSynthesizeText = text
            lastSynthesizeVoice = voice

            if (!isLoaded) {
                return TtsResult.Error(TtsErrorCode.MODEL_NOT_LOADED, "Model not loaded")
            }
            if (shouldFailSynthesize) {
                return TtsResult.Error(TtsErrorCode.SYNTHESIS_FAILED, "Synthesis failed")
            }

            // Generate mock audio (100ms of silence)
            val sampleCount = (sampleRate * 0.1).toInt()
            val samples = FloatArray(sampleCount) { 0.0f }
            return TtsResult.Success(AudioSample(samples, sampleRate))
        }

        override suspend fun synthesizeStreaming(
            text: String,
            voice: Voice,
            speed: Float,
            onChunk: (AudioSample) -> Unit
        ): Result<Unit> {
            if (!isLoaded) {
                return Result.failure(RuntimeException("Model not loaded"))
            }

            // Simulate chunked output - 3 chunks
            repeat(3) {
                val chunk = AudioSample(FloatArray(800) { 0.0f }, sampleRate)
                onChunk(chunk)
            }
            return Result.success(Unit)
        }

        override fun release() {
            releaseCalled = true
            isLoaded = false
        }
    }

    // ===== Load Model Tests =====

    @Test
    fun `loadModel sets isLoaded to true on success`() = runTest {
        val engine = MockTtsEngine()
        assertFalse(engine.isLoaded)

        engine.loadModel()

        assertTrue(engine.isLoaded)
    }

    @Test
    fun `loadModel returns success on success`() = runTest {
        val engine = MockTtsEngine()

        val result = engine.loadModel()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `loadModel returns failure when model fails to load`() = runTest {
        val engine = MockTtsEngine()
        engine.shouldFailLoad = true

        val result = engine.loadModel()

        assertTrue(result.isFailure)
        assertFalse(engine.isLoaded)
    }

    // ===== Synthesize Tests =====

    @Test
    fun `synthesize returns error when not loaded`() = runTest {
        val engine = MockTtsEngine()

        val result = engine.synthesize("Hello", Voice.Kokoro.Daniel)

        assertIs<TtsResult.Error>(result)
        assertEquals(TtsErrorCode.MODEL_NOT_LOADED, result.code)
    }

    @Test
    fun `synthesize returns audio when loaded`() = runTest {
        val engine = MockTtsEngine()
        engine.loadModel()

        val result = engine.synthesize("Hello world", Voice.Kokoro.Daniel)

        assertIs<TtsResult.Success>(result)
        assertTrue(result.audio.isNotEmpty)
        assertEquals(24000, result.audio.sampleRate)
    }

    @Test
    fun `synthesize uses provided voice`() = runTest {
        val engine = MockTtsEngine()
        engine.loadModel()

        engine.synthesize("Hello", Voice.Kokoro.Daniel)

        assertEquals(Voice.Kokoro.Daniel, engine.lastSynthesizeVoice)
    }

    @Test
    fun `synthesize stores text for processing`() = runTest {
        val engine = MockTtsEngine()
        engine.loadModel()

        engine.synthesize("Hello world", Voice.Kokoro.Daniel)

        assertEquals("Hello world", engine.lastSynthesizeText)
    }

    @Test
    fun `synthesize returns error on failure`() = runTest {
        val engine = MockTtsEngine()
        engine.loadModel()
        engine.shouldFailSynthesize = true

        val result = engine.synthesize("Hello", Voice.Kokoro.Daniel)

        assertIs<TtsResult.Error>(result)
        assertEquals(TtsErrorCode.SYNTHESIS_FAILED, result.code)
    }

    @Test
    fun `synthesize with default speed works`() = runTest {
        val engine = MockTtsEngine()
        engine.loadModel()

        val result = engine.synthesize("Hello", Voice.default)

        assertIs<TtsResult.Success>(result)
    }

    // ===== Streaming Tests =====

    @Test
    fun `synthesizeStreaming calls onChunk for each audio chunk`() = runTest {
        val engine = MockTtsEngine()
        engine.loadModel()

        val chunks = mutableListOf<AudioSample>()
        engine.synthesizeStreaming("Hello world", Voice.Kokoro.Daniel) { chunk ->
            chunks.add(chunk)
        }

        assertEquals(3, chunks.size)
        chunks.forEach { chunk ->
            assertTrue(chunk.isNotEmpty)
        }
    }

    @Test
    fun `synthesizeStreaming fails when not loaded`() = runTest {
        val engine = MockTtsEngine()

        val result = engine.synthesizeStreaming("Hello", Voice.Kokoro.Daniel) { }

        assertTrue(result.isFailure)
    }

    // ===== Release Tests =====

    @Test
    fun `release clears loaded state`() = runTest {
        val engine = MockTtsEngine()
        engine.loadModel()
        assertTrue(engine.isLoaded)

        engine.release()

        assertFalse(engine.isLoaded)
        assertTrue(engine.releaseCalled)
    }

    // ===== Sample Rate Tests =====

    @Test
    fun `engine reports correct sample rate`() {
        val engine = MockTtsEngine()
        assertEquals(24000, engine.sampleRate)
    }

    // ===== Default Voice Tests =====

    @Test
    fun `synthesize with Voice_default works`() = runTest {
        val engine = MockTtsEngine()
        engine.loadModel()

        val result = engine.synthesize("Test", Voice.default)

        assertIs<TtsResult.Success>(result)
        assertEquals(Voice.Kokoro.Daniel, engine.lastSynthesizeVoice)
    }
}
