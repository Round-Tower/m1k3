package app.m1k3.ai.assistant.ai.ma

/**
 * MaInferenceBackend - Abstraction over llama.cpp JNI bridge.
 *
 * Allows LlamaCppEngine to be tested without native code.
 * [MaBridge] provides the real JNI implementation; [FakeMaInferenceBackend]
 * is used in unit tests.
 *
 * The handle-based API mirrors llama.cpp's context lifecycle:
 * - init() loads model + creates context → returns opaque handle
 * - generate() runs inference on that context
 * - release() frees native memory
 *
 * Multiple contexts can coexist (unlike Llamatik's global state).
 */
interface MaInferenceBackend {

    /**
     * Load GGUF model and create inference context.
     *
     * @param modelPath Absolute path to the .gguf file
     * @return Opaque handle (non-zero) on success, 0 on failure
     */
    fun init(modelPath: String): Long

    /**
     * Generate text from a formatted prompt.
     *
     * Handles both streaming and non-streaming generation:
     * - When [onToken] is null: blocks until complete, returns full text
     * - When [onToken] is provided: calls back for each token piece (true streaming),
     *   then returns the accumulated text
     *
     * Stop tokens (e.g. `<end_of_turn>`) are stripped by [LlamaCppEngine] in Kotlin.
     * The C bridge handles EOG detection via llama_token_is_eog().
     *
     * @param handle Context handle from [init]
     * @param prompt Pre-formatted prompt (with chat template tokens applied)
     * @param maxTokens Maximum tokens to generate
     * @param temperature Sampling temperature (0.0–2.0)
     * @param topP Nucleus sampling threshold
     * @param topK Top-K sampling cutoff
     * @param repeatPenalty Repetition penalty (1.0 = none)
     * @param onToken Called for each generated token piece (nullable = non-streaming)
     * @return Complete generated text
     */
    fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        onToken: ((String) -> Unit)? = null
    ): String

    /**
     * Release native model and context resources.
     *
     * After calling release(), the handle is invalid and must not be used.
     */
    fun release(handle: Long)
}
