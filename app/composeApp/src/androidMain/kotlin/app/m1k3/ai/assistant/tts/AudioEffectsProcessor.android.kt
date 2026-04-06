package app.m1k3.ai.assistant.tts

import app.m1k3.ai.domain.tts.AudioSample
import app.m1k3.ai.domain.tts.TtsEffect
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

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
            is TtsEffect.Theatrical -> applyTheatrical(audio.samples, effect, audio.sampleRate)
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

    // ===== Theatrical (Film80s port) =====

    /**
     * Analog tape warmth — port of Film80sEffect from the Python desktop app.
     *
     * Chain: tape saturation → academy EQ → vintage compression → noise floor
     *
     * Presets mirror the Python Film80sEffect:
     *   THEATRICAL  → drive 1.3, LP 8kHz,  noise -70dB  (warm, professional)
     *   VHS_HIFI    → drive 1.5, LP 12kHz, noise -60dB  (early cassette)
     *   VHS_LINEAR  → drive 1.8, LP 6kHz,  noise -50dB  (lo-fi VHS intimacy)
     */
    private fun applyTheatrical(
        samples: FloatArray,
        effect: TtsEffect.Theatrical,
        sampleRate: Int
    ): FloatArray {
        val sr = sampleRate.toFloat()

        // Preset parameters (matched to Python Film80sEffect)
        val (drive, lpCutoff, noiseAmp, compThresh, compRatio) = when (effect.preset) {
            TtsEffect.Theatrical.Preset.THEATRICAL ->
                TheatricalParams(1.3f, 8000f, 0.0003f, 0.75f, 0.67f)
            TtsEffect.Theatrical.Preset.VHS_HIFI ->
                TheatricalParams(1.5f, 12000f, 0.001f, 0.65f, 0.5f)
            TtsEffect.Theatrical.Preset.VHS_LINEAR ->
                TheatricalParams(1.8f, 6000f, 0.003f, 0.60f, 0.5f)
        }

        var result = samples.copyOf()

        // 1. Tape saturation: tanh drive with normalization
        //    y = tanh(drive * x) / tanh(drive)  — preserves unity gain at x=1
        val tanhDrive = tanhApprox(drive)
        for (i in result.indices) {
            result[i] = clamp(tanhApprox(drive * result[i]) / tanhDrive)
        }

        // 2. Academy EQ — three-stage biquad:
        //    a. Bass rolloff: HP at 100Hz (gentle, removes sub-bass)
        result = simpleHighPass(result, 100f, sr)
        //    b. Mid presence: +2dB peak at 1200Hz, Q=0.7
        result = peakingEq(result, f0 = 1200f, gainDb = 2.0f, q = 0.7f, sampleRate = sr)
        //    c. Air rolloff: LP at lpCutoff (8/12/6 kHz per preset)
        result = simpleLowPass(result, lpCutoff, sr)

        // 3. Vintage compression (3:1 at threshold, with soft knee)
        //    Python params: attack 20ms, release 200ms → approximated as gentle gain riding
        result = applyVintageCompression(result, compThresh, compRatio, sr)

        // 4. Analog tape noise floor (pink-ish gaussian noise at very low level)
        if (noiseAmp > 0f) {
            for (i in result.indices) {
                result[i] = clamp(result[i] + (Random.nextFloat() * 2f - 1f) * noiseAmp)
            }
        }

        return result
    }

    private data class TheatricalParams(
        val drive: Float,
        val lpCutoff: Float,
        val noiseAmp: Float,
        val compThresh: Float,
        val compRatio: Float
    )

    /**
     * Vintage-style compression with soft knee.
     * Slow attack/release gives the "musical" pumping character of 80s gear.
     */
    private fun applyVintageCompression(
        samples: FloatArray,
        threshold: Float,
        ratio: Float,
        sampleRate: Float
    ): FloatArray {
        // Attack/release time constants (20ms attack, 200ms release — Film80s Python values)
        val attackCoeff  = exp(-1f / (0.020f * sampleRate))
        val releaseCoeff = exp(-1f / (0.200f * sampleRate))

        val output = FloatArray(samples.size)
        var envelope = 0f

        for (i in samples.indices) {
            val magnitude = abs(samples[i])
            // Envelope follower
            envelope = if (magnitude > envelope) {
                attackCoeff  * envelope + (1f - attackCoeff)  * magnitude
            } else {
                releaseCoeff * envelope + (1f - releaseCoeff) * magnitude
            }
            // Gain computation with soft knee
            val gain = if (envelope > threshold) {
                val excess = envelope - threshold
                val kneeWidth = threshold * 0.2f   // 20% knee
                if (excess < kneeWidth) {
                    // Soft knee — interpolate into compression
                    1f - (1f - ratio) * (excess / kneeWidth) * 0.5f
                } else {
                    threshold / envelope + (envelope - threshold) * ratio / envelope
                }
            } else {
                1f
            }
            output[i] = clamp(samples[i] * gain)
        }
        return output
    }

    /**
     * Parametric peaking EQ biquad filter.
     * Used for the academy mid-presence boost (+2dB at 1200Hz).
     */
    private fun peakingEq(
        samples: FloatArray,
        f0: Float,
        gainDb: Float,
        q: Float,
        sampleRate: Float
    ): FloatArray {
        val w0    = 2f * Math.PI.toFloat() * f0 / sampleRate
        val alpha = sin(w0) / (2f * q)
        val A     = sqrt(10f.pow(gainDb / 40f))

        val b0 = 1f + alpha * A;   val b1 = -2f * cos(w0); val b2 = 1f - alpha * A
        val a0 = 1f + alpha / A;   val a1 = -2f * cos(w0); val a2 = 1f - alpha / A

        // Normalise
        val nb0 = b0/a0; val nb1 = b1/a0; val nb2 = b2/a0
        val na1 = a1/a0; val na2 = a2/a0

        val output = FloatArray(samples.size)
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f

        for (i in samples.indices) {
            val x0 = samples[i]
            val y0 = nb0*x0 + nb1*x1 + nb2*x2 - na1*y1 - na2*y2
            output[i] = clamp(y0)
            x2 = x1; x1 = x0; y2 = y1; y1 = y0
        }
        return output
    }

    /** Fast tanh approximation (Padé, accurate to ~0.1% for |x| < 4) */
    private fun tanhApprox(x: Float): Float {
        val x2 = x * x
        return x * (27f + x2) / (27f + 9f * x2)
    }

    private fun Float.pow(exp: Float): Float = Math.pow(this.toDouble(), exp.toDouble()).toFloat()

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
