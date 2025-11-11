package app.m1k3.ai.assistant.ai

import android.content.Context
import app.m1k3.ai.assistant.utils.Logger
import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

private val logger = Logger.withTag("LlamaCppEngine")

/**
 * LlamaCppEngine - llama.cpp-based AI inference engine via Llamatik
 *
 * **Migration History:**
 * - v1 (ONNX Runtime): SmolLM2-135M produced severe hallucinations (tokenizer issues)
 * - v2 (InferKt 0.0.2): Native crash (SIGABRT in llama_batch_free, memory corruption)
 * - v3 (Llamatik 0.8.1): Current implementation - stable, simple API
 *
 * **Why Llamatik:**
 * - Recently updated (6 days ago) with active maintenance
 * - Stable 0.8.x version (mature API)
 * - No native crashes in testing
 * - Simpler API = fewer failure modes
 * - Network inference capability (future feature)
 *
 * **Trade-offs vs InferKt:**
 * - ✅ Stability: No native crashes
 * - ✅ Simplicity: No complex configuration APIs
 * - ❌ Lost: Device-adaptive context window (512-2048 tokens)
 * - ❌ Lost: Thread count control (2-6 threads)
 * - ❌ Lost: Sampling parameters (temperature, topP, topK, minP)
 * - 🔧 Workaround: Prompt engineering for behavioral control
 *
 * **Model Selection: SmolLM2-135M Q4_K_M (101 MB)**
 *
 * SmolLM2-135M chosen over 360M due to APK size constraints:
 * - APK budget: 200MB total (current: 149MB with embeddings/app/model)
 * - SmolLM2-360M options: Q4_K_M = 271MB (35.5% over), Q2_K = 219MB (severe quality loss)
 * - RAG enhancement: 1,401 documents compensate for smaller model
 * - Philosophy alignment: 間 (Ma) computational sufficiency - "135M is enough"
 * - Future path: 360M via dynamic delivery (Phase 3, optional download)
 *
 * See app/OPUS.md "Final Decision" section for complete analysis (2025-11-11)
 *
 * **Context Window:** 8K tokens
 * **Library:** Llamatik 0.8.1
 *
 * **Usage:**
 * ```kotlin
 * val engine: BaseLlmEngine = LlamaCppEngine(context)
 * engine.initialize()
 *
 * val config = GenerationConfig(
 *     temperature = 0.7f,  // Note: Llamatik ignores this, we use prompt engineering
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

    private var isInitialized = false

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
        if (isInitialized) {
            return@withContext
        }

        try {
            logger.i { "Starting initialization (Device RAM: ${deviceRamGB}GB, Library: Llamatik 0.8.1)" }

            // 1. Copy GGUF model from assets to internal storage
            val modelFile = File(context.filesDir, "smollm2-135m-q4.gguf")

            if (!modelFile.exists()) {
                logger.i { "Copying SmolLM2-135M GGUF to internal storage (Q4_K_M, 101 MB, 8K context)" }
                context.assets.open("models/smollm2-135m-q4.gguf").use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
                logger.i { "Model copied (${modelFile.length() / 1024 / 1024} MB)" }
            } else {
                logger.d { "SmolLM2-135M already in storage (${modelFile.length() / 1024 / 1024} MB)" }
            }

            // 2. Initialize Llamatik with model path
            // Note: Llamatik handles all configuration internally (context window, threads, etc.)
            logger.i { "Loading model with Llamatik" }
            LlamaBridge.initGenerateModel(modelFile.absolutePath)

            isInitialized = true
            logger.i { "Initialization complete (Note: Temperature/sampling control via prompt engineering only)" }

        } catch (e: Exception) {
            logger.e(e) { "Initialization failed" }
            e.printStackTrace()
            isInitialized = false
            throw RuntimeException("Failed to initialize LlamaCppEngine", e)
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
        var tokenCount = 0

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
            // LLAMATIK API DESIGN: system (identity+behavior) / context (facts) / user (query)
            val systemPrompt = buildCleanSystemPrompt(config)
            val contextParam = buildContextString(config)

            logger.d {
                "Llamatik non-streaming: user=${prompt.length}chars, maxTokens=$maxTokens, " +
                "system=${systemPrompt.length}chars, context=${contextParam.length}chars"
            }

            // Use streaming internally and collect full response
            val stopTokens = listOf("<end_of_turn>", "</s>", "<|endoftext|>", "<|im_end|>")
            var shouldStop = false
            var hasResumed = false

            suspendCancellableCoroutine<Unit> { continuation ->
                LlamaBridge.generateWithContextStream(
                    system = systemPrompt,
                    context = contextParam,
                    user = prompt,
                    onDelta = { delta ->
                        if (shouldStop) return@generateWithContextStream

                        // Check if we've hit maxTokens limit
                        if (tokenCount >= maxTokens) {
                            shouldStop = true
                            logger.d { "maxTokens limit reached: $tokenCount >= $maxTokens" }
                            if (!hasResumed) {
                                hasResumed = true
                                continuation.resume(Unit)
                            }
                            return@generateWithContextStream
                        }

                        responseBuilder.append(delta)
                        tokenCount++

                        // Check if we've hit a stop token
                        val currentText = responseBuilder.toString()
                        for (stopToken in stopTokens) {
                            if (currentText.contains(stopToken)) {
                                shouldStop = true
                                logger.d { "Stop token detected: \"$stopToken\" after $tokenCount tokens" }

                                // Truncate response at stop token
                                val textBeforeStop = currentText.substringBefore(stopToken)
                                responseBuilder.clear()
                                responseBuilder.append(textBeforeStop)

                                // Resume only if not already resumed
                                if (!hasResumed) {
                                    hasResumed = true
                                    continuation.resume(Unit)
                                }
                                return@generateWithContextStream
                            }
                        }
                    },
                    onDone = {
                        if (!hasResumed) {
                            hasResumed = true
                            logger.d { "Generation complete" }
                            continuation.resume(Unit)
                        }
                    },
                    onError = { error ->
                        if (!hasResumed) {
                            hasResumed = true
                            logger.e { "Generation error: $error" }
                            continuation.resume(Unit)
                        }
                    }
                )
            }

            val inferenceTimeMs = System.currentTimeMillis() - startTime
            val tokensPerSecond = if (inferenceTimeMs > 0) {
                (tokenCount * 1000f) / inferenceTimeMs
            } else 0f

            val fullResponse = responseBuilder.toString()

            logger.i { "Generation complete (length=${fullResponse.length} chars, tokens=$tokenCount, time=${inferenceTimeMs}ms, ${String.format("%.1f", tokensPerSecond)} tok/s)" }

            GenerationResult(
                text = fullResponse,
                tokensGenerated = tokenCount,
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
            // LLAMATIK API DESIGN (three-parameter structure):
            // - system: Identity + behavioral rules (WHO the AI is, HOW to behave)
            // - context: Background information (RAG facts, knowledge, conversation history)
            // - user: The actual query

            // Build CLEAN system prompt (identity + behavior only, no facts)
            val systemPrompt = buildCleanSystemPrompt(config)

            // Build context from all available sources (RAG facts, knowledge, conversation)
            val contextParam = buildContextString(config)

            // Get maxTokens limit (if 0, still stream but stop immediately)
            val maxTokens = config.maxTokens ?: getOptimalMaxTokens()

            logger.d {
                "Llamatik generation: user=${prompt.length}chars, maxTokens=$maxTokens, " +
                "system=${systemPrompt.length}chars, context=${contextParam.length}chars"
            }

            var tokenCount = 0
            // SmolLM2-specific stop tokens (ChatML format)
            // DO NOT add tokens from other models (Gemma's <end_of_turn>, LLaMA's </s>)
            // as they cause false positives when SmolLM2 generates text containing those strings
            val stopTokens = listOf("<|im_end|>", "<|endoftext|>")
            val responseBuffer = StringBuilder()
            var shouldStop = false
            var hasResumed = false

            // Stream tokens with Llamatik
            suspendCancellableCoroutine<Unit> { continuation ->
                LlamaBridge.generateWithContextStream(
                    system = systemPrompt,
                    context = contextParam,
                    user = prompt,
                    onDelta = { delta ->
                        if (shouldStop) return@generateWithContextStream

                        // Check if we've hit maxTokens limit
                        if (tokenCount >= maxTokens) {
                            shouldStop = true
                            logger.d { "maxTokens limit reached in streaming: $tokenCount >= $maxTokens" }
                            if (!hasResumed) {
                                hasResumed = true
                                continuation.resume(Unit)
                            }
                            return@generateWithContextStream
                        }

                        // Accumulate tokens to detect stop sequences
                        responseBuffer.append(delta)
                        tokenCount++

                        // Check if we've hit a stop token
                        val currentText = responseBuffer.toString()
                        for (stopToken in stopTokens) {
                            if (currentText.contains(stopToken)) {
                                shouldStop = true
                                logger.d { "Stop token detected: \"$stopToken\" after $tokenCount tokens" }

                                // Send only text before stop token
                                val textBeforeStop = currentText.substringBefore(stopToken)
                                if (textBeforeStop.isNotEmpty()) {
                                    // Clear buffer and send final clean text
                                    responseBuffer.clear()
                                    responseBuffer.append(textBeforeStop)
                                }

                                // Resume only if not already resumed
                                if (!hasResumed) {
                                    hasResumed = true
                                    continuation.resume(Unit)
                                }
                                return@generateWithContextStream
                            }
                        }

                        onToken(delta)  // Stream to UI
                    },
                    onDone = {
                        if (!hasResumed) {
                            hasResumed = true
                            logger.i { "Streaming complete ($tokenCount tokens)" }
                            continuation.resume(Unit)
                        }
                    },
                    onError = { error ->
                        if (!hasResumed) {
                            hasResumed = true
                            logger.e { "Streaming error: $error" }
                            continuation.resume(Unit)
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
     * Note: Minimum increased to 256 tokens for usable responses (~192 words).
     * SmolLM2-135M is efficient enough to handle this even on low-RAM devices.
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

        // Behavioral rules (HOW) - based on temperature
        val behavior = when {
            config.temperature != null && config.temperature < 0.4f -> {
                " Be concise, factual, and direct. Avoid speculation."
            }
            config.temperature != null && config.temperature >= 0.4f && config.temperature <= 0.8f -> {
                " Be helpful and accurate. Use provided facts. Do not make things up."
            }
            config.temperature != null && config.temperature > 0.8f -> {
                " Be creative and imaginative."
            }
            else -> {
                " Be helpful and accurate."
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
     * Get device context string for dynamic system prompts.
     *
     * @return Device info string (e.g., "Google Pixel 6 Pro (12GB RAM)")
     */
    private fun getDeviceContext(): String {
        val deviceModel = android.os.Build.MODEL
        return "$deviceModel (${deviceRamGB}GB RAM)"
    }
}
