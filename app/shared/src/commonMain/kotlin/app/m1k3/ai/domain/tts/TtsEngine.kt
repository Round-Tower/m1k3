package app.m1k3.ai.domain.tts

/**
 * TTS Engine Interface - Unified interface for text-to-speech synthesis.
 *
 * Abstracts TTS functionality across different backends:
 * - KokoroTtsEngine (Kokoro-82M via ONNX Runtime)
 * - MockTtsEngine (testing)
 * - PiperTtsEngine (future, smaller/faster alternative)
 *
 * Domain interface - Pure Kotlin, no platform dependencies.
 *
 * **Design Principles:**
 * 1. Platform agnostic - Works across Android, iOS, Desktop
 * 2. Backend flexibility - Supports engines with varying quality/speed
 * 3. Test-friendly - Easy to mock for UI testing
 * 4. Result-based error handling - No surprise exceptions
 * 5. Streaming support - Real-time audio chunk delivery
 *
 * **Usage:**
 * ```kotlin
 * val engine: TtsEngine = // platform implementation
 * engine.loadModel().onFailure { return }
 *
 * when (val result = engine.synthesize("Hello!", Voice.Kokoro.Daniel)) {
 *     is TtsResult.Success -> audioPlayer.play(result.audio)
 *     is TtsResult.Error -> showError(result.message)
 * }
 *
 * engine.release()
 * ```
 *
 * @see Voice for available voice options
 * @see TtsResult for synthesis output
 * @see AudioSample for audio data format
 */
interface TtsEngine {

    /**
     * Whether the model is loaded and ready for synthesis
     */
    val isLoaded: Boolean

    /**
     * Native sample rate of the engine's audio output (Hz)
     *
     * Kokoro: 24000
     * Piper: 22050
     */
    val sampleRate: Int

    /**
     * Load the TTS model into memory.
     *
     * May include:
     * - Loading ONNX model file
     * - Parsing voice embeddings
     * - Initializing phonemizer
     * - Setting up inference session
     *
     * Must be called before any synthesis methods.
     *
     * @return Result.success(Unit) if model loads successfully
     */
    suspend fun loadModel(): Result<Unit>

    /**
     * Synthesize text to audio.
     *
     * Converts text to speech using the specified voice and speed.
     * Returns the full audio when synthesis is complete.
     *
     * @param text The text to synthesize
     * @param voice Voice to use (default: Daniel)
     * @param speed Playback speed multiplier (0.5-2.0, default: 1.0)
     * @return TtsResult.Success with audio or TtsResult.Error
     */
    suspend fun synthesize(
        text: String,
        voice: Voice = Voice.default,
        speed: Float = 1.0f
    ): TtsResult

    /**
     * Synthesize text with streaming audio output.
     *
     * Delivers audio in chunks as they are generated,
     * reducing perceived latency.
     *
     * @param text The text to synthesize
     * @param voice Voice to use (default: Daniel)
     * @param speed Playback speed multiplier (0.5-2.0, default: 1.0)
     * @param onChunk Callback invoked for each generated audio chunk
     * @return Result.success(Unit) if streaming completes
     */
    suspend fun synthesizeStreaming(
        text: String,
        voice: Voice = Voice.default,
        speed: Float = 1.0f,
        onChunk: (AudioSample) -> Unit
    ): Result<Unit>

    /**
     * Release engine resources.
     *
     * Cleans up ONNX session, voice embeddings, phonemizer.
     * After calling, engine must be re-loaded before use.
     */
    fun release()
}
