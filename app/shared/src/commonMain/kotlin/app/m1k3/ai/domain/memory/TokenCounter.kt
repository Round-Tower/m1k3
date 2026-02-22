package app.m1k3.ai.domain.memory

/**
 * Token Counter Interface
 *
 * Common interface for counting tokens across platforms.
 * Used by SemanticChunker for determining chunk sizes.
 */
interface TokenCounter {
    /**
     * Count tokens in text
     *
     * @param text Input text
     * @return Number of tokens
     */
    fun countTokens(text: String): Int
}

/**
 * Simple fallback token counter
 *
 * Approximates 4 characters per token.
 * Used when real tokenizer is not available.
 */
class SimpleTokenCounter : TokenCounter {
    override fun countTokens(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
    }
}
