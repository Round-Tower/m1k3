package app.m1k3.ai.domain.tts

/**
 * AudioSample - Synthesized audio output
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Represents audio data from TTS synthesis as float samples.
 * Default format: 24kHz mono (Kokoro native format).
 *
 * **Usage:**
 * ```kotlin
 * val audio = AudioSample(samples, sampleRate = 24000)
 * println("Duration: ${audio.durationMs}ms")
 * ```
 *
 * @param samples Audio samples as float array (range: -1.0 to 1.0)
 * @param sampleRate Sample rate in Hz (default: 24000 for Kokoro)
 * @param channels Number of audio channels (default: 1 for mono)
 */
data class AudioSample(
    val samples: FloatArray,
    val sampleRate: Int = 24000,
    val channels: Int = 1
) {
    /**
     * Duration in milliseconds
     *
     * Calculated from sample count and sample rate.
     */
    val durationMs: Long
        get() = if (sampleRate > 0) {
            (samples.size * 1000L) / sampleRate
        } else {
            0L
        }

    /**
     * Check if the sample is empty
     */
    val isEmpty: Boolean
        get() = samples.isEmpty()

    /**
     * Check if the sample is not empty
     */
    val isNotEmpty: Boolean
        get() = samples.isNotEmpty()

    /**
     * Size in bytes (assuming float32 samples)
     */
    val sizeBytes: Int
        get() = samples.size * 4

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AudioSample

        if (!samples.contentEquals(other.samples)) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false

        return true
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        return result
    }

    override fun toString(): String {
        return "AudioSample(samples=${samples.size}, sampleRate=$sampleRate, channels=$channels, durationMs=$durationMs)"
    }

    companion object {
        /**
         * Create an empty AudioSample
         */
        val EMPTY = AudioSample(floatArrayOf())

        /**
         * Kokoro native sample rate
         */
        const val KOKORO_SAMPLE_RATE = 24000

        /**
         * Piper native sample rate
         */
        const val PIPER_SAMPLE_RATE = 22050
    }
}
