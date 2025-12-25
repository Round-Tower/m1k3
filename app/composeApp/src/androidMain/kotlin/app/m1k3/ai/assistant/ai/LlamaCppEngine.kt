package app.m1k3.ai.assistant.ai

import android.content.Context
import app.m1k3.ai.assistant.utils.Logger
import app.m1k3.ai.assistant.utils.resultOf
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
 * **Migration History:**
 * - v1 (ONNX Runtime): SmolLM2-135M produced severe hallucinations (tokenizer issues)
 * - v2 (InferKt 0.0.2): Native crash (SIGABRT in llama_batch_free, memory corruption)
 * - v3 (Llamatik 0.8.1): Stable with SmolLM2-135M but hallucinations
 * - v4 (Llamatik 0.9.0): Gemma 3 270M IQ3_XXS - requires special prompt handling
 *
 * **Why Llamatik:**
 * - Stable KMP library for llama.cpp on Android/iOS
 * - No native crashes in testing
 * - Simpler API = fewer failure modes
 *
 * **CRITICAL: Gemma 3 Prompt Structure**
 * Gemma 3 does NOT support a separate system role - only `user` and `model` roles.
 * See: https://ai.google.dev/gemma/docs/core/prompt-structure
 *
 * When calling `LlamaBridge.generateWithContextStream()`:
 * - `system` parameter: MUST be empty string
 * - `context` parameter: MUST be empty string
 * - `user` parameter: Combines instructions + context + user query
 *
 * This is handled by `buildGemma3UserMessage()` which structures the prompt as:
 * ```
 * [Instructions]
 * You are M1K3, a helpful assistant...
 *
 * [Context]
 * RAG facts, knowledge base info...
 *
 * [User]
 * The actual user question
 * ```
 *
 * **Sampling Parameters:**
 * Llamatik does NOT expose temperature/top_k/top_p/min_p in its Kotlin API.
 * Sampling is configured in the native llama.cpp layer with Gemma 3 defaults:
 * - temperature: 1.0, top_k: 64, top_p: 0.95
 * Behavioral control is achieved via prompt engineering.
 *
 * **Model Selection: Gemma 3 270M IQ3_XXS (176 MB)**
 * - Gemma 3 270M: Google's efficient small model with excellent reasoning
 * - IQ3_XXS quantization: 176 MB (under 200 MB cellular download limit)
 * - 32K context window
 * - Stop tokens: `<end_of_turn>`, `<eos>` (Gemma-specific only)
 *
 * **Context Window:** 32K tokens
 * **Library:** Llamatik 0.9.0
 *
 * **Usage:**
 * ```kotlin
 * val engine: BaseLlmEngine = LlamaCppEngine(context)
 * engine.initialize()
 *
 * val config = GenerationConfig(
 *     systemPrompt = "You are a helpful assistant"
 * )
 *
 * val result = engine.generate("Hello!", config)
 * println(result.text)
 *
 * engine.release()
 * ```
 */
class LlamaCppEngine(private val context: Context) : BaseLlmEngine {

    // Thread-safety: Use @Volatile for visibility + Mutex for initialization
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

