package app.m1k3.ai.assistant.tts

import app.m1k3.ai.domain.tts.AudioSample
import app.m1k3.ai.domain.tts.TtsEffect
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD Tests for AudioEffectsProcessor
 *
 * Tests DSP effects: None, Intercom, RadioChat, Compression, Normalization, Chain.
 * All effects operate on float PCM samples in [-1.0, 1.0] range.
 */
class AudioEffectsProcessorTest {

    private val processor = AudioEffectsProcessor()

    // ===== Test Data =====

    private fun sineWave(frequency: Float = 440f, sampleRate: Int = 24000, durationMs: Int = 100): AudioSample {
        val numSamples = (sampleRate * durationMs) / 1000
        val samples = FloatArray(numSamples) { i ->
            val t = i.toFloat() / sampleRate
            kotlin.math.sin(2.0 * Math.PI * frequency * t).toFloat()
        }
        return AudioSample(samples, sampleRate)
    }

    private fun constantSignal(value: Float = 0.5f, size: Int = 2400): AudioSample {
        return AudioSample(FloatArray(size) { value }, 24000)
    }

    // ===== None Effect =====

    @Test
    fun `None effect returns identical audio`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.None)

        assertEquals(input.samples.size, result.samples.size)
        for (i in input.samples.indices) {
            assertEquals(input.samples[i], result.samples[i], 0.0001f)
        }
    }

    @Test
    fun `None effect preserves sample rate and channels`() {
        val input = AudioSample(floatArrayOf(0.1f, 0.2f), sampleRate = 22050, channels = 2)
        val result = processor.apply(input, TtsEffect.None)

        assertEquals(22050, result.sampleRate)
        assertEquals(2, result.channels)
    }

    // ===== Normalization Effect =====

    @Test
    fun `Normalization scales peak to target level`() {
        val input = constantSignal(value = 0.25f)
        val result = processor.apply(input, TtsEffect.Normalization(level = 0.8f))

        val peak = result.samples.maxOf { abs(it) }
        assertEquals(0.8f, peak, 0.01f)
    }

    @Test
    fun `Normalization handles silence gracefully`() {
        val input = AudioSample(FloatArray(100) { 0f }, 24000)
        val result = processor.apply(input, TtsEffect.Normalization(level = 0.8f))

        // Should not crash or produce NaN
        assertTrue(result.samples.all { !it.isNaN() })
        assertTrue(result.samples.all { it == 0f })
    }

    @Test
    fun `Normalization preserves sample count`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.Normalization())

        assertEquals(input.samples.size, result.samples.size)
    }

    // ===== Compression Effect =====

    @Test
    fun `Compression reduces dynamic range`() {
        // Mix of quiet and loud samples
        val samples = FloatArray(200) { i ->
            if (i < 100) 0.2f else 0.9f
        }
        val input = AudioSample(samples, 24000)
        val result = processor.apply(input, TtsEffect.Compression(threshold = 0.5f, ratio = 0.3f))

        // Loud samples should be reduced
        val loudOutput = result.samples.slice(100 until 200).average()
        assertTrue(loudOutput < 0.9f, "Compression should reduce loud samples")

        // Quiet samples should remain mostly unchanged
        val quietOutput = result.samples.slice(0 until 100).average()
        assertTrue(quietOutput >= 0.15f, "Compression should not significantly affect quiet samples")
    }

    @Test
    fun `Compression preserves sample count`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.Compression())

        assertEquals(input.samples.size, result.samples.size)
    }

    // ===== Intercom Effect =====

    @Test
    fun `Intercom effect modifies audio`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.Intercom(distortion = 0.1f))

        // Should produce different output than input
        var different = false
        for (i in input.samples.indices) {
            if (abs(input.samples[i] - result.samples[i]) > 0.001f) {
                different = true
                break
            }
        }
        assertTrue(different, "Intercom should modify the audio signal")
    }

    @Test
    fun `Intercom preserves sample count`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.Intercom())

        assertEquals(input.samples.size, result.samples.size)
    }

    @Test
    fun `Intercom keeps samples in valid range`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.Intercom(distortion = 0.5f))

        assertTrue(result.samples.all { it in -1.0f..1.0f },
            "All samples should be in [-1, 1] range")
    }

    // ===== RadioChat Effect =====

    @Test
    fun `RadioChat effect modifies audio`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.RadioChat())

        var different = false
        for (i in input.samples.indices) {
            if (abs(input.samples[i] - result.samples[i]) > 0.001f) {
                different = true
                break
            }
        }
        assertTrue(different, "RadioChat should modify the audio signal")
    }

    @Test
    fun `RadioChat keeps samples in valid range`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.RadioChat())

        assertTrue(result.samples.all { it in -1.0f..1.0f },
            "All samples should be in [-1, 1] range")
    }

    @Test
    fun `RadioChat preserves sample rate`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.RadioChat())

        assertEquals(input.sampleRate, result.sampleRate)
    }

    // ===== Chain Effect =====

    @Test
    fun `Chain applies effects in order`() {
        val input = constantSignal(value = 0.9f)

        // Compress then normalize
        val chain = TtsEffect.Chain(listOf(
            TtsEffect.Compression(threshold = 0.5f, ratio = 0.3f),
            TtsEffect.Normalization(level = 0.8f)
        ))
        val result = processor.apply(input, chain)

        // After compression + normalization, peak should be ~0.8
        val peak = result.samples.maxOf { abs(it) }
        assertEquals(0.8f, peak, 0.05f)
    }

    @Test
    fun `Chain with empty list returns unchanged audio`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.Chain(emptyList()))

        assertEquals(input.samples.size, result.samples.size)
        for (i in input.samples.indices) {
            assertEquals(input.samples[i], result.samples[i], 0.0001f)
        }
    }

    @Test
    fun `M1K3 default chain produces valid output`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.Chain.M1K3_DEFAULT)

        assertTrue(result.samples.isNotEmpty())
        assertTrue(result.samples.all { it in -1.0f..1.0f })
    }

    // ===== Empty Input =====

    @Test
    fun `apply handles empty audio sample`() {
        val input = AudioSample.EMPTY
        val result = processor.apply(input, TtsEffect.RadioChat())

        assertTrue(result.isEmpty)
    }
