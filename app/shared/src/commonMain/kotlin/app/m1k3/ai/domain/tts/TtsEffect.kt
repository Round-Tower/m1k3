package app.m1k3.ai.domain.tts

/**
 * TtsEffect - Audio effects for TTS output
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Represents audio processing effects to apply after synthesis.
 * RadioChat is M1K3's signature effect combining intercom + compression.
 *
 * **Usage:**
 * ```kotlin
 * val effect = TtsEffect.RadioChat()
 * val processed = effectProcessor.apply(audio, effect)
 * ```
 */
sealed class TtsEffect {

    /**
     * No effect - pass through audio unchanged
     */
    data object None : TtsEffect()

    /**
     * Intercom effect - radio/communication channel character
     *
     * Creates the classic "assistant voice" sound by applying
     * bandpass filtering and subtle distortion.
     *
     * @param distortion Amount of harmonic distortion (0.0-1.0)
     */
    data class Intercom(
        val distortion: Float = 0.1f
    ) : TtsEffect()

    /**
     * RadioChat effect - M1K3's signature voice character
     *
     * Combines intercom, compression, and bandpass filtering
     * for a professional, radio-style voice.
     *
     * @param intercomMix Amount of intercom effect (0.0-1.0)
     * @param compression Dynamic range compression strength (0.0-1.0)
     * @param highPass High-pass filter cutoff in Hz
     * @param lowPass Low-pass filter cutoff in Hz
     */
    data class RadioChat(
        val intercomMix: Float = 0.3f,
        val compression: Float = 0.6f,
        val highPass: Float = 300f,
        val lowPass: Float = 3400f
    ) : TtsEffect()

    /**
     * Compression effect - even out volume levels
     *
     * @param threshold Amplitude threshold for compression (0.0-1.0)
     * @param ratio Compression ratio (0.0-1.0, lower = more compression)
     */
    data class Compression(
        val threshold: Float = 0.6f,
        val ratio: Float = 0.3f
    ) : TtsEffect()

    /**
     * Normalization effect - consistent output level
     *
     * @param level Target peak level (0.0-1.0)
     */
    data class Normalization(
        val level: Float = 0.8f
    ) : TtsEffect()

    /**
     * Chain effect - combine multiple effects in sequence
     *
     * Effects are applied in order from first to last.
     *
     * @param effects List of effects to apply
     */
    data class Chain(
        val effects: List<TtsEffect>
    ) : TtsEffect() {

        companion object {
            /**
             * M1K3's default effect chain
             */
            val M1K3_DEFAULT = Chain(
                listOf(
                    Intercom(distortion = 0.1f),
                    Compression(threshold = 0.6f, ratio = 0.3f),
                    Normalization(level = 0.8f)
                )
            )
        }
    }

    companion object {
        /**
         * Default effect for M1K3 - RadioChat style
         */
        val default: TtsEffect = RadioChat()
    }
}
