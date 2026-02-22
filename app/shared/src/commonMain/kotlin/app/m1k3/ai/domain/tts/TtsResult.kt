package app.m1k3.ai.domain.tts

/**
 * TtsResult - Outcome of TTS synthesis
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Represents either successful audio synthesis or an error.
 *
 * **Usage:**
 * ```kotlin
 * when (val result = ttsEngine.synthesize("Hello")) {
 *     is TtsResult.Success -> playAudio(result.audio)
 *     is TtsResult.Error -> showError(result.message)
 * }
 * ```
 */
sealed class TtsResult {

    /**
     * Whether this result is successful
     */
    abstract val isSuccess: Boolean

    /**
     * Whether this result is an error
     */
    val isError: Boolean
        get() = !isSuccess

    /**
     * Get the audio if successful, null otherwise
     */
    abstract fun getOrNull(): AudioSample?

    /**
     * Fold over the result
     *
     * @param onSuccess Called with audio if successful
     * @param onError Called with error info if failed
     * @return Result of the appropriate callback
     */
    inline fun <R> fold(
        onSuccess: (AudioSample) -> R,
        onError: (TtsResult.Error) -> R
    ): R = when (this) {
        is Success -> onSuccess(audio)
        is Error -> onError(this)
    }

    /**
     * Successful synthesis with audio
     *
     * @param audio The synthesized audio samples
     */
    data class Success(val audio: AudioSample) : TtsResult() {
        override val isSuccess: Boolean = true
        override fun getOrNull(): AudioSample = audio
    }

    /**
     * Failed synthesis with error information
     *
     * @param code Error category
     * @param message Human-readable error description
     */
    data class Error(
        val code: TtsErrorCode,
        val message: String
    ) : TtsResult() {
        override val isSuccess: Boolean = false
        override fun getOrNull(): AudioSample? = null
    }
}

/**
 * TTS Error codes
 *
 * Categorizes synthesis failures for appropriate handling.
 */
enum class TtsErrorCode {
    /**
     * Model not loaded - call loadModel() first
     */
    MODEL_NOT_LOADED,

    /**
     * Text to phoneme conversion failed
     */
    PHONEMIZATION_FAILED,

    /**
     * Audio synthesis failed during inference
     */
    SYNTHESIS_FAILED,

    /**
     * Requested voice not found or invalid
     */
    INVALID_VOICE,

    /**
     * Insufficient memory for synthesis
     */
    OUT_OF_MEMORY,

    /**
     * Audio playback failed
     */
    PLAYBACK_FAILED,

    /**
     * Voice embeddings file missing or corrupt
     */
    VOICE_EMBEDDINGS_ERROR,

    /**
     * Unknown or unspecified error
     */
    UNKNOWN
}
