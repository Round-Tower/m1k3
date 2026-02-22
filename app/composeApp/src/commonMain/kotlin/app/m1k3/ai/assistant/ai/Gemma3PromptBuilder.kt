package app.m1k3.ai.assistant.ai

/**
 * Gemma 3 Prompt Builder - Generates properly formatted prompts for Gemma 3 models.
 *
 * @deprecated Use `ChatFormatter` with `ChatFormat.Gemma3` instead for unified
 * prompt building across all model formats. This class is kept for reference
 * and backward compatibility but will be removed in a future version.
 *
 * **Migration:**
 * ```kotlin
 * // Old
 * val prompt = Gemma3PromptBuilder.build(userQuery, context)
 *
 * // New
 * val formatter = DefaultChatFormatter(ChatFormat.Gemma3)
 * val prompt = formatter.buildPrompt(systemPrompt, messages)
 * ```
 *
 * **Key Insight:** Gemma 3 only supports `user` and `model` roles. NO system role!
 * See: https://ai.google.dev/gemma/docs/core/prompt-structure
 */
@Deprecated(
    message = "Use ChatFormatter with ChatFormat.Gemma3 for unified prompt building",
    replaceWith = ReplaceWith(
        "DefaultChatFormatter(ChatFormat.Gemma3).buildPrompt(systemPrompt, messages)",
        "app.m1k3.ai.domain.chat.services.DefaultChatFormatter",
        "app.m1k3.ai.domain.chat.format.ChatFormat"
    )
)
object Gemma3PromptBuilder {

    private const val BOS = "<bos>"
    private const val START_USER = "<start_of_turn>user\n\n"
    private const val START_MODEL = "<start_of_turn>model\n\n"
    private const val END_TURN = "<end_of_turn>\n\n"

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

        val hasValidContext = !context.isNullOrBlank()
        if (hasValidContext) {
            append(context.trim())
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

        val hasValidContext = !context.isNullOrBlank()
        if (hasValidContext) {
            append(context.trim())
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
