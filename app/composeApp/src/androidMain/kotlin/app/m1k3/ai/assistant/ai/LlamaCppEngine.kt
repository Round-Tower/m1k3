package app.m1k3.ai.assistant.ai

import android.content.Context
import app.m1k3.ai.assistant.utils.Logger
import app.m1k3.ai.domain.ai.GenerationConfig
import app.m1k3.ai.domain.ai.LlmModel
import app.m1k3.ai.domain.chat.format.ChatFormat
import app.m1k3.ai.domain.chat.format.MessageRole
import app.m1k3.ai.domain.chat.services.ChatFormatter
import app.m1k3.ai.domain.chat.services.ChatMessage
import app.m1k3.ai.domain.chat.services.DefaultChatFormatter
import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private val logger = Logger.withTag("LlamaCppEngine")

/**
 * LlamaCppEngine - llama.cpp-based AI inference engine via Llamatik
 *
 * @param context Android context for file access
 * @param model The LLM model to use (default: Gemma3 270M)
 * @param chatFormatter Optional formatter for prompt construction (derived from model's format)
 */
class LlamaCppEngine(
    private val context: Context,
    private val model: LlmModel = LlmModel.default,
    private val chatFormatter: ChatFormatter = DefaultChatFormatter(model.chatFormat)
) : BaseLlmEngine {

    @Volatile
    private var isInitialized = false
    private val initMutex = Mutex()

    // Device-specific configuration (for getOptimalMaxTokens)
    private val deviceRamGB: Int by lazy {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        (memInfo.totalMem / (1024 * 1024 * 1024)).toInt()
    }

    private val modelFilename = model.filename
    private var defaultConfig = GenerationConfig()

    /**
     * Initialize llama.cpp engine with the configured GGUF model.
     *
     * Steps:
     * 1. Copy GGUF model from assets to internal storage (if not exists)
     * 2. Initialize Llamatik with model path
     *
     * @return Result.success(Unit) if initialization succeeds, Result.failure(exception) otherwise
     */
    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        // Fast path: already initialized (volatile read)
        if (isInitialized) {
            return@withContext Result.success(Unit)
        }

        // Thread-safe initialization with mutex (prevents double-init race condition)
        initMutex.withLock {
            // Double-check after acquiring lock
            if (isInitialized) {
                return@withLock Result.success(Unit)
            }

            try {
                logger.i { "Starting initialization -> model=${model.displayName}, RAM: ${deviceRamGB}GB" }

                val modelFile = File(context.filesDir, modelFilename)

                if (!modelFile.exists()) {
                    logger.i { "Copying $modelFilename to internal storage" }

                    context.assets.open("models/${modelFilename}").use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output, bufferSize = 8192)
                        }
                    }
                    logger.i { "Model copied (${modelFile.length() / 1024 / 1024} MB)" }
                } else {
                    logger.d { "Model $modelFilename already in storage (${modelFile.length() / 1024 / 1024} MB)" }
                }

                LlamaBridge.initGenerateModel(modelFile.absolutePath)
                LlamaBridge.updateGenerateParams(
                    temperature = defaultConfig.temperature!!,
                    maxTokens = getOptimalMaxTokens(),
                    topP = defaultConfig.topP!!,
                    topK = defaultConfig.topK!!,
                    repeatPenalty = defaultConfig.repetitionPenalty!!
                )

                isInitialized = true
                logger.i { "Initialization complete" }

                Result.success(Unit)
            } catch (e: Exception) {
                logger.e(e) { "Initialization failed" }
                e.printStackTrace()
                isInitialized = false
                Result.failure(RuntimeException("Failed to initialize LlamaCppEngine", e))
            }
        }
    }

    /**
     * Generate a single response (non-streaming).
     *
     * Uses Llamatik's non-streaming APIs:
     * - Pre-formatted prompts (from UnifiedPromptBuilder): `generate(prompt)` — pass-through,
     *   no additional template wrapping by native code.
     * - Raw prompts: `generateWithContext(system, context, user)` — native code applies
     *   the model's chat template.
     *
     * IMPORTANT: generateWithContext applies its own Gemma-style template in C++.
     * Passing a pre-formatted prompt as userPrompt causes double-formatting →
     * model hallucination → invalid UTF-8 bytes → JNI NewStringUTF SIGABRT.
     *
     * @param prompt User input text
     * @param config Generation configuration
     * @return Result.success(GenerationResult) or Result.failure(exception)
     */
    override suspend fun generate(
        prompt: String,
        config: GenerationConfig
    ): Result<GenerationResult> = withContext(Dispatchers.IO) {
        logger.i { "GENERATE" }

        if (!isInitialized) {
            return@withContext Result.failure(
                IllegalStateException("Engine not initialized. Call initialize() first.")
            )
        }

        val startTime = System.currentTimeMillis()
        val maxTokens = config.maxTokens?.takeIf { it > 0 } ?: getOptimalMaxTokens()

        try {
            LlamaBridge.updateGenerateParams(
                temperature = config.temperature ?: 0.7f,
                maxTokens = maxTokens,
                topP = 0.9f,
                topK = 40,
                repeatPenalty = 1.1f
            )

            val preformatted = isAlreadyFormatted(prompt)
            val rawResponse = if (preformatted) {
                // Pre-formatted prompt (from UnifiedPromptBuilder) — already has chat
                // template tokens. Use generate() which passes the prompt straight to
                // the model without additional wrapping.
                logger.d { "Generate (pre-formatted) -> ${prompt.length}c maxTokens=$maxTokens" }
                LlamaBridge.generate(prompt)
            } else {
                // Raw prompt — let native generateWithContext apply the model's
                // chat template (Gemma-style system/context/user structure).
                val systemPrompt = buildCleanSystemPrompt(config)
                val contextBlock = buildContextString(config)
                logger.d { "Generate (structured) -> system=${systemPrompt.length}c context=${contextBlock.length}c user=${prompt.length}c maxTokens=$maxTokens" }
                LlamaBridge.generateWithContext(systemPrompt, contextBlock, prompt)
            }

            val response = stripStopTokens(rawResponse)

            val inferenceTimeMs = System.currentTimeMillis() - startTime
            val estimatedTokens = (response.length / 4).coerceAtLeast(1)
            val tokensPerSecond = if (inferenceTimeMs > 0) {
                (estimatedTokens * 1000f) / inferenceTimeMs
            } else 0f

            logger.i { "Generation complete (${response.length} chars, ~$estimatedTokens tokens, ${inferenceTimeMs}ms, ${String.format("%.1f", tokensPerSecond)} tok/s)" }

            Result.success(
                GenerationResult(
                    text = response,
                    tokensGenerated = estimatedTokens,
                    inferenceTimeMs = inferenceTimeMs,
                    tokensPerSecond = tokensPerSecond
                )
            )
        } catch (e: Exception) {
            logger.e(e) { "Generation failed" }
            e.printStackTrace()
            Result.failure(RuntimeException("Generation failed", e))
        }
    }

    /**
     * Generate response with simulated streaming.
     *
     * Generates the complete response first, then emits word-by-word via onToken()
     * for progressive UI display.
     *
     * Uses the same pre-formatted/structured routing as generate():
     * - Pre-formatted → generate(prompt) — no double-formatting
     * - Raw → generateWithContext(system, context, user) — native applies template
     *
     * WHY simulated: ALL Llamatik streaming APIs (nativeGenerateStream,
     * nativeGenerateWithContextStream) call JNI NewStringUTF per token with
     * partial multi-byte UTF-8 sequences → fatal SIGABRT, non-catchable in Kotlin.
     *
     * @param prompt User input text
     * @param config Generation configuration
     * @param onToken Callback invoked for each word chunk
     * @return Result.success(Unit) or Result.failure(exception)
     */
    override suspend fun generateStreaming(
        prompt: String,
        config: GenerationConfig,
        onToken: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        logger.i { "GENERATE STREAMING (simulated)" }

        if (!isInitialized) {
            return@withContext Result.failure(
                IllegalStateException("Engine not initialized. Call initialize() first.")
            )
        }

        try {
            val maxTokens = config.maxTokens?.takeIf { it > 0 } ?: getOptimalMaxTokens()

            LlamaBridge.updateGenerateParams(
                temperature = config.temperature ?: 0.7f,
                maxTokens = maxTokens,
                topP = 0.9f,
                topK = 40,
                repeatPenalty = 1.1f
            )

            val preformatted = isAlreadyFormatted(prompt)
            val rawResponse = if (preformatted) {
                logger.d { "Streaming (pre-formatted) -> ${prompt.length}c maxTokens=$maxTokens" }
                LlamaBridge.generate(prompt)
            } else {
                val systemPrompt = buildCleanSystemPrompt(config)
                val contextBlock = buildContextString(config)
                logger.d { "Streaming (structured) -> system=${systemPrompt.length}c context=${contextBlock.length}c user=${prompt.length}c maxTokens=$maxTokens" }
                LlamaBridge.generateWithContext(systemPrompt, contextBlock, prompt)
            }

            val response = stripStopTokens(rawResponse)

            // Simulate streaming: emit word-by-word for progressive UI display.
            // Split on whitespace boundaries to preserve natural word spacing.
            val words = response.split("(?<=\\s)|(?=\\s)".toRegex())
            for (word in words) {
                if (word.isNotEmpty()) {
                    onToken(word)
                }
            }

            logger.i { "Simulated streaming complete (${response.length} chars, ${words.size} chunks)" }

            Result.success(Unit)
        } catch (e: Exception) {
            logger.e(e) { "Streaming failed" }
            Result.failure(RuntimeException("Streaming failed", e))
        }
    }

    /**
     * Strip stop tokens from model output.
     *
     * The non-streaming API may include stop tokens in the response
     * that the streaming path would normally detect and truncate at.
     */
    private fun stripStopTokens(response: String): String {
        val stopTokens = chatFormatter.getStopTokens()
        var cleaned = response
        for (stopToken in stopTokens) {
            val idx = cleaned.indexOf(stopToken)
            if (idx >= 0) {
                cleaned = cleaned.substring(0, idx)
            }
        }
        return cleaned.trim()
    }

    /**
     * Get device-appropriate maximum tokens for generation.
     *
     * Returns a very high limit to let the model's stop tokens determine completion naturally.
     * The model will stop when it generates <end_of_turn> or <eos> tokens.
     *
     * Note: High limits are safe because:
     * 1. Model stop tokens prevent runaway generation
     * 2. User can interrupt generation in UI
     * 3. Allows for longer, more complete responses
     *
     * @return High token limit (2048-4096 depending on device) to let model decide
     */
    override fun getOptimalMaxTokens(): Int {
        return when {
            deviceRamGB >= 12 -> 4096   // 12GB+: Let model decide naturally (~3000 words max)
            deviceRamGB >= 8 -> 3072    // 8-12GB: High limit (~2300 words max)
            deviceRamGB >= 6 -> 2048    // 6-8GB: Generous limit (~1500 words max)
            deviceRamGB >= 4 -> 1536    // 4-6GB: Reasonable limit (~1150 words max)
            else -> 1024                // <4GB: Conservative but usable (~750 words max)
        }
    }

    /**
     * Release llama.cpp resources.
     *
     * Note: Llamatik doesn't provide explicit cleanup APIs.
     * We just mark as uninitialized so re-initialization is required.
     */
    override fun release() {
        try {
            isInitialized = false
            logger.i { "Resources released (marked as uninitialized)" }
        } catch (e: Exception) {
            logger.e(e) { "Release failed" }
        }
    }

    // ==================== Llamatik Three-Parameter Structure ====================

    /**
     * Build CLEAN system prompt (identity + behavioral rules only).
     *
     * Llamatik's API design separates concerns:
     * - **system**: WHO the AI is + HOW to behave (this function)
     * - **context**: Background info (RAG facts, knowledge, conversation)
     * - **user**: The actual query
     *
     * This function builds ONLY the system part. NO facts, NO knowledge base descriptions.
     *
     * @param config Generation configuration
     * @return Clean system prompt (50-200 chars typical)
     */
    private fun buildCleanSystemPrompt(config: GenerationConfig): String {
        val deviceInfo = getDeviceContext()
        val userName = config.userContext?.get("name") ?: "Human"

        // Base identity (WHO)
        val identity = if (userName != null) {
            "You are M1K3 (Mike), $userName's local AI assistant running on $deviceInfo."
        } else {
            "You are M1K3 (Mike), a local AI assistant running on $deviceInfo."
        }

        // Behavioral rules (HOW) - with anti-hallucination & anti-repetition directives
        val temperature = config.temperature
        val behavior = when {
            temperature != null && temperature < 0.4f -> {
                " Be concise, factual, and direct. Avoid speculation. No repetition."
            }
            temperature != null && temperature >= 0.4f && temperature <= 0.8f -> {
                " Be helpful and accurate. Use only provided facts. If unsure, say so. " +
                "Avoid repetition. Each sentence must provide new information."
            }
            temperature != null && temperature > 0.8f -> {
                " Be creative and imaginative. Vary your phrasing."
            }
            else -> {
                " Be helpful and accurate. Use only provided facts. If unsure, say so. " +
                "Avoid repetition. Each sentence must provide new information."
            }
        }

        // Custom systemPrompt can override (but should also stay clean)
        return config.systemPrompt ?: (identity + behavior)
    }

    /**
     * Build context string from all available sources.
     *
     * Llamatik's context parameter should contain:
     * - RAG-retrieved facts (if available)
     * - Knowledge base summary (if RAG not used)
     * - Conversation history (if provided)
     *
     * This keeps the system prompt clean while providing background info.
     *
     * @param config Generation configuration
     * @return Context string (may be empty if no context available)
     */
    private fun buildContextString(config: GenerationConfig): String {
        val contextParts = mutableListOf<String>()

        // Add knowledge context if provided (either RAG facts or static KB summary)
        config.knowledgeContext?.let { contextParts.add(it) }

        // Future: Add conversation history from config.conversationHistory if provided
        // Future: Add user profile context if provided

        return contextParts.joinToString("\n\n")
    }

    /**
     * Build chat prompt using the injected ChatFormatter.
     *
     * This unified approach:
     * - Works with any ChatFormat (Gemma3, ChatML, Llama, etc.)
     * - Handles format-specific tokens automatically (BOS, stop tokens)
     * - Supports pre-formatted prompts (pass-through)
     *
     * @param systemPrompt System instructions (used by formatter)
     * @param context Background info (RAG, knowledge)
     * @param userQuery The user's message OR a pre-formatted prompt
     * @return Complete chat prompt ready for generation
     */
    private fun buildChatPrompt(
        systemPrompt: String,
        context: String,
        userQuery: String
    ): String {
        // If prompt is already formatted (from UnifiedPromptBuilder), use as-is
        if (isAlreadyFormatted(userQuery)) {
            return userQuery
        }

        // Build user message with context prepended
        val userContent = if (context.isNotBlank()) {
            "$context\n\n$userQuery"
        } else {
            userQuery
        }

        val messages = listOf(
            ChatMessage(role = MessageRole.USER, content = userContent)
        )

        // Use formatter to build format-aware prompt (includes BOS, stop tokens, etc.)
        return chatFormatter.buildPrompt(
            systemPrompt = systemPrompt,
            messages = messages
        )
    }

    /**
     * Check if prompt is already formatted with chat tokens.
     *
     * Detection looks for characteristic markers from any supported format.
     */
    private fun isAlreadyFormatted(prompt: String): Boolean {
        return prompt.contains("<start_of_turn>") ||     // Gemma3
               prompt.contains("<end_of_turn>") ||       // Gemma3
               prompt.contains("<|im_start|>") ||        // ChatML
               prompt.contains("[INST]") ||              // Llama
               prompt.contains("<|start_header_id|>") || // FalconH1
               prompt.startsWith("<bos>") ||             // Gemma3 BOS
               prompt.startsWith("<|begin_of_text|>")    // FalconH1 BOS
    }

    /**
     * Get device context string for dynamic system prompts.
     *
     * @return Device info string (e.g., "Google Pixel 6 Pro (12GB RAM)")
     */
    private fun getDeviceContext(): String {
        val deviceModel = android.os.Build.MODEL
        return "$deviceModel (${deviceRamGB}GB RAM)"
    }
}
