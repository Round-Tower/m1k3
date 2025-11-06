package app.m1k3.ai.assistant.ai

import android.content.Context
import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

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
 * **Model:** SmolLM2-135M-Instruct Q4_K_M (101 MB)
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
     * Initialize llama.cpp engine with SmolLM2-135M GGUF model.
     *
     * Steps:
     * 1. Copy GGUF model from assets to internal storage (if not exists)
     * 2. Initialize Llamatik with model path
     *
     * Note: Llamatik's initGenerateModel() handles all configuration internally.
     * We lose fine-grained control over context window, threads, etc.
     *
     * @throws RuntimeException if initialization fails
     */
    override suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) {
            return@withContext
        }

        try {
            println("🚀 [LlamaCppEngine] Starting initialization...")
            println("   Device RAM: ${deviceRamGB}GB")
            println("   Library: Llamatik 0.8.1")

            // 1. Copy GGUF model from assets to internal storage
            val modelFile = File(context.filesDir, "smollm2-135m-q4.gguf")

            if (!modelFile.exists()) {
                println("   📥 Copying GGUF model to internal storage (one-time operation)...")
                context.assets.open("models/smollm2-135m-q4.gguf").use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
                println("   ✓ Model copied (${modelFile.length() / 1024 / 1024} MB)")
            } else {
                println("   ✓ Model already in storage (${modelFile.length() / 1024 / 1024} MB)")
            }

            // 2. Initialize Llamatik with model path
            // Note: Llamatik handles all configuration internally (context window, threads, etc.)
            println("   📖 Loading model with Llamatik...")
            LlamaBridge.initGenerateModel(modelFile.absolutePath)

            isInitialized = true
            println("✅ [LlamaCppEngine] Initialization complete")
            println("   ⚠️  Note: Temperature/sampling control via prompt engineering only")

        } catch (e: Exception) {
            println("❌ [LlamaCppEngine] Initialization failed: ${e.message}")
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

        try {
            // Build system prompt with prompt engineering for behavioral control
            val systemPrompt = buildSystemPrompt(config)

            // Always use knowledgeContext in the context parameter
            // Llamatik will handle combining system + context + user appropriately
            val contextParam = config.knowledgeContext ?: ""

            println("🔍 [LlamaCppEngine] Starting generation...")
            println("   Prompt: \"$prompt\"")
            println("   System: \"${systemPrompt.take(150)}...\"")
            println("   Context: ${if (contextParam.isNotEmpty()) "${contextParam.take(150)}..." else "empty"}")

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

                        responseBuilder.append(delta)
                        tokenCount++

                        // Check if we've hit a stop token
                        val currentText = responseBuilder.toString()
                        for (stopToken in stopTokens) {
                            if (currentText.contains(stopToken)) {
                                shouldStop = true
                                println("🛑 [LlamaCppEngine] Stop token detected: \"$stopToken\"")

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
                            println("   ✅ Generation complete")
                            continuation.resume(Unit)
                        }
                    },
                    onError = { error ->
                        if (!hasResumed) {
                            hasResumed = true
                            println("   ❌ Generation error: $error")
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

            println("✅ [LlamaCppEngine] Generation complete")
            println("   Response length: ${fullResponse.length} chars")
            println("   Tokens: $tokenCount")
            println("   Time: ${inferenceTimeMs}ms (${String.format("%.1f", tokensPerSecond)} tok/s)")

            GenerationResult(
                text = fullResponse,
                tokensGenerated = tokenCount,
                inferenceTimeMs = inferenceTimeMs,
                tokensPerSecond = tokensPerSecond
            )

        } catch (e: Exception) {
            println("❌ [LlamaCppEngine] Generation failed: ${e.message}")
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
            // Build system prompt with prompt engineering for behavioral control
            val systemPrompt = buildSystemPrompt(config)

            // Always use knowledgeContext in the context parameter
            // Llamatik will handle combining system + context + user appropriately
            val contextParam = config.knowledgeContext ?: ""

            println("🔍 [LlamaCppEngine] Starting streaming generation...")
            println("   Prompt: \"$prompt\"")
            println("   System: \"${systemPrompt.take(150)}...\"")
            println("   Context: ${if (contextParam.isNotEmpty()) "${contextParam.take(150)}..." else "empty"}")

            var tokenCount = 0
            val stopTokens = listOf("<end_of_turn>", "</s>", "<|endoftext|>", "<|im_end|>")
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

                        // Accumulate tokens to detect stop sequences
                        responseBuffer.append(delta)
                        tokenCount++

                        // Check if we've hit a stop token
                        val currentText = responseBuffer.toString()
                        for (stopToken in stopTokens) {
                            if (currentText.contains(stopToken)) {
                                shouldStop = true
                                println("🛑 [LlamaCppEngine] Stop token detected: \"$stopToken\"")
                                println("   Stopping generation early at $tokenCount tokens")

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
                            println("✅ [LlamaCppEngine] Streaming complete ($tokenCount tokens)")
                            continuation.resume(Unit)
                        }
                    },
                    onError = { error ->
                        if (!hasResumed) {
                            hasResumed = true
                            println("❌ [LlamaCppEngine] Streaming failed: $error")
                            continuation.resume(Unit)
                        }
                    }
                )
            }

        } catch (e: Exception) {
            println("❌ [LlamaCppEngine] Streaming failed: ${e.message}")
            e.printStackTrace()
            throw RuntimeException("Streaming failed", e)
        }
    }

    /**
     * Get device-appropriate maximum tokens for generation.
     *
     * Since Llamatik doesn't expose configuration APIs, we return reasonable defaults
     * based on device RAM. The actual behavior is controlled by Llamatik internally.
     *
     * @return Recommended max tokens (64-512 depending on device)
     */
    override fun getOptimalMaxTokens(): Int {
        return when {
            deviceRamGB >= 12 -> 512   // 12GB+: Long responses
            deviceRamGB >= 8 -> 384    // 8-12GB: Medium-long responses
            deviceRamGB >= 6 -> 256    // 6-8GB: Medium responses (default)
            deviceRamGB >= 4 -> 128    // 4-6GB: Short responses
            else -> 64                  // <4GB: Very short responses
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
            println("✅ [LlamaCppEngine] Resources released (marked as uninitialized)")
        } catch (e: Exception) {
            println("❌ [LlamaCppEngine] Release failed: ${e.message}")
            e.printStackTrace()
        }
    }

    // ==================== Prompt Engineering for Behavioral Control ====================

    /**
     * Build system prompt with prompt engineering to compensate for lost sampling control.
     *
     * **Problem:** Llamatik doesn't expose temperature/sampling APIs.
     * **Solution:** Use prompt engineering to guide model behavior.
     *
     * Temperature simulation:
     * - Low (0.0-0.3): Add "Be concise, factual, and direct" instructions
     * - Medium (0.4-0.7): Balanced default behavior
     * - High (0.8-1.0): Add "Be creative, imaginative, and expansive" instructions
     *
     * @param config Generation configuration
     * @return Enhanced system prompt with behavioral guidance
     */
    private fun buildSystemPrompt(config: GenerationConfig): String {
        val deviceInfo = getDeviceContext()
        val userName = config.userContext?.get("name")

        // Base M1K3 prompt
        val basePrompt = if (userName != null) {
            "You are M1K3 (Mike), $userName's privacy-first local AI assistant running on $deviceInfo"
        } else {
            "You are M1K3 (Mike), privacy-first local AI assistant running on $deviceInfo"
        }

        // Prompt engineering for temperature control
        val behaviorGuidance = when {
            // Low temperature (deterministic, factual)
            config.temperature != null && config.temperature < 0.4f -> {
                "\n\nIMPORTANT: Be concise, factual, and direct in your responses. Avoid speculation or creativity."
            }
            // High temperature (creative, expansive)
            config.temperature != null && config.temperature > 0.7f -> {
                "\n\nIMPORTANT: Be creative, imaginative, and expansive in your responses. Feel free to explore ideas."
            }
            // Medium temperature (balanced) - default behavior
            else -> ""
        }

        // Custom system prompt overrides default
        // If custom systemPrompt is provided (e.g., RAG-enhanced), use it as-is
        // (RAG-enhanced prompts already contain the relevant facts)
        val finalBase = config.systemPrompt ?: (basePrompt + behaviorGuidance)

        // Append knowledge context if provided (only when not using custom RAG prompt)
        // Note: ChatScreen now conditionally passes knowledgeContext based on ragResult.ragApplied
        return if (config.knowledgeContext != null) {
            "$finalBase\n\n${config.knowledgeContext}"
        } else {
            finalBase
        }
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