    /**
     * Initialize llama.cpp engine with Gemma 3 270M GGUF model.
     *
     * Steps:
     * 1. Copy GGUF model from assets to internal storage (if not exists)
     * 2. Initialize Llamatik with model path
     *
     * Note: Llamatik's initGenerateModel() handles all configuration internally.
     * Gemma 3 270M provides 32K token context window (4x larger than SmolLM2-135M).
     *
     * @throws RuntimeException if initialization fails
     */
    override suspend fun initialize() = withContext(Dispatchers.IO) {
        // Fast path: already initialized (volatile read)
        if (isInitialized) {
            return@withContext
        }

        // Thread-safe initialization with mutex (prevents double-init race condition)
        initMutex.withLock {
            // Double-check after acquiring lock
            if (isInitialized) {
                return@withContext
            }

            try {
                logger.i { "Starting initialization (Device RAM: ${deviceRamGB}GB, Library: Llamatik 0.9.0)" }

                // 1. Copy GGUF model from assets to internal storage
                // Using Gemma 3 270M IQ3_XXS (176 MB) - much better quality than SmolLM2-135M
                val modelFile = File(context.filesDir, "gemma-3-270m-it-UD-IQ3_XXS.gguf")

                if (!modelFile.exists()) {
                    logger.i { "Copying Gemma 3 270M GGUF to internal storage (IQ3_XXS, 176 MB)" }
                    context.assets.open("models/gemma-3-270m-it-UD-IQ3_XXS.gguf").use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output, bufferSize = 8192)
                        }
                    }
                    logger.i { "Model copied (${modelFile.length() / 1024 / 1024} MB)" }
                } else {
                    logger.d { "Gemma 3 270M already in storage (${modelFile.length() / 1024 / 1024} MB)" }
                }

                // 2. Initialize Llamatik with model path
                // Note: Llamatik handles all configuration internally (context window, threads, etc.)
                logger.i { "Loading model with Llamatik" }
                LlamaBridge.initGenerateModel(modelFile.absolutePath)

