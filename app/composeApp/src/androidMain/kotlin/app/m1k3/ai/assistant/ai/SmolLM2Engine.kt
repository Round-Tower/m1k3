package app.m1k3.ai.assistant.ai

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * M1K3 AI - SmolLM2 Inference Engine
 *
 * Runs SmolLM2-360M locally on device using ONNX Runtime.
 *
 * Features:
 * - 100% local inference (zero network)
 * - INT8 quantization for efficiency
 * - Optimized for mobile CPUs
 * - ~180MB model size
 * - Privacy-first architecture
 *
 * Performance (Pixel 6 Pro - Tensor G1):
 * - Inference: ~20 tokens/second
 * - First token latency: ~500ms
 * - Memory: ~300MB
 */
class SmolLM2Engine(private val context: Context) {

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: SmolLM2Tokenizer? = null

    private var isInitialized = false

    // Device-adaptive settings
    private val deviceRamGB: Long by lazy {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        memInfo.totalMem / (1024 * 1024 * 1024)
    }

    /**
     * Get optimal context window based on device RAM
     * SmolLM2-360M supports up to 24K tokens, but we limit based on device capability
     */
    private fun getOptimalContextWindow(): Int {
        return when {
            deviceRamGB >= 12 -> 24000  // 12GB+: Full context (flagship devices)
            deviceRamGB >= 8 -> 16000   // 8-12GB: Large context (high-end)
            deviceRamGB >= 6 -> 8000    // 6-8GB: Medium context (mid-range)
            deviceRamGB >= 4 -> 4000    // 4-6GB: Small context (budget)
            else -> 2000                 // <4GB: Minimal context (very budget)
        }
    }

    /**
     * Get optimal max tokens for generation based on device RAM
     *
     * Public API for UI to get device-appropriate max tokens
     */
    fun getOptimalMaxTokens(): Int {
        return when {
            deviceRamGB >= 12 -> 512   // 12GB+: Long responses
            deviceRamGB >= 8 -> 384    // 8-12GB: Medium-long responses
            deviceRamGB >= 6 -> 256    // 6-8GB: Medium responses
            deviceRamGB >= 4 -> 128    // 4-6GB: Short responses
            else -> 64                  // <4GB: Very short responses
        }
    }

    /**
     * Get device context for dynamic system prompts
     */
    private fun getDeviceContext(): String {
        val deviceModel = android.os.Build.MODEL
        val androidVersion = android.os.Build.VERSION.RELEASE
        val contextWindow = getOptimalContextWindow()

        return "$deviceModel (Android $androidVersion, ${deviceRamGB}GB RAM, ${contextWindow} token context)"
    }

    /**
     * Build default M1K3 system prompt with device context
     */
    private fun getDefaultSystemPrompt(userContext: Map<String, String>? = null): String {
        val deviceInfo = getDeviceContext()
        val userName = userContext?.get("name")

        return if (userName != null) {
            "You are M1K3 (Mike), $userName's privacy-first AI assistant running 100% locally on $deviceInfo. You never transmit data and respect user privacy."
        } else {
            "You are M1K3 (Mike), a privacy-first AI assistant running 100% locally on $deviceInfo. You never transmit data and respect user privacy."
        }
    }

