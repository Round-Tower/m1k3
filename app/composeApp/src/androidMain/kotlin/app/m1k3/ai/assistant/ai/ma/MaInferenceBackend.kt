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
     * @param nBatch Logical batch size for prompt prefill (llama_context_params.n_batch).
     * @param nUbatch Physical micro-batch size; must be <= [nBatch] (n_ubatch).
     * @param threadsGen Thread count for generation (decode). 0 = native hw-derived default.
     * @param threadsBatch Thread count for prefill (prompt encode). 0 = native default.
     * @param useFlashAttn When true the bridge asks for LLAMA_FLASH_ATTN_TYPE_AUTO.
     *   Phase 1 keeps this false everywhere; Phase 2 flips it on for HIGH_END+ paired
     *   with [kvQuantOrdinal] ≠ F16 (upstream couples them: V-cache quant requires FA).
     * @param kvQuantOrdinal Ordinal of [app.m1k3.ai.domain.ai.KvCacheType]: 0=F16, 1=Q8_0.
     *   Any non-F16 value requires [useFlashAttn] = true.
     * @param useMlock When true asks llama.cpp to mlock model weights into RAM.
     * @return Opaque handle (non-zero) on success, 0 on failure
     */
    fun init(
        modelPath: String,
        nCtx: Int = 2048,
        nBatch: Int = 512,
        nUbatch: Int = 128,
        threadsGen: Int = 0,
        threadsBatch: Int = 0,
        useFlashAttn: Boolean = false,
        kvQuantOrdinal: Int = 0,
        useMlock: Boolean = false,
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
     * @param minP Minimum probability floor relative to the most likely token (0.0 = disabled).
     *   min_p is inserted between top_p and temperature in the sampler chain when > 0.
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
        minP: Float = 0.0f,
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
        minP: Float = 0.0f,
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
