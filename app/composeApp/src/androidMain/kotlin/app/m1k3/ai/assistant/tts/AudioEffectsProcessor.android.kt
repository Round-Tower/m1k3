package app.m1k3.ai.assistant.tts

import app.m1k3.ai.domain.tts.AudioSample
import app.m1k3.ai.domain.tts.TtsEffect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * AudioEffectsProcessor - DSP effects for TTS audio
 *
 * Applies audio effects defined in [TtsEffect] to [AudioSample] data.
 * All processing operates on float PCM samples in [-1.0, 1.0] range.
 *
 * **Effects:**
 * - None: Pass-through
 * - Intercom: Harmonic distortion + bandpass character
 * - RadioChat: Intercom + compression + bandpass filtering
 * - Compression: Dynamic range reduction
 * - Normalization: Peak level scaling
 * - Chain: Sequential effect pipeline
 */
class AudioEffectsProcessor {

    /**
     * Apply an effect to an audio sample.
     *
     * @param audio Input audio
     * @param effect Effect to apply
     * @return Processed audio with same sample rate and channels
     */
    fun apply(audio: AudioSample, effect: TtsEffect): AudioSample {
        if (audio.isEmpty) return audio

        val processed = when (effect) {
            is TtsEffect.None -> audio.samples.copyOf()
            is TtsEffect.Normalization -> applyNormalization(audio.samples, effect)
            is TtsEffect.Compression -> applyCompression(audio.samples, effect)
            is TtsEffect.Intercom -> applyIntercom(audio.samples, effect)
            is TtsEffect.RadioChat -> applyRadioChat(audio.samples, effect)
            is TtsEffect.Chain -> {
                var current = audio
                for (chainEffect in effect.effects) {
                    current = apply(current, chainEffect)
                }
                return current
            }
        }

        return AudioSample(processed, audio.sampleRate, audio.channels)
    }

    // ===== Normalization =====

    private fun applyNormalization(samples: FloatArray, effect: TtsEffect.Normalization): FloatArray {
        val peak = samples.maxOfOrNull { abs(it) } ?: return samples.copyOf()
        if (peak < 0.0001f) return samples.copyOf() // silence

        val gain = effect.level / peak
        return FloatArray(samples.size) { i ->
            clamp(samples[i] * gain)
        }
    }

    // ===== Compression =====

    private fun applyCompression(samples: FloatArray, effect: TtsEffect.Compression): FloatArray {
        return FloatArray(samples.size) { i ->
            val sample = samples[i]
            val magnitude = abs(sample)
            if (magnitude > effect.threshold) {
                val excess = magnitude - effect.threshold
                val compressed = effect.threshold + excess * effect.ratio
                clamp(compressed * sign(sample))
            } else {
                sample
            }
        }
    }

    // ===== Intercom =====

    private fun applyIntercom(samples: FloatArray, effect: TtsEffect.Intercom): FloatArray {
        // Soft-clip distortion for harmonic character
        return FloatArray(samples.size) { i ->
            val sample = samples[i]
            val driven = sample * (1f + effect.distortion * 3f)
            clamp(softClip(driven))
        }
    }

    // ===== RadioChat =====

    private fun applyRadioChat(samples: FloatArray, effect: TtsEffect.RadioChat): FloatArray {
        var result = samples.copyOf()

        // 1. Simple high-pass approximation (remove low rumble)
        result = simpleHighPass(result, effect.highPass, 24000f)

        // 2. Simple low-pass approximation (remove high hiss)
        result = simpleLowPass(result, effect.lowPass, 24000f)

        // 3. Intercom character (subtle harmonic distortion)
        val intercom = TtsEffect.Intercom(distortion = effect.intercomMix * 0.3f)
        result = applyIntercom(result, intercom)

        // 4. Compression
        val compression = TtsEffect.Compression(threshold = 0.5f, ratio = 1f - effect.compression)
        result = applyCompression(result, compression)

        // 5. Clamp all outputs
        for (i in result.indices) {
            result[i] = clamp(result[i])
        }

        return result
    }

    // ===== DSP Utilities =====

    /**
     * Soft clipping via tanh-like approximation.
     * Adds warm harmonic distortion instead of harsh clipping.
     */
    private fun softClip(x: Float): Float {
        return when {
            x > 1f -> 1f - 1f / (1f + x)
            x < -1f -> -1f + 1f / (1f - x)
            else -> x - (x * x * x) / 3f
        }
    }

    /**
     * Simple single-pole high-pass filter.
     * Attenuates frequencies below cutoff.
     */
    private fun simpleHighPass(samples: FloatArray, cutoffHz: Float, sampleRate: Float): FloatArray {
        if (samples.isEmpty()) return samples
        val rc = 1f / (2f * Math.PI.toFloat() * cutoffHz)
        val dt = 1f / sampleRate
        val alpha = rc / (rc + dt)

        val output = FloatArray(samples.size)
        output[0] = samples[0]
        for (i in 1 until samples.size) {
            output[i] = alpha * (output[i - 1] + samples[i] - samples[i - 1])
        }
        return output
    }

    /**
     * Simple single-pole low-pass filter.
     * Attenuates frequencies above cutoff.
     */
    private fun simpleLowPass(samples: FloatArray, cutoffHz: Float, sampleRate: Float): FloatArray {
        if (samples.isEmpty()) return samples
        val rc = 1f / (2f * Math.PI.toFloat() * cutoffHz)
        val dt = 1f / sampleRate
        val alpha = dt / (rc + dt)

        val output = FloatArray(samples.size)
        output[0] = samples[0]
        for (i in 1 until samples.size) {
            output[i] = output[i - 1] + alpha * (samples[i] - output[i - 1])
        }
        return output
    }

    private fun clamp(value: Float): Float = max(-1f, min(1f, value))
}