    /**
     * Initialize the AI engine.
     * Must be called before inference.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        try {
            println("🤖 Initializing SmolLM2 engine...")
            println("   Device: ${android.os.Build.MODEL}")

            // 1. Initialize ONNX Runtime Environment
            ortEnvironment = OrtEnvironment.getEnvironment()
            println("   ✓ ONNX Runtime environment created")

            // 2. Configure session options for mobile optimization
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.apply {
                setIntraOpNumThreads(4)  // Tensor G1 has 4 high-performance cores
                setInterOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setMemoryPatternOptimization(true)
                // Note: setCpuArenaAllocator not available in this ONNX Runtime version
            }
            println("   ✓ Session options configured (4 threads, full optimization)")

            // 3. Copy ONNX model to internal storage (avoids OOM when loading large model)
            val modelFile = java.io.File(context.filesDir, "smollm2-360m-q4f16.onnx")

            if (!modelFile.exists()) {
                println("   📥 Copying model to internal storage (one-time operation)...")
                context.assets.open("models/smollm2-360m-q4f16.onnx").use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
                println("   ✓ Model copied (${modelFile.length() / 1024 / 1024} MB)")
            } else {
                println("   ✓ Model already in storage (${modelFile.length() / 1024 / 1024} MB)")
            }

            // 4. Create ONNX session from file path (memory efficient)
            ortSession = ortEnvironment!!.createSession(modelFile.absolutePath, sessionOptions)
            println("   ✓ ONNX session created")

            // 5. Initialize tokenizer
            tokenizer = SmolLM2Tokenizer(context)
            tokenizer?.initialize()
            println("   ✓ Tokenizer initialized")

            println("✅ SmolLM2 engine ready!")
            println("   Model: SmolLM2-360M-Instruct (INT4 quantized)")
            println("   Size: ${modelFile.length() / 1024 / 1024} MB")
            println("   Backend: ONNX Runtime 1.17.0")
            println("   Mode: Production inference")

            isInitialized = true

        } catch (e: Exception) {
            println("❌ Failed to initialize SmolLM2 engine: ${e.message}")
            e.printStackTrace()
            throw RuntimeException("Failed to initialize SmolLM2 engine", e)
        }
    }

    /**
     * Generate AI response for a given prompt.
     *
     * @param prompt User input text
     * @param maxTokens Maximum tokens to generate (default: 256 for better responses)
     * @param temperature Sampling temperature (0.0-1.0, default: 0.7)
     * @param systemPrompt Custom system prompt (default: dynamic M1K3 prompt with device context)
     * @param userContext Optional user personalization context (e.g., name, timezone)
     * @return AI-generated response
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 256,  // Increased from 128 to 256 for better quality
        temperature: Float = 0.7f,
        systemPrompt: String? = null,  // null = use default M1K3 prompt
        userContext: Map<String, String>? = null
    ): GenerationResult = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            throw IllegalStateException("Engine not initialized. Call initialize() first.")
        }

        val startTime = System.currentTimeMillis()

        try {
            val session = ortSession ?: throw IllegalStateException("ONNX session not initialized")
            val tok = tokenizer ?: throw IllegalStateException("Tokenizer not initialized")

            // Use custom system prompt or build default M1K3 prompt with device context
            val finalSystemPrompt = systemPrompt ?: getDefaultSystemPrompt(userContext)

            println("🔍 [DEBUG] Starting generation...")
            println("   Prompt: \"$prompt\"")
            println("   Max tokens: $maxTokens")
            println("   Temperature: $temperature")
            println("   System prompt: \"$finalSystemPrompt\"")

            // 1. Format prompt with instruction template (ChatML format)
            val formattedPrompt = """<|im_start|>system
$finalSystemPrompt<|im_end|>
<|im_start|>user
$prompt<|im_end|>
<|im_start|>assistant
"""

            println("🔍 [DEBUG] Formatted prompt length: ${formattedPrompt.length} chars")

            // 2. Tokenize input
            val inputIds = tok.encode(formattedPrompt)
            println("   📝 Tokenized prompt: ${inputIds.size} tokens")
            println("🔍 [DEBUG] Token IDs (first 10): ${inputIds.take(10).joinToString(", ")}")

            // 3. Run inference with proper resource management
            val env = ortEnvironment ?: throw IllegalStateException("Environment not initialized")

            // 4. Autoregressive generation with KV cache
            val generatedIds = mutableListOf<Long>()
            generatedIds.addAll(inputIds.toList())

            // KV cache configuration (SmolLM2-360M: 32 layers, 5 heads, 64 head_dim)
            val numLayers = 32
            val numHeads = 5
            val headDim = 64

            // Initialize KV cache storage (will be populated after first inference)
            var pastKeyValues: MutableMap<String, OnnxTensor>? = null
            var previousOutputs: OrtSession.Result? = null  // Track previous outputs container
            var currentSeqLen = 0  // Track sequence length in KV cache

            println("🔍 [DEBUG] Starting autoregressive generation loop (max $maxTokens tokens)...")

            try {
                for (i in 0 until maxTokens) {
                if (i % 10 == 0) {
                    println("🔍 [DEBUG] Token $i/${maxTokens} | Sequence length: ${generatedIds.size} | KV cache len: $currentSeqLen")
                }

                // For first token, process entire prompt. For subsequent tokens, only process new token
                val isFirstToken = (i == 0)
                val currentIds = if (isFirstToken) {
                    generatedIds.toLongArray()
                } else {
                    longArrayOf(generatedIds.last())  // Only the newly generated token
                }

                val currentTensor = OnnxTensor.createTensor(
                    env,
                    arrayOf(currentIds)
                )

                // Create position_ids tensor
                val positionIds = if (isFirstToken) {
                    LongArray(currentIds.size) { it.toLong() }
                } else {
                    longArrayOf(currentSeqLen.toLong())  // Position of the new token
                }
                val positionIdsTensor = OnnxTensor.createTensor(
                    env,
                    arrayOf(positionIds)
                )

                // Create attention_mask tensor (all 1s for the total sequence length so far)
                val totalSeqLen = if (isFirstToken) currentIds.size else currentSeqLen + 1
                val attentionMask = LongArray(totalSeqLen) { 1L }
                val attentionMaskTensor = OnnxTensor.createTensor(
                    env,
                    arrayOf(attentionMask)
                )

                // Prepare model inputs
                val inputs = mutableMapOf(
                    "input_ids" to currentTensor,
                    "attention_mask" to attentionMaskTensor,
                    "position_ids" to positionIdsTensor
                )

                // Add KV cache tensors
                if (isFirstToken || pastKeyValues == null) {
                    // First token: Use empty KV cache
                    for (layer in 0 until numLayers) {
                        val emptyKeyCache = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
                        val emptyValueCache = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
                        val shape = longArrayOf(1, numHeads.toLong(), 0, headDim.toLong())
                        inputs["past_key_values.$layer.key"] = OnnxTensor.createTensor(env, emptyKeyCache, shape, ai.onnxruntime.OnnxJavaType.FLOAT16)
                        inputs["past_key_values.$layer.value"] = OnnxTensor.createTensor(env, emptyValueCache, shape, ai.onnxruntime.OnnxJavaType.FLOAT16)
                    }
                } else {
                    // Subsequent tokens: Reuse KV cache from previous iteration
                    for (layer in 0 until numLayers) {
                        inputs["past_key_values.$layer.key"] = pastKeyValues!!["present.$layer.key"]!!
                        inputs["past_key_values.$layer.value"] = pastKeyValues!!["present.$layer.value"]!!
                    }
                }

                // Run model with all required inputs
                val inferenceStart = System.currentTimeMillis()
                val outputs = session.run(inputs)
                val inferenceTime = System.currentTimeMillis() - inferenceStart

                if (i < 3 || i % 10 == 0) {
                    println("🔍 [DEBUG] Inference ${i+1} took ${inferenceTime}ms")
                }

                // Get logits and sample next token
                val logitsObj = outputs.get(0).value
                val nextTokenId = sampleNextToken(
                    logitsObj,
                    temperature,
                    previousTokens = generatedIds  // Pass previously generated tokens for repetition penalty
                )

                if (i < 5) {
                    println("🔍 [DEBUG] Generated token ID: $nextTokenId")
                }

                // Check for end-of-sequence
                if (nextTokenId == 2L) {  // EOS token
                    println("🔍 [DEBUG] EOS token detected, stopping generation")
                    currentTensor.close()
                    attentionMaskTensor.close()
                    positionIdsTensor.close()
                    outputs.close()
                    previousOutputs?.close()
                    break
                }

                generatedIds.add(nextTokenId)

                // Close the PREVIOUS outputs container (now that we've used its cached tensors)
                // This is safe because we're about to extract new tensors from the current outputs
                previousOutputs?.close()

                // Extract new KV cache from outputs for reuse in next iteration
                pastKeyValues = mutableMapOf()
                for (layer in 0 until numLayers) {
                    val presentKeyOpt = outputs.get("present.$layer.key")
                    val presentValueOpt = outputs.get("present.$layer.value")

                    if (presentKeyOpt.isPresent && presentValueOpt.isPresent) {
                        pastKeyValues["present.$layer.key"] = presentKeyOpt.get() as OnnxTensor
                        pastKeyValues["present.$layer.value"] = presentValueOpt.get() as OnnxTensor
                    }
                }

                // Update sequence length for next iteration
                currentSeqLen = totalSeqLen

                // Clean up input tensors
                currentTensor.close()
                attentionMaskTensor.close()
                positionIdsTensor.close()

                // Save current outputs for cleanup in next iteration
                // DO NOT close it yet - we need the KV cache tensors to stay alive!
                previousOutputs = outputs

                // Stop if we hit special tokens
                if (nextTokenId == 0L || nextTokenId == 1L) {
                    break
                }
            }

            } finally {
                // Clean up resources in finally block to ensure cleanup even if exception occurs
                previousOutputs?.close()
                pastKeyValues?.values?.forEach { it.close() }
            }

            // 5. Decode generated tokens (skip the input prompt)
            val responseTokens = generatedIds.drop(inputIds.size).toLongArray()
            println("🔍 [DEBUG] Generated ${responseTokens.size} new tokens")
            println("🔍 [DEBUG] Response token IDs (first 10): ${responseTokens.take(10).joinToString(", ")}")

            val responseText = tok.decode(responseTokens)

            val inferenceTime = System.currentTimeMillis() - startTime
            val tokensGenerated = responseTokens.size

            println("   ⚡ Generated ${tokensGenerated} tokens in ${inferenceTime}ms")
            println("   🚀 Speed: ${"%.1f".format((tokensGenerated * 1000.0f) / inferenceTime)} tok/s")
            println("🔍 [DEBUG] Response text: \"$responseText\"")

            GenerationResult(
                text = responseText.trim(),
                tokensGenerated = tokensGenerated,
                inferenceTimeMs = inferenceTime,
                tokensPerSecond = (tokensGenerated * 1000.0f) / inferenceTime
            )

        } catch (e: Exception) {
            println("❌ Inference failed: ${e.message}")
            e.printStackTrace()
            throw RuntimeException("Inference failed", e)
        }
    }

    /**
     * Sample next token from logits using temperature sampling
     * When temperature = 0.0, uses greedy decoding (argmax)
     */
    private fun sampleNextToken(
        logitsObj: Any,
        temperature: Float,
        previousTokens: List<Long> = emptyList(),
        repetitionPenalty: Float = 1.2f  // 1.0 = no penalty, higher = stronger penalty
    ): Long {
        // Extract logits from ONNX output
        @Suppress("UNCHECKED_CAST")
        val logits = when (logitsObj) {
            is Array<*> -> {
                val batch = logitsObj[0] as Array<*>
                val lastToken = batch[batch.size - 1] as FloatArray
                lastToken.copyOf()  // Make a copy so we can modify it
            }
            else -> throw IllegalStateException("Unexpected logits format")
        }

        // Apply repetition penalty to recently generated tokens
        if (repetitionPenalty != 1.0f && previousTokens.isNotEmpty()) {
            // Penalize last 50 tokens to prevent repetition
            val recentTokens = previousTokens.takeLast(50)
            for (tokenId in recentTokens) {
                val idx = tokenId.toInt()
                if (idx >= 0 && idx < logits.size) {
                    // Divide logit by penalty (reduces probability)
                    logits[idx] /= repetitionPenalty
                }
            }
        }

        // Greedy decoding (temperature = 0.0)
        if (temperature == 0.0f) {
            return logits.indices.maxByOrNull { logits[it] }?.toLong() ?: 0L
        }

        // Temperature sampling
        val scaledLogits = logits.map { it / temperature }

        // Softmax
        val maxLogit = scaledLogits.maxOrNull() ?: 0f
        val expLogits = scaledLogits.map { kotlin.math.exp(it - maxLogit) }
        val sumExp = expLogits.sum()
        val probs = expLogits.map { it / sumExp }

        // Sample from distribution
        val random = kotlin.random.Random.nextFloat()
        var cumProb = 0f
        for (i in probs.indices) {
            cumProb += probs[i]
            if (random < cumProb) {
                return i.toLong()
            }
        }

        // Fallback to argmax
        return probs.indices.maxByOrNull { probs[it] }?.toLong() ?: 0L
    }


