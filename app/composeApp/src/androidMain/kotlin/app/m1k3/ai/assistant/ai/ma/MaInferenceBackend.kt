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
     * @param nCtx Context window size — controls KV cache memory usage.
     *   2048 is safe for all devices. 4096 recommended for Big M1K3 on 8GB+.
     * @return Opaque handle (non-zero) on success, 0 on failure
     */
    fun init(
        modelPath: String,
        nCtx: Int = 2048,
    ): Long

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
     * @param grammar Optional GBNF grammar string. When non-null, installs a lazy grammar
     *                sampler triggered by `<tool_call>` so the model physically cannot emit
     *                malformed tool-call JSON. Null = unconstrained sampling (legacy path).
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
        onToken: ((String) -> Unit)? = null,
        grammar: String? = null,
    ): String

    /**
     * Generate via the model's own chat template (common_chat_templates_apply path).
     *
     * The native bridge renders [messagesJson] + [toolsJson] through the Jinja template
     * embedded in the GGUF, builds a per-model grammar (when tools are present), runs
     * the generation, and then parses the output back into structured fields via
     * common_chat_parse. This lets any model with native tool-calling training
     * (Qwen 2/3/3.5, Llama 3.x, Mistral, Phi-4, DeepSeek-R1, etc.) use the format
     * it was trained on, without us prompt-engineering per model.
     *
     * Returns a JSON string of shape:
     * ```
     * {"content": "...", "reasoning_content": "...",
     *  "tool_calls": [{"name": "...", "arguments": "..."}],
     *  "raw": "..."}
     * ```
     * or `{"error": "..."}` on failure — callers should fall back to [generate] in that case.
     *
     * @param messagesJson OpenAI-style messages array JSON
     * @param toolsJson OpenAI-style tools array JSON ("" or "[]" when no tools)
     * @param enableThinking Hint to the template to encourage `<think>` tags when supported
     *
     * MurphySig: kev+claude / confidence 0.85 / 2026-04-18
     */
    fun generateChat(
        handle: Long,
        messagesJson: String,
        toolsJson: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        enableThinking: Boolean = true,
        onToken: ((String) -> Unit)? = null,
    ): String

    /**
     * Release native model and context resources.
     *
     * After calling release(), the handle is invalid and must not be used.
     */
    fun release(handle: Long)
}
