package app.m1k3.ai.domain.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD Tests for AudioSample data class
 *
 * AudioSample represents synthesized audio output from TTS.
 */
class AudioSampleTest {

    // ===== Basic Construction Tests =====

    @Test
    fun `AudioSample stores samples correctly`() {
        val samples = floatArrayOf(0.1f, 0.2f, 0.3f)
        val audio = AudioSample(samples)

        assertEquals(3, audio.samples.size)
        assertEquals(0.1f, audio.samples[0])
        assertEquals(0.2f, audio.samples[1])
        assertEquals(0.3f, audio.samples[2])
    }

    @Test
    fun `AudioSample default sample rate is 24000`() {
        val audio = AudioSample(floatArrayOf())
        assertEquals(24000, audio.sampleRate)
    }

    @Test
    fun `AudioSample default channels is 1 mono`() {
        val audio = AudioSample(floatArrayOf())
        assertEquals(1, audio.channels)
    }

    // ===== Duration Calculation Tests =====

    @Test
    fun `AudioSample calculates duration for 1 second correctly`() {
        // 24000 samples at 24kHz = 1 second = 1000ms
        val samples = FloatArray(24000) { 0.0f }
        val audio = AudioSample(samples, sampleRate = 24000)

        assertEquals(1000L, audio.durationMs)
    }

    @Test
    fun `AudioSample calculates duration for 500ms correctly`() {
        // 12000 samples at 24kHz = 0.5 seconds = 500ms
        val samples = FloatArray(12000) { 0.0f }
        val audio = AudioSample(samples, sampleRate = 24000)

        assertEquals(500L, audio.durationMs)
    }

    @Test
    fun `AudioSample calculates duration for empty array as 0`() {
        val audio = AudioSample(floatArrayOf())
        assertEquals(0L, audio.durationMs)
    }

    @Test
    fun `AudioSample calculates duration with custom sample rate`() {
        // 22050 samples at 22050Hz = 1 second = 1000ms
        val samples = FloatArray(22050) { 0.0f }
        val audio = AudioSample(samples, sampleRate = 22050)

        assertEquals(1000L, audio.durationMs)
    }

    // ===== Custom Parameters Tests =====

    @Test
    fun `AudioSample accepts custom sample rate`() {
        val audio = AudioSample(floatArrayOf(), sampleRate = 22050)
        assertEquals(22050, audio.sampleRate)
    }

    @Test
    fun `AudioSample accepts custom channels`() {
        val audio = AudioSample(floatArrayOf(), channels = 2)
        assertEquals(2, audio.channels)
    }

    // ===== Edge Cases =====

    @Test
    fun `AudioSample handles large arrays`() {
        // 5 minutes of audio at 24kHz = 7,200,000 samples
        val samples = FloatArray(7_200_000) { 0.0f }
        val audio = AudioSample(samples)

        assertEquals(300_000L, audio.durationMs) // 5 minutes = 300,000ms
    }

    @Test
    fun `AudioSample preserves sample values`() {
        val samples = floatArrayOf(-1.0f, -0.5f, 0.0f, 0.5f, 1.0f)
        val audio = AudioSample(samples)

        assertEquals(-1.0f, audio.samples[0])
        assertEquals(0.0f, audio.samples[2])
        assertEquals(1.0f, audio.samples[4])
    }

    // ===== Utility Tests =====

    @Test
    fun `AudioSample isEmpty returns true for empty samples`() {
        val audio = AudioSample(floatArrayOf())
        assertTrue(audio.isEmpty)
    }

    @Test
    fun `AudioSample isEmpty returns false for non-empty samples`() {
        val audio = AudioSample(floatArrayOf(0.1f))
        assertTrue(!audio.isEmpty)
    }
}
