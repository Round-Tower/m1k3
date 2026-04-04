package app.m1k3.ai.domain.ai

import app.m1k3.ai.domain.chat.format.ChatFormat

/**
 * LlmModel - Available on-device LLM models
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Represents GGUF models that can be loaded via llama.cpp (Llamatik).
 * Each model specifies its filename, chat format, and parameters.
 *
 * **Usage:**
 * ```kotlin
 * val model = LlmModel.FalconH1_90M
 * val engine = LlamaCppEngine(context, model)
 * ```
 *
 * @param id Unique identifier
 * @param displayName Human-readable name for UI
 * @param filename GGUF model filename (in assets/models/)
 * @param parameterCount Approximate parameter count
 * @param chatFormat The chat template format for this model
 */
sealed class LlmModel(
    val id: String,
    val displayName: String,
    val filename: String,
    val parameterCount: Long,
    val chatFormat: ChatFormat,
    val minRamGB: Int = 0
) {
    /**
     * Gemma 3 270M - Google's compact model
     *
     * Current default. Good balance of quality and speed.
     * Uses IQ3_XXS quantization for minimal size.
     */
    data object Gemma3_270M : LlmModel(
        id = "gemma-3-270m",
        displayName = "Gemma 3 (270M)",
        filename = "gemma-3-270m-it-UD-IQ3_XXS.gguf",
        parameterCount = 270_000_000L,
        chatFormat = ChatFormat.Gemma3
    )

    /**
     * Falcon-H1 Tiny 90M - TII's Mamba2/Attention hybrid
     *
     * Ultra-lightweight model with novel hybrid architecture.
     * Uses Q8_0 quantization (~98MB) for best quality testing.
     */
    data object FalconH1_90M : LlmModel(
        id = "falcon-h1-90m",
        displayName = "Falcon-H1 (90M)",
        filename = "Falcon-H1-Tiny-90M-Instruct-Q8_0.gguf",
        parameterCount = 90_000_000L,
        chatFormat = ChatFormat.FalconH1
    )

    /**
     * Gemma 4 E2B - Google's efficient on-device model
     *
     * 2.3B effective params (5.1B total with Per-Layer Embeddings).
     * Multimodal (text+image+audio), 128K context, function calling.
     * Requires 8GB+ RAM at 4-bit quantization.
     */
    data object Gemma4_E2B : LlmModel(
        id = "gemma-4-e2b",
        displayName = "Gemma 4 (2.3B)",
        filename = "gemma-4-E2B-it-Q4_K_M.gguf",
        parameterCount = 2_300_000_000L,
        chatFormat = ChatFormat.Gemma4,
        minRamGB = 8
    )

    companion object {
        /**
         * Default model - Gemma 3 270M
         */
        val default: LlmModel get() = Gemma3_270M

        /**
         * Get all available models
         */
        fun all(): List<LlmModel> = listOf(
            Gemma3_270M,
            FalconH1_90M,
            Gemma4_E2B
        )

        /**
         * Find model by ID
         *
         * @return Model if found, null otherwise
         */
        fun findById(id: String): LlmModel? = all().find { it.id == id }

        /**
         * Find model by filename
         *
         * @return Model if found, null otherwise
         */
        fun findByFilename(filename: String): LlmModel? =
            all().find { it.filename == filename }

        /**
         * Get models available for a given device RAM
         *
         * Filters out models that require more RAM than available.
         *
         * @param deviceRamGB Device RAM in gigabytes
         * @return List of models that can run on this device
         */
        fun availableFor(deviceRamGB: Int): List<LlmModel> =
            all().filter { it.minRamGB <= deviceRamGB }
    }
}
