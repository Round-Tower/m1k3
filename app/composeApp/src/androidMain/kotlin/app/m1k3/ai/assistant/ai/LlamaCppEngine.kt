package app.m1k3.ai.assistant.ai

import android.content.Context
import app.m1k3.ai.assistant.ai.ma.MaBridge
import app.m1k3.ai.assistant.ai.ma.MaInferenceBackend
import app.m1k3.ai.assistant.utils.Logger
import app.m1k3.ai.domain.ai.GenerationConfig
import app.m1k3.ai.domain.ai.LlmModel
import app.m1k3.ai.domain.chat.format.MessageRole
import app.m1k3.ai.domain.chat.services.ChatFormatter
import app.m1k3.ai.domain.chat.services.DefaultChatFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private val logger = Logger.withTag("LlamaCppEngine")

/**
 * LlamaCppEngine - llama.cpp inference engine via Ma JNI bridge
 *
 * Replaces Llamatik with the Ma library, which:
 * - Wraps llama.cpp b8637+ directly (required for Gemma 4)
 * - Provides true token-level streaming (no UTF-8 JNI crash)
 * - Exposes a handle-based API (multiple contexts can coexist)
 *
 * @param context Android context for model path resolution
 * @param model The LLM model to use (default: Gemma 3 1B)
 * @param chatFormatter Prompt formatter derived from model's chat format
 * @param overrideModelPath Absolute path to a model file (skips asset/download resolution)
 * @param backend Inference backend (injectable for testing)
 * @param deviceRamGbOverride Override device RAM detection (-1 = auto-detect)
 */