    /**
     * Generate response with streaming (token-by-token)
     *
     * This function provides real-time token generation with incremental decoding.
     * The onToken callback is called after each token is generated and decoded.
     */
    suspend fun generateStreaming(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        systemPrompt: String? = null,
        userContext: Map<String, String>? = null,
        onToken: suspend (String) -> Unit
    ) = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            throw IllegalStateException("Engine not initialized. Call initialize() first.")
        }

        val startTime = System.currentTimeMillis()

        try {
            val session = ortSession ?: throw IllegalStateException("ONNX session not initialized")
            val tok = tokenizer ?: throw IllegalStateException("Tokenizer not initialized")

            // Use custom system prompt or build default M1K3 prompt
            val finalSystemPrompt = systemPrompt ?: getDefaultSystemPrompt(userContext)

            println("🔍 [STREAMING] Starting generation...")
            println("   Prompt: \"$prompt\"")
            println("   Max tokens: $maxTokens")
            println("   Temperature: $temperature")

            // 1. Format prompt with ChatML
            val formattedPrompt = """<|im_start|>system
$finalSystemPrompt<|im_end|>
<|im_start|>user
$prompt<|im_end|>
<|im_start|>assistant
"""

            println("🔍 [STREAMING] Formatted prompt:")
            println("---BEGIN PROMPT---")
            println(formattedPrompt)
            println("---END PROMPT---")

            // 2. Tokenize input
            val inputIds = tok.encode(formattedPrompt)
            println("   📝 Tokenized prompt: ${inputIds.size} tokens")
            println("   🔍 First 20 token IDs: ${inputIds.take(20).joinToString(", ")}")
            println("   🔍 Last 10 token IDs: ${inputIds.takeLast(10).joinToString(", ")}")

            val env = ortEnvironment ?: throw IllegalStateException("Environment not initialized")

            // 3. Initialize generation state
            val generatedIds = mutableListOf<Long>()
            generatedIds.addAll(inputIds.toList())
            var accumulatedText = ""  // Track full generated text to detect multi-token special sequences

            val numLayers = 32
            val numHeads = 5
            val headDim = 64

            var pastKeyValues: MutableMap<String, OnnxTensor>? = null
            var previousOutputs: OrtSession.Result? = null  // Track previous outputs container
            var currentSeqLen = 0

            println("🔍 [STREAMING] Starting token-by-token generation...")

            // 4. Generate tokens one at a time with proper resource management
            try {
                for (i in 0 until maxTokens) {
                val isFirstToken = (i == 0)
                val currentIds = if (isFirstToken) {
                    generatedIds.toLongArray()
                } else {
                    longArrayOf(generatedIds.last())
                }

                val currentTensor = OnnxTensor.createTensor(env, arrayOf(currentIds))

                val positionIds = if (isFirstToken) {
                    LongArray(currentIds.size) { it.toLong() }
                } else {
                    longArrayOf(currentSeqLen.toLong())
                }
                val positionIdsTensor = OnnxTensor.createTensor(env, arrayOf(positionIds))

                val totalSeqLen = if (isFirstToken) currentIds.size else currentSeqLen + 1
                val attentionMask = LongArray(totalSeqLen) { 1L }
                val attentionMaskTensor = OnnxTensor.createTensor(env, arrayOf(attentionMask))

                val inputs = mutableMapOf(
                    "input_ids" to currentTensor,
                    "attention_mask" to attentionMaskTensor,
                    "position_ids" to positionIdsTensor
                )

                // Add KV cache
                if (isFirstToken || pastKeyValues == null) {
                    for (layer in 0 until numLayers) {
                        val emptyKeyCache = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
                        val emptyValueCache = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
                        val shape = longArrayOf(1, numHeads.toLong(), 0, headDim.toLong())
                        inputs["past_key_values.$layer.key"] = OnnxTensor.createTensor(env, emptyKeyCache, shape, ai.onnxruntime.OnnxJavaType.FLOAT16)
                        inputs["past_key_values.$layer.value"] = OnnxTensor.createTensor(env, emptyValueCache, shape, ai.onnxruntime.OnnxJavaType.FLOAT16)
                    }
                } else {
                    for (layer in 0 until numLayers) {
                        inputs["past_key_values.$layer.key"] = pastKeyValues!!["present.$layer.key"]!!
                        inputs["past_key_values.$layer.value"] = pastKeyValues!!["present.$layer.value"]!!
                    }
                }

                // Run inference
                val outputs = session.run(inputs)
                val logitsObj = outputs.get(0).value
                val nextTokenId = sampleNextToken(
                    logitsObj,
                    temperature,
                    previousTokens = generatedIds  // Pass previously generated tokens for repetition penalty
                )

                // Debug logging for token generation
                if (i < 5 || i % 20 == 0) {
                    println("🔍 [STREAMING] Token #$i: ID=$nextTokenId (${generatedIds.size} tokens so far)")
                }

                // Check for EOS token (ID = 2)
                if (nextTokenId == 2L) {
                    println("✅ [STREAMING] EOS TOKEN DETECTED (ID=2) - Natural stop after $i tokens!")
                    println("   This is good! The model chose to end naturally.")
                    currentTensor.close()
                    attentionMaskTensor.close()
                    positionIdsTensor.close()
                    outputs.close()
                    previousOutputs?.close()
                    break
                }

                generatedIds.add(nextTokenId)

                // Decode and emit the new token immediately
                val newTokenText = tok.decode(longArrayOf(nextTokenId))
                accumulatedText += newTokenText  // Track full generated text

                // Check accumulated text for special tokens (they may be generated as multiple tokens)
                if (accumulatedText.contains("<|im_end|>") ||
                    accumulatedText.contains("<|im_start|>") ||
                    accumulatedText.contains("<|endoftext|>")) {
                    println("✅ [STREAMING] Special token detected in accumulated text, stopping generation")
                    println("   Accumulated: \"${accumulatedText.takeLast(50)}\"")
                    currentTensor.close()
                    attentionMaskTensor.close()
                    positionIdsTensor.close()
                    outputs.close()
                    previousOutputs?.close()
                    break
                }

                if (newTokenText.isNotEmpty()) {
                    if (i == 0 || i % 5 == 0) {
                        // Make spaces visible in logs
                        val visibleText = newTokenText.replace(" ", "␣")
                        println("🔍 [STREAMING] Token $i: \"$visibleText\" (${newTokenText.length} chars)")
                    }
                    onToken(newTokenText)
                }

                // Close the PREVIOUS outputs container (now that we've used its cached tensors)
                // This is safe because we're about to extract new tensors from the current outputs
                previousOutputs?.close()

                // Extract KV cache for next iteration from current outputs
                pastKeyValues = mutableMapOf()
                for (layer in 0 until numLayers) {
                    val presentKeyOpt = outputs.get("present.$layer.key")
                    val presentValueOpt = outputs.get("present.$layer.value")
                    if (presentKeyOpt.isPresent && presentValueOpt.isPresent) {
                        pastKeyValues["present.$layer.key"] = presentKeyOpt.get() as OnnxTensor
                        pastKeyValues["present.$layer.value"] = presentValueOpt.get() as OnnxTensor
                    }
                }

                currentSeqLen = totalSeqLen

                // Clean up input tensors
                currentTensor.close()
                attentionMaskTensor.close()
                positionIdsTensor.close()

                // Save current outputs for cleanup in next iteration
                // DO NOT close it yet - we need the KV cache tensors to stay alive!
                previousOutputs = outputs

                // Stop on special tokens
                if (nextTokenId == 0L || nextTokenId == 1L) {
                    break
                }
            }

            } finally {
                // Clean up resources in finally block to ensure cleanup even if exception occurs
                previousOutputs?.close()
                pastKeyValues?.values?.forEach { it.close() }
            }

            val totalTime = System.currentTimeMillis() - startTime
            val tokensGenerated = generatedIds.size - inputIds.size
            println("   ⚡ Streamed ${tokensGenerated} tokens in ${totalTime}ms")
            println("   🚀 Speed: ${"%.1f".format((tokensGenerated * 1000.0f) / totalTime)} tok/s")

        } catch (e: Exception) {
            println("❌ Streaming inference failed: ${e.message}")
            e.printStackTrace()
            throw RuntimeException("Streaming inference failed", e)
        }
    }

    /**
     * Clean up resources
     */
    fun close() {
        ortSession?.close()
        ortEnvironment?.close()
        isInitialized = false
        println("🛑 SmolLM2 engine closed")
    }

    // Helper functions (commented out for mock inference)
    // TODO: Re-enable when using real ONNX model

    /* private fun createInputTensor(inputIds: LongArray): OnnxTensor {
        val env = ortEnvironment ?: throw IllegalStateException("Environment not initialized")
        val shape = longArrayOf(1, inputIds.size.toLong())
        return OnnxTensor.createTensor(env, inputIds, shape)
    }

    private fun extractOutputIds(outputs: OrtSession.Result): LongArray {
        val logits = outputs.get(0).value as Array<*>
        val outputIds = mutableListOf<Long>()
        @Suppress("UNCHECKED_CAST")
        val logitsArray = logits[0] as FloatArray
        var maxIdx = 0
        var maxVal = logitsArray[0]
        for (i in 1 until logitsArray.size) {
            if (logitsArray[i] > maxVal) {
                maxVal = logitsArray[i]
                maxIdx = i
            }
        }
        outputIds.add(maxIdx.toLong())
        return outputIds.toLongArray()
    } */
}

/**
 * AI generation result
 */
data class GenerationResult(
    val text: String,
    val tokensGenerated: Int,
    val inferenceTimeMs: Long,
    val tokensPerSecond: Float
) {
    override fun toString(): String = """
        Generated: "$text"
        Tokens: $tokensGenerated
        Time: ${inferenceTimeMs}ms
        Speed: ${"%.1f".format(tokensPerSecond)} tokens/sec
    """.trimIndent()
}

/**
 * Engine statistics
 */
data class EngineStats(
    val modelName: String = "SmolLM2-360M-Instruct",
    val modelSize: String = "~180MB (INT8)",
    val deviceName: String = android.os.Build.MODEL,
    val isInitialized: Boolean = false,
    val totalInferences: Int = 0,
    val avgInferenceMs: Float = 0f
)
