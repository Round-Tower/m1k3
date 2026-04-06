package app.m1k3.ai.assistant.ai.ma

/**
 * MaBridge - JNI bridge to the Ma native inference library.
 *
 * Ma wraps llama.cpp's stable C API, pinned to b8637+ (required for Gemma 4).
 * Built via NDK/CMake as `libma.so`, shipped with the APK for arm64-v8a and x86_64.
 *
 * ## Key improvements over Llamatik:
 * - llama.cpp b8637+ (Gemma 4 support — Llamatik was stuck at b7815, Jan 2025)
 * - True token-level streaming (NewStringUTF on complete token pieces only)
 * - Handle-based API (multiple model contexts can coexist)
 * - Explicit resource release (no orphaned contexts)
 *
 * ## Thread safety:
 * Each context handle is NOT thread-safe. [LlamaCppEngine] uses coroutines
 * with Mutex to guarantee single-threaded access per context.
 *
 * ## Setup (one-time):
 * ```
 * git submodule add https://github.com/ggerganov/llama.cpp.git \
 *     app/composeApp/src/androidMain/cpp/llama.cpp
 * cd app/composeApp/src/androidMain/cpp/llama.cpp
 * git checkout <commit-hash-for-b8637+>
 * ```
 */
object MaBridge : MaInferenceBackend {

    init {
        System.loadLibrary("ma")
    }

    /**
     * Load a GGUF model from the given path.
     *
     * @param modelPath Absolute path to the .gguf file
     * @return Opaque context handle (pointer cast to Long), or 0 on failure
     */
    override external fun init(modelPath: String): Long

    /**
     * Generate text from a pre-formatted prompt.
     *
     * When [onToken] is provided, each token piece is emitted as it is
     * sampled (true streaming). The complete generated text is also returned.
     *
     * The C bridge handles EOG detection via llama_token_is_eog().
     * Custom stop tokens (e.g. <end_of_turn>) are stripped in Kotlin by
     * [LlamaCppEngine.stripStopTokens].
     */
    override fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        onToken: ((String) -> Unit)?
    ): String {
        val callback = onToken?.let { MaTokenCallback(it) }
        return nativeGenerate(handle, prompt, maxTokens, temperature, topP, topK, repeatPenalty, callback)
    }

    /**
     * Release native resources for this context handle.
     * After calling release(), [handle] must not be used.
     */
    override external fun release(handle: Long)

    // --- Private JNI ---

    /**
     * JNI entry point for generation.
     *
     * [callback] is a [MaTokenCallback] instance called once per token piece,
     * or null for non-streaming generation.
     */
    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        callback: MaTokenCallback?
    ): String
}
