package app.m1k3.ai.assistant.ai

/**
 * Gemma 3 Prompt Builder - Generates properly formatted prompts for Gemma 3 models.
 *
 * **Key Insight:** Gemma 3 only supports `user` and `model` roles. NO system role!
 * See: https://ai.google.dev/gemma/docs/core/prompt-structure
 *
 * **Design Philosophy for Small Models (270M):**
 * - SIMPLE single-turn format (no fake multi-turn conversations)
 * - RAG facts BEFORE the question (model sees context first)
 * - No complex instructions (small models get confused)
 * - Let the model's training handle personality
 *
 * **Format:**
 * ```
 * <bos><start_of_turn>user
 * Facts:
 * [RAG facts if any]
 *
 * [User question]
 * <end_of_turn>
 * <start_of_turn>model
 * ```
 */
object Gemma3PromptBuilder {

    private const val BOS = "<bos>"
    private const val START_USER = "<start_of_turn>user\n"
    private const val START_MODEL = "<start_of_turn>model\n"
    private const val END_TURN = "<end_of_turn>\n"

    /**
     * Build a simple Gemma 3 chat prompt.
     *
     * @param userQuery The user's question or request
     * @param context Optional RAG facts or context (shown before question)
     * @return Formatted prompt ready for Gemma 3 inference
     */
    fun build(
        userQuery: String,
        context: String? = null
    ): String = buildString {
        append(BOS)
        append(START_USER)

        // RAG facts FIRST (so model sees context before question)
        // Note: "0 facts" check guards against empty KB summary like "I have access to 0 facts..."
        // This is fragile but prevents showing useless context to the model
        val hasValidContext = !context.isNullOrBlank() && !context.contains("0 facts")
        if (hasValidContext) {
            append("Facts:\n")
            append(context.trim())
            append("\n\n")
        }
        // DEBUG: Log if context was skipped (helps trace RAG issues)
        // Note: This logs only in debug builds - consider using platform-specific logger for production
        if (!context.isNullOrBlank() && !hasValidContext) {
            println("[Gemma3PromptBuilder] WARNING: Context skipped due to '0 facts' check. Context preview: ${context.take(100)}")
        }

        // User question
        append(userQuery.trim())
        append(END_TURN)

        // Model responds
        append(START_MODEL)
    }

    /**
     * Build prompt with explicit instruction prefix.
     *
     * Use sparingly - small models handle simple prompts better.
     *
     * @param userQuery The user's question
     * @param context Optional RAG facts
     * @param instruction Brief instruction (e.g., "Answer briefly:")
     * @return Formatted prompt
     */
    fun buildWithInstruction(
        userQuery: String,
        context: String? = null,
        instruction: String
    ): String = buildString {
        append(BOS)
        append(START_USER)

        // Instruction first
        append(instruction.trim())
        append("\n\n")

        // RAG facts (same "0 facts" guard as build())
        val hasValidContext = !context.isNullOrBlank() && !context.contains("0 facts")
        if (hasValidContext) {
            append("Facts:\n")
            append(context.trim())
            append("\n\n")
        }

        // User question
        append(userQuery.trim())
        append(END_TURN)

        // Model responds
        append(START_MODEL)
    }

    /**
     * Validate that a prompt has correct Gemma 3 structure.
     *
     * @param prompt The prompt to validate
     * @return true if prompt has valid Gemma 3 format
     */
    fun isValidFormat(prompt: String): Boolean {
        return prompt.startsWith(BOS) &&
                prompt.contains(START_USER) &&
                prompt.contains(END_TURN) &&
                prompt.endsWith(START_MODEL) &&
                !prompt.contains("<start_of_turn>system") // Gemma 3 has NO system role!
    }

    /**
     * Get stop tokens for Gemma 3.
     *
     * @return List of stop tokens that signal end of generation
     */
    fun getStopTokens(): List<String> = listOf("<end_of_turn>", "<eos>")
}
