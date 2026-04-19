package app.m1k3.ai.assistant.ai

import app.m1k3.ai.domain.ai.GenerationConfig

/**
 * A tool call extracted by the model's native parser
 * (common_chat_parse on the C++ side).
 *
 * [arguments] is a JSON string (not a parsed map) because different models
 * emit different argument shapes (key/value strings, nested objects, arrays);
 * we let the executor layer parse what it expects.
 */
data class NativeToolCall(
    val name: String,
    val arguments: String,
    val id: String = "",
)

/**
 * Output of [NativeChatCapable.generateChatNative].
 *
 * - [content] is the user-visible reply (reasoning stripped).
 * - [reasoningContent] is the `<think>…</think>` block the parser peeled off.
 * - [toolCalls] is the structured list of tool invocations the model emitted.
 * - [raw] is the untouched model output — keep it for debugging / fallbacks.
 */
data class NativeChatOutput(
    val content: String,
    val reasoningContent: String,
    val toolCalls: List<NativeToolCall>,
    val raw: String,
)

/**
 * Engines that can render prompts via the model's own chat template
 * (via llama.cpp's `common_chat_templates_apply`) and parse tool calls
 * with the model-appropriate grammar (`common_chat_parse`).
 *
 * When an engine implements this, [app.m1k3.ai.assistant.chat.usecase.ChatWithToolsUseCase]
 * prefers this path for tool-calling turns: each GGUF model gets its native
 * tool format (Qwen's `<function=…>`, Llama 3's `<|python_tag|>`, Mistral's
 * `[TOOL_CALLS]`, etc.) for free.
 *
 * Engines without native chat templates (MlKit, cloud proxies, old models
 * lacking a `tokenizer.chat_template`) can simply not implement this and
 * the use case will fall back to the prompt-engineered path.
 *
 * MurphySig: kev+claude / confidence 0.88 / 2026-04-18
 * Rationale: plug-n-play ethos — every GGUF ships its format, let llama.cpp
 * read it instead of us chasing each model's quirks.
 */
interface NativeChatCapable {
    /**
     * Generate a response using the model's native chat template.
     *
     * @param messagesJson OpenAI-style messages array as a JSON string
     * @param toolsJson OpenAI-style tools array as a JSON string ("" or "[]" for none)
     * @param config sampling config; `grammar` field is ignored (native path generates its own)
     * @param onToken streaming callback invoked per token piece
     * @param enableThinking hint to the template to emit `<think>` tags when supported
     */
    suspend fun generateChatNative(
        messagesJson: String,
        toolsJson: String,
        config: GenerationConfig,
        onToken: (String) -> Unit = {},
        enableThinking: Boolean = true,
    ): Result<NativeChatOutput>
}