// ===== Theatrical Effect =====

    @Test
    fun `Theatrical modifies audio — not a pass-through`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.Theatrical())
        var different = false
        for (i in input.samples.indices) {
            if (abs(input.samples[i] - result.samples[i]) > 0.001f) { different = true; break }
        }
        assertTrue(different, "Theatrical should modify the signal")
    }

    @Test
    fun `Theatrical keeps samples in valid range`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.Theatrical())
        assertTrue(result.samples.all { it in -1.0f..1.0f }, "Theatrical must not clip")
    }

    @Test
    fun `Theatrical preserves sample count`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.Theatrical())
        assertEquals(input.samples.size, result.samples.size)
    }

    @Test
    fun `Theatrical VHS_HIFI keeps samples in valid range`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.Theatrical(TtsEffect.Theatrical.Preset.VHS_HIFI))
        assertTrue(result.samples.all { it in -1.0f..1.0f })
    }

    @Test
    fun `Theatrical VHS_LINEAR keeps samples in valid range`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.Theatrical(TtsEffect.Theatrical.Preset.VHS_LINEAR))
        assertTrue(result.samples.all { it in -1.0f..1.0f })
    }

    @Test
    fun `Theatrical output stays within safe headroom`() {
        // Saturation + mid EQ + compression should keep output well within [-1, 1]
        // (EQ mid boost can slightly nudge peak, but compression brings it back)
        val samples = FloatArray(2400) { i ->
            (0.9f * kotlin.math.sin(2.0 * Math.PI * 440.0 * i / 24000)).toFloat()
        }
        val input = AudioSample(samples, 24000)
        val result = processor.apply(input, TtsEffect.Theatrical())
        val outputPeak = result.samples.maxOf { abs(it) }
        assertTrue(outputPeak <= 1.0f, "Theatrical must not exceed unity gain (got $outputPeak)")
    }

    @Test
    fun `M1K3_DEFAULT chain with Theatrical produces valid output`() {
        val input = sineWave()
        val result = processor.apply(input, TtsEffect.Chain.M1K3_DEFAULT)
        assertTrue(result.samples.isNotEmpty())
        assertTrue(result.samples.all { it in -1.0f..1.0f })
    }
}