                isInitialized = true
                logger.i { "Initialization complete - Gemma 3 270M ready (sampling: temp=1.0, top_k=64, top_p=0.95)" }

            } catch (e: Exception) {
                logger.e(e) { "Initialization failed" }
                e.printStackTrace()
                isInitialized = false
                throw RuntimeException("Failed to initialize LlamaCppEngine", e)
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
     * @return GenerationResult with full response text and performance metrics
     * @throws IllegalStateException if engine not initialized
     * @throws RuntimeException if inference fails
     */
    override suspend fun generate(
        prompt: String,
        config: GenerationConfig
    ): GenerationResult = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            throw IllegalStateException("Engine not initialized. Call initialize() first.")
        }

        val startTime = System.currentTimeMillis()
        val responseBuilder = StringBuilder()
        // Thread-safe counters for native callback access
        val tokenCount = AtomicInteger(0)

        // Get maxTokens limit (0 = return empty response immediately)
        val maxTokens = config.maxTokens ?: getOptimalMaxTokens()

        // Handle maxTokens = 0 edge case
        if (maxTokens == 0) {
            logger.d { "maxTokens=0, returning empty response" }
            return@withContext GenerationResult(
                text = "",
                tokensGenerated = 0,
                inferenceTimeMs = 0,
                tokensPerSecond = 0f
            )
        }

        try {
            // GEMMA 3 PROMPT STRUCTURE: User + Model roles ONLY (no system role!)
            // Using generateStream() with RAW prompt because generateWithContextStream()
            // uses <start_of_turn>system which Gemma 3 doesn't support
            val systemPrompt = buildCleanSystemPrompt(config)
            val contextParam = buildContextString(config)

            // Build complete Gemma 3 chat prompt with proper template
            val chatPrompt = buildGemma3ChatPrompt(systemPrompt, contextParam, prompt)

            logger.d {
                "Llamatik non-streaming (Gemma 3 raw template): prompt=${chatPrompt.length}chars, maxTokens=$maxTokens"
            }

            // Use streaming internally and collect full response
            // Gemma 3 stop tokens ONLY - no ChatML/LLaMA tokens
            val stopTokens = listOf("<end_of_turn>", "<eos>")
            // Thread-safe flags for native callback access (prevents double-resume race condition)
            val shouldStop = AtomicBoolean(false)
            val hasResumed = AtomicBoolean(false)

            suspendCancellableCoroutine<Unit> { continuation ->
                LlamaBridge.generateStream(
                    chatPrompt,
                    object : com.llamatik.library.platform.GenStream {
                        override fun onDelta(delta: String) {
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

                            responseBuilder.append(delta)
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

                        override fun onError(error: String) {
                            if (hasResumed.compareAndSet(false, true)) {
                                logger.e { "Generation error: $error" }
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

            GenerationResult(
                text = fullResponse,
                tokensGenerated = finalTokenCount,
                inferenceTimeMs = inferenceTimeMs,
                tokensPerSecond = tokensPerSecond
            )

        } catch (e: Exception) {
            logger.e(e) { "Generation failed" }
            e.printStackTrace()
            throw RuntimeException("Generation failed", e)
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
     * @throws IllegalStateException if engine not initialized
     * @throws RuntimeException if inference fails
     */
    override suspend fun generateStreaming(
        prompt: String,
        config: GenerationConfig,
        onToken: (String) -> Unit
    ) = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            throw IllegalStateException("Engine not initialized. Call initialize() first.")
        }

        try {
            // GEMMA 3 PROMPT STRUCTURE: User + Model roles ONLY (no system role!)
            // Gemma 3 doesn't support a separate system parameter - it must be combined into user message
            // See: https://ai.google.dev/gemma/docs/core/prompt-structure

            // Build CLEAN system prompt (identity + behavior only, no facts)
            val systemPrompt = buildCleanSystemPrompt(config)

            // Build context from all available sources (RAG facts, knowledge, conversation)
            val contextParam = buildContextString(config)

            // Build complete Gemma 3 chat prompt with proper template
            // Using generateStream() instead of generateWithContextStream() because
            // the latter uses <start_of_turn>system which Gemma 3 doesn't support
            val chatPrompt = buildGemma3ChatPrompt(systemPrompt, contextParam, prompt)

            // Get maxTokens limit (if 0, still stream but stop immediately)
            val maxTokens = config.maxTokens ?: getOptimalMaxTokens()

            logger.d {
                "Llamatik streaming (Gemma 3 raw template): prompt=${chatPrompt.length}chars, maxTokens=$maxTokens"
            }

            // DEBUG: Log actual prompt contents (truncated for readability)
            logger.v { ">>> GEMMA 3 PROMPT: ${chatPrompt.take(500)}${if (chatPrompt.length > 500) "..." else ""}" }

            // Thread-safe counters and flags for native callback access
            val tokenCount = AtomicInteger(0)
            // Gemma 3 stop tokens ONLY - no ChatML/LLaMA tokens
            val stopTokens = listOf("<end_of_turn>", "<eos>")
            val responseBuffer = StringBuilder()
            val shouldStop = AtomicBoolean(false)
            val hasResumed = AtomicBoolean(false)

            // Stream tokens with Llamatik using RAW prompt (no templating)
            suspendCancellableCoroutine<Unit> { continuation ->
                LlamaBridge.generateStream(
                    chatPrompt,
                    object : com.llamatik.library.platform.GenStream {
                        override fun onDelta(delta: String) {
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
                            responseBuffer.append(delta)
                            tokenCount.incrementAndGet()

                            // Check if we've hit a stop token
                            val currentText = responseBuffer.toString()
                            for (stopToken in stopTokens) {
                                if (currentText.contains(stopToken)) {
                                    shouldStop.set(true)
                                    logger.d { "Stop token detected: \"$stopToken\" after ${tokenCount.get()} tokens" }

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

                            onToken(delta)  // Stream to UI
                        }

                        override fun onComplete() {
                            if (hasResumed.compareAndSet(false, true)) {
                                logger.i { "Streaming complete (${tokenCount.get()} tokens)" }

                                // DEBUG: Log final response (truncated)
                                val finalResponse = responseBuffer.toString()
                                logger.v { "<<< RESPONSE (${finalResponse.length} chars): ${finalResponse.take(500)}${if (finalResponse.length > 500) "..." else ""}" }

                                continuation.resume(Unit)
                            }
                        }

                        override fun onError(error: String) {
                            if (hasResumed.compareAndSet(false, true)) {
                                logger.e { "Streaming error: $error" }
                                continuation.resume(Unit)
                            }
                        }
                    }
                )
            }

        } catch (e: Exception) {
            logger.e(e) { "Streaming failed" }
            throw RuntimeException("Streaming failed", e)
        }
    }

    /**
     * Get device-appropriate maximum tokens for generation.
     *
     * Since Llamatik doesn't expose configuration APIs, we return reasonable defaults
     * based on device RAM. The actual behavior is controlled by Llamatik internally.
     *
     * Note: Minimum 256 tokens for usable responses (~192 words).
     * Gemma 3 270M handles this well even on low-RAM devices.
     *
     * @return Recommended max tokens (256-512 depending on device)
     */
    override fun getOptimalMaxTokens(): Int {
        return when {
            deviceRamGB >= 12 -> 512   // 12GB+: Long responses (~384 words)
            deviceRamGB >= 8 -> 384    // 8-12GB: Medium-long responses (~288 words)
            deviceRamGB >= 6 -> 320    // 6-8GB: Medium responses (~240 words)
            deviceRamGB >= 4 -> 256    // 4-6GB: Short responses (~192 words)
            else -> 256                 // <4GB: Minimum usable (~192 words) - emulators + budget devices
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
            // Llamatik doesn't provide cleanup API
            // Just mark as uninitialized
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
        val userName = config.userContext?.get("name")

        // Base identity (WHO)
        val identity = if (userName != null) {
            "You are M1K3 (Mike), $userName's local AI assistant running on $deviceInfo."
        } else {
            "You are M1K3 (Mike), a local AI assistant running on $deviceInfo."
        }

        // Behavioral rules (HOW) - with anti-hallucination & anti-repetition directives
        val behavior = when {
            config.temperature != null && config.temperature < 0.4f -> {
                " Be concise, factual, and direct. Avoid speculation. No repetition."
            }
            config.temperature != null && config.temperature >= 0.4f && config.temperature <= 0.8f -> {
                " Be helpful and accurate. Use only provided facts. If unsure, say so. " +
                "Avoid repetition. Each sentence must provide new information."
            }
            config.temperature != null && config.temperature > 0.8f -> {
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
     * Build Gemma 3 compatible user message.
     *
     * **CRITICAL:** Gemma 3 does NOT support a separate system role!
     * It only has two roles: user and model.
     * See: https://ai.google.dev/gemma/docs/core/prompt-structure
     *
     * This function combines:
     * - System instructions (identity + behavior)
     * - Context (RAG facts, knowledge, conversation history)
     * - User query
     *
     * Into a single user message that Gemma 3 can properly process.
     *
     * @param systemPrompt System instructions (identity + behavior)
     * @param context Background info (RAG, knowledge, conversation)
     * @param userQuery The actual user question/request
     * @return Combined user message for Gemma 3
     */
    /**
     * Build a complete Gemma 3 chat prompt with proper template.
     *
     * **CRITICAL:** We use LlamaBridge.generateStream() with a RAW prompt because
     * LlamaBridge.generateWithContextStream() uses a template with `<start_of_turn>system`
     * which Gemma 3 does NOT support (only `user` and `model` roles).
     *
     * Gemma 3 template format:
     * ```
     * <bos><start_of_turn>user
     * {user_message}<end_of_turn>
     * <start_of_turn>model
     * ```
     *
     * @param systemPrompt System instructions (goes into user message)
     * @param context Background info (RAG, knowledge)
     * @param userQuery The actual user question
     * @return Complete Gemma 3 chat prompt ready for generation
     */
    /**
     * Build Gemma 3 prompt using the testable Gemma3PromptBuilder.
     *
     * Note: systemPrompt is intentionally ignored for 270M models.
     * Complex instructions confuse small models - let RAG + question do the work.
     */
    private fun buildGemma3ChatPrompt(
        systemPrompt: String,
        context: String,
        userQuery: String
    ): String = Gemma3PromptBuilder.build(
        userQuery = userQuery,
        context = context.ifBlank { null }
    )

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
