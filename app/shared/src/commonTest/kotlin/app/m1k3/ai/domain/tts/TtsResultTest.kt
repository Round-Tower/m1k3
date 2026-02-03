package app.m1k3.ai.domain.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * TDD Tests for TtsResult sealed class
 *
 * TtsResult represents the outcome of a TTS synthesis operation.
 */
class TtsResultTest {

    // ===== Success Tests =====

    @Test
    fun `TtsResult Success contains audio`() {
        val audio = AudioSample(floatArrayOf(0.1f, 0.2f))
        val result = TtsResult.Success(audio)

        assertEquals(2, result.audio.samples.size)
    }

    @Test
    fun `TtsResult Success isSuccess returns true`() {
        val result = TtsResult.Success(AudioSample(floatArrayOf()))
        assertTrue(result.isSuccess)
    }

    @Test
    fun `TtsResult Success isError returns false`() {
        val result = TtsResult.Success(AudioSample(floatArrayOf()))
        assertFalse(result.isError)
    }

    @Test
    fun `TtsResult Success getOrNull returns audio`() {
        val audio = AudioSample(floatArrayOf(0.5f))
        val result = TtsResult.Success(audio)

        assertEquals(audio, result.getOrNull())
    }

    // ===== Error Tests =====

    @Test
    fun `TtsResult Error contains error code`() {
        val result = TtsResult.Error(TtsErrorCode.MODEL_NOT_LOADED, "Not loaded")
        assertEquals(TtsErrorCode.MODEL_NOT_LOADED, result.code)
    }

    @Test
    fun `TtsResult Error contains message`() {
        val result = TtsResult.Error(TtsErrorCode.SYNTHESIS_FAILED, "Synthesis failed")
        assertEquals("Synthesis failed", result.message)
    }

    @Test
    fun `TtsResult Error isSuccess returns false`() {
        val result = TtsResult.Error(TtsErrorCode.MODEL_NOT_LOADED, "Error")
        assertFalse(result.isSuccess)
    }

    @Test
    fun `TtsResult Error isError returns true`() {
        val result = TtsResult.Error(TtsErrorCode.MODEL_NOT_LOADED, "Error")
        assertTrue(result.isError)
    }

    @Test
    fun `TtsResult Error getOrNull returns null`() {
        val result = TtsResult.Error(TtsErrorCode.SYNTHESIS_FAILED, "Failed")
        assertNull(result.getOrNull())
    }

    // ===== Error Code Tests =====

    @Test
    fun `TtsErrorCode has MODEL_NOT_LOADED`() {
        val code = TtsErrorCode.MODEL_NOT_LOADED
        assertEquals("MODEL_NOT_LOADED", code.name)
    }

    @Test
    fun `TtsErrorCode has PHONEMIZATION_FAILED`() {
        val code = TtsErrorCode.PHONEMIZATION_FAILED
        assertEquals("PHONEMIZATION_FAILED", code.name)
    }

    @Test
    fun `TtsErrorCode has SYNTHESIS_FAILED`() {
        val code = TtsErrorCode.SYNTHESIS_FAILED
        assertEquals("SYNTHESIS_FAILED", code.name)
    }

    @Test
    fun `TtsErrorCode has INVALID_VOICE`() {
        val code = TtsErrorCode.INVALID_VOICE
        assertEquals("INVALID_VOICE", code.name)
    }

    @Test
    fun `TtsErrorCode has OUT_OF_MEMORY`() {
        val code = TtsErrorCode.OUT_OF_MEMORY
        assertEquals("OUT_OF_MEMORY", code.name)
    }

    @Test
    fun `TtsErrorCode has PLAYBACK_FAILED`() {
        val code = TtsErrorCode.PLAYBACK_FAILED
        assertEquals("PLAYBACK_FAILED", code.name)
    }

    // ===== Type Checking Tests =====

    @Test
    fun `TtsResult Success is instance of TtsResult`() {
        val result: TtsResult = TtsResult.Success(AudioSample(floatArrayOf()))
        assertIs<TtsResult>(result)
        assertIs<TtsResult.Success>(result)
    }

    @Test
    fun `TtsResult Error is instance of TtsResult`() {
        val result: TtsResult = TtsResult.Error(TtsErrorCode.SYNTHESIS_FAILED, "Error")
        assertIs<TtsResult>(result)
        assertIs<TtsResult.Error>(result)
    }

    // ===== Fold/Map Tests =====

    @Test
    fun `TtsResult fold returns success value for Success`() {
        val result = TtsResult.Success(AudioSample(floatArrayOf(0.1f)))

        val value = result.fold(
            onSuccess = { it.samples.size },
            onError = { -1 }
        )

        assertEquals(1, value)
    }

    @Test
    fun `TtsResult fold returns error value for Error`() {
        val result = TtsResult.Error(TtsErrorCode.MODEL_NOT_LOADED, "Not loaded")

        val value = result.fold(
            onSuccess = { it.samples.size },
            onError = { -1 }
        )

        assertEquals(-1, value)
    }
}