class LlamaCppEngine(
    private val context: Context,
    private val model: LlmModel = LlmModel.default,
    private val chatFormatter: ChatFormatter = DefaultChatFormatter(model.chatFormat),
    private val overrideModelPath: String? = null,
    private val backend: MaInferenceBackend = MaBridge,
    private val deviceRamGbOverride: Int = -1
) : BaseLlmEngine {

    @Volatile
    private var isInitialized = false
    private val initMutex = Mutex()

    /** Opaque handle to the native llama_context (0 = not initialized). */
    @Volatile
    private var contextHandle: Long = 0L

    private val deviceRamGB: Int by lazy {
        if (deviceRamGbOverride >= 0) return@lazy deviceRamGbOverride
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        (memInfo.totalMem / (1024 * 1024 * 1024)).toInt()
    }

    private val modelFilename = model.filename
    private val defaultConfig = GenerationConfig()

    /**
     * Resolve the model file path:
     * 1. overrideModelPath (externally-provided)
     * 2. Downloaded model in filesDir/models/
     * 3. Copied from assets/models/ (bundled small models)
     *
     * @throws NoModelAvailableException when no model file can be found or copied
     */
    private fun resolveModelPath(): String {
        overrideModelPath?.let { path ->
            val file = File(path)
            if (file.exists()) return path
            logger.w { "Override path does not exist: $path" }
        }

        val downloadedFile = File(File(context.filesDir, "models"), modelFilename)
        if (downloadedFile.exists() && downloadedFile.length() > 0) {
            logger.d { "Found downloaded model (${downloadedFile.length() / 1024 / 1024} MB)" }
            return downloadedFile.absolutePath
        }

        // Attempt to copy from bundled assets (legacy; removed in no-bundle builds)
        val assetFile = File(context.filesDir, modelFilename)
        return try {
            if (!assetFile.exists()) {
                logger.i { "Copying $modelFilename from assets to internal storage" }
                context.assets.open("models/$modelFilename").use { input ->
                    assetFile.outputStream().use { output -> input.copyTo(output, bufferSize = 8192) }
                }
                logger.i { "Model copied (${assetFile.length() / 1024 / 1024} MB)" }
            }
            assetFile.absolutePath
        } catch (e: Exception) {
            // No assets fallback — model must be downloaded first
            throw NoModelAvailableException(
                model = model,
                cause = e
            )
        }
    }

    /**
     * Initialize the engine by loading the GGUF model via [MaBridge].
     */
    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext Result.success(Unit)

        initMutex.withLock {
            if (isInitialized) return@withLock Result.success(Unit)

            try {
                logger.i { "Initializing -> model=${model.displayName}, RAM: ${deviceRamGB}GB" }

                val modelPath = resolveModelPath()
                logger.i { "Loading model: $modelPath" }

                val nCtx = if (deviceRamGB >= 8) 4096 else 2048
                val handle = backend.init(modelPath, nCtx)
                if (handle == 0L) {
                    return@withLock Result.failure(
                        RuntimeException("Failed to load model '${model.displayName}'. File may be corrupt or incompatible.")
                    )
                }

                contextHandle = handle
                isInitialized = true
                logger.i { "Initialization complete (handle=$handle)" }

                Result.success(Unit)
            } catch (e: NoModelAvailableException) {
                logger.w { "No model available: ${e.message}" }
                Result.failure(e)
            } catch (e: Exception) {
                logger.e(e) { "Initialization failed" }
                isInitialized = false
                contextHandle = 0L
                Result.failure(RuntimeException("Failed to initialize LlamaCppEngine", e))
            }
        }
    }

    /**
     * Generate a single response (non-streaming).
     */
    override suspend fun generate(
        prompt: String,
        config: GenerationConfig
    ): Result<GenerationResult> = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext Result.failure(
                IllegalStateException("Engine not initialized. Call initialize() first.")
            )
        }

        val startTime = System.currentTimeMillis()
        val maxTokens = config.maxTokens?.takeIf { it > 0 } ?: getOptimalMaxTokens()

        try {
            val resolvedPrompt = resolvePrompt(prompt, config)

            logger.d { "generate -> ${resolvedPrompt.length}c maxTokens=$maxTokens" }

            val rawResponse = backend.generate(
                handle = contextHandle,
                prompt = resolvedPrompt,
                maxTokens = maxTokens,
                temperature = config.temperature ?: 0.7f,
                topP = config.topP ?: 0.95f,
                topK = config.topK ?: 64,
                repeatPenalty = config.repetitionPenalty ?: 1.1f
            )

            val response = stripStopTokens(rawResponse)

            if (response.isEmpty()) {
                logger.w { "Backend returned empty response" }
                return@withContext Result.failure(
                    RuntimeException("Model returned empty response. The native context may have been lost.")
                )
            }

            val inferenceTimeMs = System.currentTimeMillis() - startTime
            val estimatedTokens = (response.length / 4).coerceAtLeast(1)
            val tokensPerSecond = if (inferenceTimeMs > 0) {
                (estimatedTokens * 1000f) / inferenceTimeMs
            } else 0f

            logger.i {
                "Generation complete (${response.length} chars, ~$estimatedTokens tokens, " +
                        "${inferenceTimeMs}ms, ${"%.1f".format(tokensPerSecond)} tok/s)"
            }

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
            Result.failure(RuntimeException("Generation failed", e))
        }
    }

    /**
     * Generate response with true token streaming via Ma JNI callbacks.
     *
     * Each token piece is emitted via [onToken] as it is generated — no
     * word-split simulation. The UTF-8 issue from Llamatik is resolved
     * because [MaBridge] calls NewStringUTF only on complete token pieces.
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

        val maxTokens = config.maxTokens?.takeIf { it > 0 } ?: getOptimalMaxTokens()

        try {
            val resolvedPrompt = resolvePrompt(prompt, config)

            logger.d { "generateStreaming -> ${resolvedPrompt.length}c maxTokens=$maxTokens" }

            val rawResponse = backend.generate(
                handle = contextHandle,
                prompt = resolvedPrompt,
                maxTokens = maxTokens,
                temperature = config.temperature ?: 0.7f,
                topP = config.topP ?: 0.95f,
                topK = config.topK ?: 64,
                repeatPenalty = config.repetitionPenalty ?: 1.1f,
                onToken = onToken  // true streaming: called per token from C++
            )

            val response = stripStopTokens(rawResponse)

            if (response.isEmpty()) {
                logger.w { "Backend returned empty response during streaming" }
                return@withContext Result.failure(
                    RuntimeException("Model returned empty response. The native context may have been lost.")
                )
            }

            logger.i { "Streaming complete (${response.length} chars)" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.e(e) { "Streaming failed" }
            Result.failure(RuntimeException("Streaming failed", e))
        }
    }

    override fun getOptimalMaxTokens(): Int = when {
        deviceRamGB >= 12 -> 4096
        deviceRamGB >= 8 -> 3072
        deviceRamGB >= 6 -> 2048
        deviceRamGB >= 4 -> 1536
        else -> 1024
    }

    override fun release() {
        val handle = contextHandle
        if (handle != 0L) {
            try {
                backend.release(handle)
            } catch (e: Exception) {
                logger.e(e) { "Release failed" }
            }
        }
        contextHandle = 0L
        isInitialized = false
        logger.i { "Released" }
    }

    // ==================== Prompt Construction ====================

    /**
     * Resolve the final prompt to send to the backend.
     *
     * If the prompt is already formatted with chat template tokens
     * (e.g., from UnifiedPromptBuilder), pass it through. Otherwise,
     * build a formatted prompt using the model's [ChatFormat].
     */
    private fun resolvePrompt(prompt: String, config: GenerationConfig): String =
        if (isAlreadyFormatted(prompt)) {
            prompt
        } else {
            buildFormattedPrompt(config, prompt)
        }

    /**
     * Build a fully-formatted prompt for an unformatted user input.
     *
     * Assembles: BOS prefix + system (if supported) + user turn + model prefix.
     */
    private fun buildFormattedPrompt(config: GenerationConfig, userMessage: String): String {
        val format = model.chatFormat
        val systemPrompt = buildCleanSystemPrompt(config)
        val contextBlock = buildContextString(config)

        return buildString {
            append(format.getPromptPrefix())

            if (format.supportsSystemRole && systemPrompt.isNotEmpty()) {
                append(format.formatMessage(MessageRole.SYSTEM, systemPrompt))
                val userContent = if (contextBlock.isNotEmpty()) {
                    "$contextBlock\n\n$userMessage"
                } else userMessage
                append(format.formatMessage(MessageRole.USER, userContent))
            } else {
                // No system role (Gemma3): prepend system + context into user turn
                val userContent = buildString {
                    if (systemPrompt.isNotEmpty()) append("$systemPrompt\n\n")
                    if (contextBlock.isNotEmpty()) append("$contextBlock\n\n")
                    append(userMessage)
                }
                append(format.formatMessage(MessageRole.USER, userContent))
            }

            // Open the model/assistant turn for generation
            append(assistantPrefix(format))
        }
    }

    private fun buildCleanSystemPrompt(config: GenerationConfig): String {
        val deviceInfo = "${android.os.Build.MODEL} (${deviceRamGB}GB RAM)"
        val userName = config.userContext?.get("name") ?: "Human"
        val identity = "You are M1K3 (Mike), $userName's local AI assistant running on $deviceInfo."
        val temperature = config.temperature
        val behavior = when {
            temperature != null && temperature < 0.4f ->
                " Be concise, factual, and direct. Avoid speculation. No repetition."
            temperature != null && temperature > 0.8f ->
                " Be creative and imaginative. Vary your phrasing."
            else ->
                " Be helpful and accurate. Use only provided facts. If unsure, say so." +
                        " Avoid repetition. Each sentence must provide new information."
        }
        return config.systemPrompt ?: (identity + behavior)
    }

    private fun buildContextString(config: GenerationConfig): String {
        val parts = mutableListOf<String>()
        config.knowledgeContext?.let { parts.add(it) }
        return parts.joinToString("\n\n")
    }

    private fun isAlreadyFormatted(prompt: String): Boolean =
        prompt.contains("<start_of_turn>") ||
                prompt.contains("<end_of_turn>") ||
                prompt.contains("<|im_start|>") ||
                prompt.contains("[INST]") ||
                prompt.contains("<|start_header_id|>") ||
                prompt.startsWith("<bos>") ||
                prompt.startsWith("<|begin_of_text|>")

    private fun stripStopTokens(response: String?): String {
        val nonNull = response ?: return ""
        val stopTokens = chatFormatter.getStopTokens()
        var cleaned: String = nonNull
        for (stopToken in stopTokens) {
            val idx = cleaned.indexOf(stopToken)
            if (idx >= 0) cleaned = cleaned.substring(0, idx)
        }
        return cleaned.trim()
    }

    companion object {
        /** Format-specific prefix that opens the model's generation turn. */
        fun assistantPrefix(format: app.m1k3.ai.domain.chat.format.ChatFormat): String =
            when (format) {
                is app.m1k3.ai.domain.chat.format.ChatFormat.Gemma3 -> "<start_of_turn>model\n"
                is app.m1k3.ai.domain.chat.format.ChatFormat.Gemma4 -> "<start_of_turn>model\n"
                is app.m1k3.ai.domain.chat.format.ChatFormat.FalconH1 -> "<|start_header_id|>assistant<|end_header_id|>\n"
                is app.m1k3.ai.domain.chat.format.ChatFormat.ChatML -> "<|im_start|>assistant\n"
                is app.m1k3.ai.domain.chat.format.ChatFormat.Llama -> ""
                is app.m1k3.ai.domain.chat.format.ChatFormat.Simple -> ""
                else -> ""
            }
    }
}
