package app.m1k3.ai.assistant.ai

import android.content.Context
import app.m1k3.ai.assistant.utils.Logger
import app.m1k3.ai.assistant.utils.resultOf
import app.m1k3.ai.domain.ai.GenerationConfig
import app.m1k3.ai.domain.chat.format.ChatFormat
import app.m1k3.ai.domain.chat.format.MessageRole
import app.m1k3.ai.domain.chat.services.ChatFormatter
import app.m1k3.ai.domain.chat.services.ChatMessage
import app.m1k3.ai.domain.chat.services.DefaultChatFormatter
import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

private val logger = Logger.withTag("LlamaCppEngine")

/**
 * LlamaCppEngine - llama.cpp-based AI inference engine via Llamatik
 *
 * @param context Android context for file access
 * @param chatFormatter Optional formatter for prompt construction (defaults to Gemma3)
 */
class LlamaCppEngine(
    private val context: Context,
    private val chatFormatter: ChatFormatter = DefaultChatFormatter(ChatFormat.Gemma3)
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

    private var modelMini = "gemma-3-270m-it-UD-IQ3_XXS.gguf"
    private var defaultConfig = GenerationConfig()

    /**
     * Initialize llama.cpp engine with Gemma 3 270M GGUF model.
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
                logger.i { "Starting initialization -> RAM: ${deviceRamGB}GB" }

                val modelFile = File(context.filesDir, modelMini)

                if (!modelFile.exists()) {
                    logger.i { "Copying $modelMini to internal storage" }

                    context.assets.open("models/${modelMini}").use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output, bufferSize = 8192)
                        }
                    }
                    logger.i { "Model copied (${modelFile.length() / 1024 / 1024} MB)" }
                } else {
                    logger.d { "Model already in storage (${modelFile.length() / 1024 / 1024} MB)" }
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
     * Blocks until full response is generated, then returns GenerationResult.
     *
     * @param prompt User input text
     * @param config Generation configuration
     * @return Result.success(GenerationResult) if generation succeeds, Result.failure(exception) otherwise
     */
    override suspend fun generate(
        prompt: String,
        config: GenerationConfig
    ): Result<GenerationResult> = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            return@withContext Result.failure(
                IllegalStateException("Engine not initialized. Call initialize() first.")
            )
        }

        val startTime = System.currentTimeMillis()
        val responseBuilder = StringBuilder()
        val tokenCount = AtomicInteger(0)

        // Get maxTokens limit (0 = return empty response immediately)
        val maxTokens = config.maxTokens ?: getOptimalMaxTokens()

        try {
            val systemPrompt = buildCleanSystemPrompt(config)
            val contextParam = buildContextString(config)
            val chatPrompt = buildChatPrompt(systemPrompt, contextParam, prompt)

            logger.d {
                "Prompt -> ${chatPrompt} - ${chatPrompt.length}chars - maxTokens=$maxTokens"
            }

            val stopTokens = chatFormatter.getStopTokens()
            val shouldStop = AtomicBoolean(false)
            val hasResumed = AtomicBoolean(false)

            suspendCancellableCoroutine { continuation ->
                LlamaBridge.generateStream(
                    chatPrompt,
                    object : com.llamatik.library.platform.GenStream {
                        override fun onDelta(text: String) {
                            if (shouldStop.get()) return

                            // Check if we've hit maxTokens limit
                            val currentCount = tokenCount.get()
                            if (currentCount >= maxTokens) {
                                shouldStop.set(true)
                                logger.d { "maxTokens limit reached: $currentCount >= $maxTokens" }

                                if (hasResumed.compareAndSet(false, true)) {
                                    continuation.resume(Unit)
                                }
                                return
                            }

                            responseBuilder.append(text)
                            tokenCount.incrementAndGet()

                            // Check if we've hit a stop token
                            val currentText = responseBuilder.toString()
                            for (stopToken in stopTokens) {
                                if (currentText.contains(stopToken)) {
                                    shouldStop.set(true)
                                    logger.d { "Stop token detected: \"$stopToken\" after ${tokenCount.get()} tokens" }

                                    // Truncate response at stop token
                                    val textBeforeStop = currentText.substringBefore(stopToken)
                                    responseBuilder.clear()
                                    responseBuilder.append(textBeforeStop)

                                    // Resume only if not already resumed (atomic check-and-set)
                                    if (hasResumed.compareAndSet(false, true)) {
                                        continuation.resume(Unit)
                                    }
                                    return
                                }
                            }
                        }

                        override fun onComplete() {
                            if (hasResumed.compareAndSet(false, true)) {
                                logger.d { "Generation complete" }
                                continuation.resume(Unit)
                            }
                        }

                        override fun onError(message: String) {
                            if (hasResumed.compareAndSet(false, true)) {
                                logger.e { "Generation error: $message" }
                                continuation.resume(Unit)
                            }
                        }
                    }
                )
            }

            val inferenceTimeMs = System.currentTimeMillis() - startTime
            val finalTokenCount = tokenCount.get()
            val tokensPerSecond = if (inferenceTimeMs > 0) {
                (finalTokenCount * 1000f) / inferenceTimeMs
            } else 0f

            val fullResponse = responseBuilder.toString()

            logger.i { "Generation complete (length=${fullResponse.length} chars, tokens=$finalTokenCount, time=${inferenceTimeMs}ms, ${String.format("%.1f", tokensPerSecond)} tok/s)" }

            Result.success(
                GenerationResult(
                    text = fullResponse,
                    tokensGenerated = finalTokenCount,
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
     * Generate response with streaming token-by-token callback.
     *
     * For real-time UI updates, calls onToken() for each generated token.
     *
     * @param prompt User input text
     * @param config Generation configuration
     * @param onToken Callback invoked for each generated token
     * @return Result.success(Unit) if streaming completes, Result.failure(exception) if error occurs
     */
    override suspend fun generateStreaming(
        prompt: String,
        config: GenerationConfig,
        onToken: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext Result.failure(
                IllegalStateException("Engine not initialized. Call initialize() first.")
            )
        }

        try {
//            val systemPrompt = buildCleanSystemPrompt(config)
//            val contextParam = buildContextString(config)
//            val chatPrompt = buildChatPrompt(systemPrompt, contextParam, prompt)

            val maxTokens = config.maxTokens ?: getOptimalMaxTokens()

            logger.d {
                "Generate with prompt -> ${prompt} - ${prompt.length} chars - maxTokens=$maxTokens"
            }

            // Thread-safe counters and flags for native callback access
            val tokenCount = AtomicInteger(0)

            val stopTokens = chatFormatter.getStopTokens()
            val responseBuffer = StringBuilder()
            val shouldStop = AtomicBoolean(false)
            val hasResumed = AtomicBoolean(false)

            // Stream tokens with Llamatik using RAW prompt (no templating)
            suspendCancellableCoroutine { continuation ->
                LlamaBridge.generateStream(
                    prompt,
                    object : com.llamatik.library.platform.GenStream {
                        override fun onDelta(text: String) {
                            if (shouldStop.get()) return

                            // Check if we've hit maxTokens limit
                            val currentCount = tokenCount.get()
                            if (currentCount >= maxTokens) {
                                shouldStop.set(true)
                                logger.d { "maxTokens limit reached in streaming: $currentCount >= $maxTokens" }
                                if (hasResumed.compareAndSet(false, true)) {
                                    continuation.resume(Unit)
                                }
                                return
                            }

                            // Accumulate tokens to detect stop sequences
                            responseBuffer.append(text)
                            tokenCount.incrementAndGet()

                            // Check if we've hit a stop token
                            val currentText = responseBuffer.toString()
                            for (stopToken in stopTokens) {
                                if (currentText.contains(stopToken)) {
                                    shouldStop.set(true)
                                    logger.d { "STOP TOKEN detected: \"$stopToken\" after ${tokenCount.get()} tokens" }

                                    // Send only text before stop token
                                    val textBeforeStop = currentText.substringBefore(stopToken)
                                    if (textBeforeStop.isNotEmpty()) {
                                        // Clear buffer and send final clean text
                                        responseBuffer.clear()
                                        responseBuffer.append(textBeforeStop)
                                    }

                                    // Resume only if not already resumed (atomic check-and-set)
                                    if (hasResumed.compareAndSet(false, true)) {
                                        continuation.resume(Unit)
                                    }
                                    return
                                }
                            }

                            onToken(text)  // Stream to UI
                        }

                        override fun onComplete() {
                            if (hasResumed.compareAndSet(false, true)) {
                                logger.i { "Streaming complete (${tokenCount.get()} tokens)" }

                                val finalResponse = responseBuffer.toString()
                                logger.v { "RESPONSE (${finalResponse.length} chars): ${finalResponse.take(500)}${if (finalResponse.length > 500) "..." else ""}" }

                                continuation.resume(Unit)
                            }
                        }

                        override fun onError(message: String) {
                            if (hasResumed.compareAndSet(false, true)) {
                                logger.e { "Streaming error: $message" }
                                continuation.resume(Unit)
                            }
                        }
                    }
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            logger.e(e) { "Streaming failed" }
            Result.failure(RuntimeException("Streaming failed", e))
        }
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
        return prompt.contains("<start_of_turn>") ||  // Gemma3
               prompt.contains("<end_of_turn>") ||    // Gemma3
               prompt.contains("<|im_start|>") ||     // ChatML
               prompt.contains("[INST]") ||           // Llama
               prompt.startsWith("<bos>")             // Already has BOS
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
