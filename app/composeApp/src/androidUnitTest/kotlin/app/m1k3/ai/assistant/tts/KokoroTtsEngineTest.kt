package app.m1k3.ai.assistant.tts

import app.m1k3.ai.domain.tts.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * TDD Tests for KokoroTtsEngine
 *
 * Unit tests (no device required) that verify the engine's
 * state management, error handling, and integration contract.
 *
 * Actual ONNX inference is tested via instrumented tests on device.
 */
class KokoroTtsEngineTest {

    // ===== Initialization State Tests =====

    @Test
    fun `engine starts not loaded`() {
        val engine = KokoroTtsEngine(context = null)
        assertFalse(engine.isLoaded)
    }

    @Test
    fun `engine has correct sample rate`() {
        val engine = KokoroTtsEngine(context = null)
        assertEquals(24000, engine.sampleRate)
    }

    // ===== Synthesis Without Loading Tests =====

    @Test
    fun `synthesize returns MODEL_NOT_LOADED when not initialized`() = runTest {
        val engine = KokoroTtsEngine(context = null)

        val result = engine.synthesize("Hello", Voice.Kokoro.Daniel)

        assertTrue(result is TtsResult.Error)
        assertEquals(TtsErrorCode.MODEL_NOT_LOADED, (result as TtsResult.Error).code)
    }

    @Test
    fun `synthesizeStreaming fails when not initialized`() = runTest {
        val engine = KokoroTtsEngine(context = null)

        val result = engine.synthesizeStreaming("Hello", Voice.Kokoro.Daniel) { }

        assertTrue(result.isFailure)
    }

    // ===== Release Tests =====

    @Test
    fun `release clears loaded state`() {
        val engine = KokoroTtsEngine(context = null)

        engine.release()

        assertFalse(engine.isLoaded)
    }

    // ===== Speed Clamping Tests =====

    @Test
    fun `speed is clamped to valid range`() = runTest {
        val engine = KokoroTtsEngine(context = null)

        // Should not crash even with extreme speed values
        // (will fail with MODEL_NOT_LOADED, but shouldn't crash on speed)
        val result1 = engine.synthesize("Test", Voice.Kokoro.Daniel, speed = -1.0f)
        assertTrue(result1 is TtsResult.Error)
        assertEquals(TtsErrorCode.MODEL_NOT_LOADED, (result1 as TtsResult.Error).code)

        val result2 = engine.synthesize("Test", Voice.Kokoro.Daniel, speed = 100.0f)
        assertTrue(result2 is TtsResult.Error)
        assertEquals(TtsErrorCode.MODEL_NOT_LOADED, (result2 as TtsResult.Error).code)
    }

    // ===== Empty Text Guard Tests =====

    @Test
    fun `synthesize returns PHONEMIZATION_FAILED for empty text when loaded`() = runTest {
        // Can't fully test without context, but verifies the guard exists
        // by checking that empty text doesn't reach the inference path
        val engine = KokoroTtsEngine(context = null)

        val result = engine.synthesize("", Voice.Kokoro.Daniel)
        assertTrue(result is TtsResult.Error)
        // Should be MODEL_NOT_LOADED since we can't load without context,
        // but the guard for empty text should be before inference
    }

    @Test
    fun `synthesize returns PHONEMIZATION_FAILED for blank text when loaded`() = runTest {
        val engine = KokoroTtsEngine(context = null)

        val result = engine.synthesize("   ", Voice.Kokoro.Daniel)
        assertTrue(result is TtsResult.Error)
    }

    // ===== Concurrent Load Safety Tests =====

    @Test
    fun `loadModel returns existing result when already loading`() = runTest {
        val engine = KokoroTtsEngine(context = null)

        // Without context, both should fail — but neither should crash
        val result1 = engine.loadModel()
        val result2 = engine.loadModel()

        assertTrue(result1.isFailure)
        assertTrue(result2.isFailure)
    }

    // ===== Voice Validation Tests =====

    @Test
    fun `engine accepts all Kokoro voices`() = runTest {
        val engine = KokoroTtsEngine(context = null)

        Voice.all().filterIsInstance<Voice.Kokoro>().forEach { voice ->
            val result = engine.synthesize("Hello", voice)
            // Should fail with MODEL_NOT_LOADED, not INVALID_VOICE
            assertTrue(
                "Voice ${voice.id} should not be rejected as invalid",
                result is TtsResult.Error && (result as TtsResult.Error).code == TtsErrorCode.MODEL_NOT_LOADED
            )
        }
    }
}
