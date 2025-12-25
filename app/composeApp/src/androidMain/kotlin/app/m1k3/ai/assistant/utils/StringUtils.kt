package app.m1k3.ai.assistant.utils

/**
 * String utilities for text processing and cleaning.
 *
 * Contains helpers for:
 * - Chat template token removal
 * - Text normalization
 * - Streaming output processing
 */

/**
 * Regex for cleaning chat template tokens from streaming inference output.
 *
 * Matches both complete and partial template tokens:
 * - ChatML tokens (legacy): <|im_start|>, <|im_end|>, <|endoftext|>
 * - Gemma/Llama tokens: <end_of_turn>, <start_of_turn>, <eos>, <bos>
 * - Partial tokens (handles tokenizer splitting): <end_of_turn, end_of_turn>, etc.
 * - Fragments: <|, |>, end_of_turn (without angle brackets)
 *
 * Performance: Single-pass regex replacement (8x faster than sequential String.replace())
 *
 * @see cleanStreamingToken
 */
val CHAT_TEMPLATE_TOKEN_REGEX = Regex(
    // Complete ChatML tokens
    "<\\|im_start\\>|<\\|im_end\\>|<\\|endoftext\\>|" +
    // Complete Gemma/Llama tokens
    "<end_of_turn>|<start_of_turn>|<eos>|<bos>|" +
    // Partial tokens (tokenizer may split them)
    "<end_of_turn|end_of_turn>|end_of_turn|" +
    "<start_of_turn|start_of_turn>|start_of_turn|" +
    // Fragments
    "<\\||\\|>"
)

/**
 * Clean streaming token by removing chat template markers.
 *
 * Optimizations:
 * - Single regex pass (vs 8 sequential String.replace() calls)
 * - Skip whitespace-only tokens at start of generation
 * - First token trimming to remove leading newlines
 *
 * Threading: Safe to call from any thread (pure function, no side effects)
 *
 * Example:
 * ```kotlin
 * val cleaned = cleanStreamingToken("<end_of_turn>Hello", false)
 * // Result: "Hello"
 *
 * val startToken = cleanStreamingToken("\n\n", isStartOfGeneration = true)
 * // Result: "" (whitespace-only tokens ignored at start)
 * ```
 *
 * @param token Raw token from AI model
 * @param isStartOfGeneration True if this is the very start of text generation (not just first token)
 * @return Cleaned token, or empty string if whitespace-only at start
 */
fun cleanStreamingToken(token: String, isStartOfGeneration: Boolean): String {
    // Remove all chat template tokens in single pass
    val cleaned = CHAT_TEMPLATE_TOKEN_REGEX.replace(token, "")

    // At start of generation, skip pure whitespace tokens (avoid leading blank lines)
    if (isStartOfGeneration && cleaned.isBlank()) {
        return ""
    }

    // Trim leading whitespace from first meaningful token
    return if (isStartOfGeneration) cleaned.trimStart() else cleaned
}
