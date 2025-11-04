package app.m1k3.ai.assistant.ai

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Gemma 3:270m Inference Engine - PHASE1.5-002 Part 2
 *
 * Runs Gemma 3:270m locally on device using ONNX Runtime.
 * Model comparison candidate against SmolLM2-360M.
 *
 * **Architecture:**
 * - Parameters: 270M (vs SmolLM2's 360M)
 * - Quantization: INT4 (4-bit)
 * - Expected size: ~140MB (vs SmolLM2's 180MB)
 * - Context window: 8K tokens (vs SmolLM2's 24K)
 * - Vocabulary: 256K tokens (vs SmolLM2's 49K)
 *
 * **Comparison Metrics:**
 * | Metric | SmolLM2-360M | Gemma 3:270m |
 * |--------|--------------|--------------|
 * | Size | 360M params | 270M params |
 * | Model Size | 180MB | ~140MB |
 * | Context | 24K tokens | 8K tokens |
 * | Vocab | 49K | 256K |
 * | Tokenizer | GPT-2 BPE | SentencePiece |
 *
 * **Performance (Expected):**
 * - Inference: 25-30 tokens/second (smaller model = faster)
 * - First token latency: ~400ms (vs SmolLM2's 500ms)
 * - Memory: ~250MB (vs SmolLM2's 300MB)
 *
 * **Quality Hypothesis:**
 * - Smaller model but larger vocab = better multilingual support
 * - Google's training data may be higher quality
 * - Gemma series optimized for instruction-following
 *
 * Usage:
 * ```kotlin
 * val engine = Gemma3Engine(context)
 * engine.initialize()
 * val result = engine.generate("What is AI?", maxTokens = 256)
 * println(result.text)
 * ```
 */
class Gemma3Engine(private val context: Context) {

    companion object {
        private const val TAG = "Gemma3Engine"
        private const val MODEL_FILE = "gemma-3-270m-q4f16.onnx"
    }

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: Gemma3Tokenizer? = null

    private var isInitialized = false

    // Device-adaptive settings (same logic as SmolLM2)
    private val deviceRamGB: Long by lazy {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        memInfo.totalMem / (1024 * 1024 * 1024)
    }

    /**
     * Get optimal context window based on device RAM
     * Gemma 3:270m supports up to 8K tokens (vs SmolLM2's 24K)
     */
    private fun getOptimalContextWindow(): Int {
        return when {
            deviceRamGB >= 12 -> 8000    // 12GB+: Full context
            deviceRamGB >= 8 -> 6000     // 8-12GB: Large context
            deviceRamGB >= 6 -> 4000     // 6-8GB: Medium context
            deviceRamGB >= 4 -> 2000     // 4-6GB: Small context
            else -> 1000                  // <4GB: Minimal context
        }
    }

    /**
     * Get optimal max tokens for generation based on device RAM
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
     * Build default system prompt with device context
     */
    private fun getDefaultSystemPrompt(userContext: Map<String, String>? = null): String {
        val deviceInfo = getDeviceContext()
        val userName = userContext?.get("name")

        return if (userName != null) {
            "You are 間 AI (Ma AI), $userName's privacy-first AI assistant powered by Gemma 3:270m running 100% locally on $deviceInfo. You never transmit data and respect user privacy."
        } else {
            "You are 間 AI (Ma AI), a privacy-first AI assistant powered by Gemma 3:270m running 100% locally on $deviceInfo. You never transmit data and respect user privacy."
        }
    }

    /**
     * Initialize the AI engine.
     * Must be called before inference.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        try {
            Log.d(TAG, "🤖 Initializing Gemma 3:270m engine...")
            Log.d(TAG, "   Device: ${android.os.Build.MODEL}")
            Log.d(TAG, "   RAM: ${deviceRamGB}GB")

            // 1. Initialize ONNX Runtime Environment
            ortEnvironment = OrtEnvironment.getEnvironment()
            Log.d(TAG, "   ✓ ONNX Runtime environment created")

            // 2. Configure session options for mobile optimization
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.apply {
                setIntraOpNumThreads(4)  // Mobile CPUs typically have 4-8 performance cores
                setInterOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setMemoryPatternOptimization(true)
            }
            Log.d(TAG, "   ✓ Session options configured (4 threads, full optimization)")

            // 3. Copy ONNX model to internal storage (avoids OOM when loading large model)
            val modelFile = java.io.File(context.filesDir, MODEL_FILE)

            if (!modelFile.exists()) {
                Log.d(TAG, "   📥 Copying model to internal storage (one-time operation)...")
                try {
                    context.assets.open("models/$MODEL_FILE").use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output, bufferSize = 8192)
                        }
                    }
                    Log.d(TAG, "   ✓ Model copied (${modelFile.length() / 1024 / 1024} MB)")
                } catch (e: Exception) {
                    Log.e(TAG, "   ❌ Model file not found in assets/models/")
                    Log.e(TAG, "   NOTE: Run export_gemma3_270m.py to generate ONNX model")
                    throw IllegalStateException("Gemma 3:270m model not found. Run export script first.", e)
                }
            } else {
                Log.d(TAG, "   ✓ Model already in storage (${modelFile.length() / 1024 / 1024} MB)")
            }

            // 4. Create ONNX session from file path (memory efficient)
            ortSession = ortEnvironment!!.createSession(modelFile.absolutePath, sessionOptions)
            Log.d(TAG, "   ✓ ONNX session created")

            // 5. Initialize tokenizer
            tokenizer = Gemma3Tokenizer(context)
            tokenizer?.initialize()
            Log.d(TAG, "   ✓ Tokenizer initialized")

            Log.d(TAG, "✅ Gemma 3:270m engine ready!")
            Log.d(TAG, "   Model: Gemma 3:270m (INT4 quantized)")
            Log.d(TAG, "   Size: ${modelFile.length() / 1024 / 1024} MB")
            Log.d(TAG, "   Backend: ONNX Runtime 1.17.0")
            Log.d(TAG, "   Context window: ${getOptimalContextWindow()} tokens")

            isInitialized = true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize Gemma 3:270m engine: ${e.message}")
            e.printStackTrace()
            throw RuntimeException("Failed to initialize Gemma 3:270m engine", e)
        }
    }

    /**
     * Generate AI response for a given prompt.
     *
     * @param prompt User input text
     * @param maxTokens Maximum tokens to generate
     * @param temperature Sampling temperature (0.0-1.0)
     * @param systemPrompt Custom system prompt (default: dynamic prompt with device context)
     * @param userContext Optional user personalization context
     * @return AI-generated response
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        systemPrompt: String? = null,
        userContext: Map<String, String>? = null
    ): GenerationResult = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            throw IllegalStateException("Engine not initialized. Call initialize() first.")
        }

        val startTime = System.currentTimeMillis()

        try {
            val session = ortSession ?: throw IllegalStateException("ONNX session not initialized")
            val tok = tokenizer ?: throw IllegalStateException("Tokenizer not initialized")

            // Use custom system prompt or build default with device context
            val finalSystemPrompt = systemPrompt ?: getDefaultSystemPrompt(userContext)

            Log.d(TAG, "🔍 [DEBUG] Starting generation...")
            Log.d(TAG, "   Prompt: \"$prompt\"")
            Log.d(TAG, "   Max tokens: $maxTokens")
            Log.d(TAG, "   Temperature: $temperature")

            // 1. Format prompt with Gemma chat template
            val formattedPrompt = """<bos><start_of_turn>system
$finalSystemPrompt<end_of_turn>
<start_of_turn>user
$prompt<end_of_turn>
<start_of_turn>model
"""

            Log.d(TAG, "🔍 [DEBUG] Formatted prompt length: ${formattedPrompt.length} chars")

            // 2. Tokenize input
            val inputIds = tok.encode(formattedPrompt)
            Log.d(TAG, "   📝 Tokenized prompt: ${inputIds.size} tokens")
            Log.d(TAG, "🔍 [DEBUG] Token IDs (first 10): ${inputIds.take(10).joinToString(", ")}")

            // 3. Run inference
            // TODO: Implement autoregressive generation with KV cache (once model exported)
            // For now, return placeholder
            val placeholderResponse = "Gemma 3:270m model integration in progress. " +
                    "Run export_gemma3_270m.py to generate ONNX model and complete implementation."

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            Log.d(TAG, "⚠️ Placeholder response (model not yet exported)")
            Log.d(TAG, "   Time: ${duration}ms")

            GenerationResult(
                text = placeholderResponse,
                tokensGenerated = 0,
                inferenceTimeMs = duration,
                tokensPerSecond = 0.0f
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Generation failed: ${e.message}")
            e.printStackTrace()

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            GenerationResult(
                text = "Error: ${e.message}",
                tokensGenerated = 0,
                inferenceTimeMs = duration,
                tokensPerSecond = 0.0f
            )
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        ortSession?.close()
        ortEnvironment?.close()
        isInitialized = false
        Log.d(TAG, "🧹 Gemma 3:270m engine cleaned up")
    }

    /**
     * Get model info for comparison
     */
    fun getModelInfo(): ModelInfo {
        return ModelInfo(
            name = "Gemma 3:270m",
            parameters = "270M",
            sizeBytes = 140 * 1024 * 1024L,  // ~140MB INT4
            contextWindow = getOptimalContextWindow(),
            vocabSize = tokenizer?.vocabSize ?: 256000,
            quantization = "INT4",
            backend = "ONNX Runtime 1.17.0"
        )
    }
}

/**
 * Model information for comparison dashboard
 */
data class ModelInfo(
    val name: String,
    val parameters: String,
    val sizeBytes: Long,
    val contextWindow: Int,
    val vocabSize: Int,
    val quantization: String,
    val backend: String
)
