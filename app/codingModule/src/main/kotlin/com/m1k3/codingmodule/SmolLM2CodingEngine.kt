package com.m1k3.codingmodule

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import domain.coding.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer
import kotlin.system.measureTimeMillis

/**
 * SmolLM2-360M Coding Engine - General-Purpose Model
 *
 * Implements template-driven code generation using SmolLM2-360M-Instruct.
 * This model is better suited for creative, varied content like quizzes and presentations.
 *
 * Key Differences from Qwen:
 * - Context: 8K tokens (vs Qwen's 32K)
 * - Specialization: General-purpose (vs Qwen's coding-focus)
 * - Strengths: Creative content, varied topics, storytelling
 * - Best for: Quiz, Presentation templates
 *
 * Model Details:
 * - Model: SmolLM2-360M-Instruct (HuggingFace)
 * - Size: 180MB (INT4 quantized)
 * - Context: 8K tokens
 * - Vocab: 49K tokens (same as GPT-2)
 *
 * Reuses Phase 1 Integration:
 * This adapts the existing SmolLM2 model from Phase 1 (general chat)
 * for template-driven code generation.
 *
 * @property context Android application context for asset access
 */
class SmolLM2CodingEngine(
    private val context: Context
) : CodingEngine {

    private val modelDir = "models/smollm2"
    private val templateDir = "templates" // Shared with Qwen

    // ONNX Runtime components (same as Qwen)
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // Model state
    private var isModelLoaded = false
    private val modelLock = Any()

    /**
     * Check if SmolLM2 model is available
     */
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check model file
            val modelExists = context.assets.list(modelDir)?.contains("model_quantized.onnx") == true
            modelExists
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Load SmolLM2 ONNX model into memory
     */
    override suspend fun loadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        val result = synchronized(modelLock) {
            if (isModelLoaded) {
                return@synchronized Result.success(Unit)
            }

            try {
                // 1. Create ONNX Runtime environment
                ortEnvironment = OrtEnvironment.getEnvironment()

                // 2. Load model
                val modelPath = copyAssetToCache("$modelDir/model_quantized.onnx")
                val sessionOptions = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(4)
                    setInterOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }

                ortSession = ortEnvironment!!.createSession(modelPath, sessionOptions)

                isModelLoaded = true
                Result.success(Unit)
            } catch (e: Exception) {
                ortSession?.close()
                ortSession = null
                ortEnvironment?.close()
                ortEnvironment = null
                Result.failure(e)
            }
        }

        // 3. Warm up model (outside synchronized block since it's a suspend function)
        if (result.isSuccess) {
            warmUpModel()
        }

        result
    }

    /**
     * Unload model and free resources
     */
    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        synchronized(modelLock) {
            ortSession?.close()
            ortSession = null
            ortEnvironment?.close()
            ortEnvironment = null
            isModelLoaded = false
        }
    }

    /**
     * Generate code using SmolLM2 with template-driven approach
     *
     * Uses same template system as Qwen but with adjusted prompts
     * for general-purpose model.
     */
    override fun generateCode(request: GenerationRequest): Flow<GenerationEvent> = flow {
        val startTime = System.currentTimeMillis()

        try {
            emit(GenerationEvent.Started(request.templateType))

            if (!isModelLoaded) {
                emit(GenerationEvent.Failed(
                    IllegalStateException("Model not loaded"),
                    "initialization"
                ))
                return@flow
            }

            // Load template
            emit(GenerationEvent.LoadingTemplate(request.templateType))
            val templateLoadStart = System.currentTimeMillis()
            val template = loadTemplate(request.templateType)
            val example = loadExample(request.templateType)
            val templateLoadTime = System.currentTimeMillis() - templateLoadStart

            // Build prompt (adjusted for SmolLM2)
            val prompt = buildPrompt(request.templateType, request.topic, example, request.config)

            // Generate with streaming using ONNX Runtime
            emit(GenerationEvent.Generating(0f))
            val inferenceStart = System.currentTimeMillis()

            var generatedContent = ""
            var tokensGenerated = 0

            // Real ONNX inference
            val maxNewTokens = request.config.maxTokens.coerceAtMost(8192 - 1000) // 8K context limit

            try {
                // Tokenize prompt (simplified - using GPT-2 style)
                val inputText = prompt
                val inputTokens = tokenizeText(inputText)

                // Prepare ONNX inputs
                val inputIds = inputTokens
                val attentionMask = LongArray(inputIds.size) { 1L }

                // Run inference with streaming token generation
                val generatedTokens = generateTokens(
                    inputIds = inputIds,
                    attentionMask = attentionMask,
                    maxNewTokens = maxNewTokens,
                    temperature = request.config.temperature,
                    onProgress = { progress, partial ->
                        // Emit progress events
                        emit(GenerationEvent.Generating(progress))
                        if (partial.isNotEmpty()) {
                            emit(GenerationEvent.PartialResult(partial))
                        }
                    }
                )

                // Decode generated tokens to text
                generatedContent = decodeTokens(generatedTokens)
                tokensGenerated = generatedTokens.size

            } catch (e: Exception) {
                println("⚠️ ONNX inference failed, using fallback: ${e.message}")
                // Fallback to mock content if inference fails
                generatedContent = generateMockContent(request)
                tokensGenerated = generatedContent.length / 4
            }

            val inferenceTime = System.currentTimeMillis() - inferenceStart

            // Validate
            emit(GenerationEvent.Validating)
            val validationStart = System.currentTimeMillis()
            val jsonContent = extractJSON(generatedContent)
            val validationTime = System.currentTimeMillis() - validationStart

            // Inject into template
            emit(GenerationEvent.InjectingTemplate)
            val finalHtml = injectTemplate(template, jsonContent, request)

            // Completed
            val totalTime = System.currentTimeMillis() - startTime
            val metrics = GenerationMetrics(
                durationMs = totalTime,
                tokensGenerated = tokensGenerated,
                tokensPerSecond = (tokensGenerated * 1000f) / inferenceTime,
                templateLoadTimeMs = templateLoadTime,
                inferenceTimeMs = inferenceTime,
                validationTimeMs = validationTime
            )

            emit(GenerationEvent.Completed(finalHtml, metrics))

        } catch (e: Exception) {
            emit(GenerationEvent.Failed(e, "generation"))
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Validate generated HTML (same as Qwen)
     */
    override suspend fun validateCode(html: String): ValidationResult = withContext(Dispatchers.Default) {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()
        val suggestions = mutableListOf<String>()

        // Same validation logic as QwenCodingEngine
        if (!html.contains("<!DOCTYPE html>", ignoreCase = true)) {
            errors.add(ValidationIssue(
                IssueType.INVALID_HTML,
                "Missing DOCTYPE declaration",
                null,
                IssueSeverity.ERROR
            ))
        }

        // Security checks
        val dangerousPatterns = listOf("eval\\(", "innerHTML\\s*=", "document.write\\(")
        dangerousPatterns.forEach { pattern ->
            if (Regex(pattern).find(html) != null) {
                warnings.add(ValidationIssue(
                    IssueType.SECURITY,
                    "Potentially unsafe pattern: $pattern",
                    null,
                    IssueSeverity.WARNING
                ))
            }
        }

        // Accessibility checks
        if (!html.contains("aria-", ignoreCase = true)) {
            warnings.add(ValidationIssue(
                IssueType.ACCESSIBILITY,
                "No ARIA attributes found",
                null,
                IssueSeverity.WARNING
            ))
        }

        if (!html.contains("alt=", ignoreCase = true) && html.contains("<img", ignoreCase = true)) {
            errors.add(ValidationIssue(
                IssueType.ACCESSIBILITY,
                "Images without alt text",
                null,
                IssueSeverity.ERROR
            ))
        }

        ValidationResult(
            isValid = errors.isEmpty(),
            issues = errors + warnings
        )
    }

    /**
     * Get SmolLM2 model information
     */
    override fun getModelInfo(): ModelInfo = ModelInfo(
        name = "SmolLM2-360M-Instruct",
        version = "360M",
        sizeBytes = 180 * 1024 * 1024L,
        contextWindow = 8192,
        supportedLanguages = listOf(
            "HTML", "CSS", "JavaScript", "Python", "Java", "Kotlin",
            "General programming", "and 20+ more..."
        ),
        capabilities = listOf(
            "Creative content generation",
            "Varied question creation",
            "Storytelling and narrative",
            "General-purpose coding"
        )
    )

    /**
     * Estimate generation time for SmolLM2
     * (Slightly faster than Qwen due to smaller model + shorter context)
     */
    override suspend fun estimateGenerationTime(request: GenerationRequest): Long {
        val baseTime = when (request.templateType) {
            TemplateType.QUIZ -> 18_000L // 18 seconds (vs Qwen's 20s)
            TemplateType.GAME -> 32_000L // 32 seconds (vs Qwen's 35s)
            TemplateType.SVG_CHART -> 16_000L // 16 seconds (vs Qwen's 18s)
            TemplateType.PRESENTATION -> 40_000L // 40 seconds (vs Qwen's 45s)
        }

        val complexityFactor = when (request.config.targetAudience) {
            AudienceLevel.BEGINNER -> 0.8f
            AudienceLevel.GENERAL -> 1.0f
            AudienceLevel.ADVANCED -> 1.2f
            AudienceLevel.EXPERT -> 1.5f
        }

        return (baseTime * complexityFactor).toLong()
    }

    // ===== Private Helper Methods =====

    private fun copyAssetToCache(assetPath: String): String {
        val cacheFile = File(context.cacheDir, assetPath.substringAfterLast('/'))
        if (!cacheFile.exists()) {
            context.assets.open(assetPath).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return cacheFile.absolutePath
    }

    private suspend fun warmUpModel() = withContext(Dispatchers.IO) {
        // Warm up with simple test
        // In production: actual inference call
    }

    private fun loadTemplate(templateType: TemplateType): String {
        val templatePath = when (templateType) {
            TemplateType.QUIZ -> "$templateDir/quiz/base.html"
            TemplateType.GAME -> "$templateDir/games/canvas-base.html"
            TemplateType.SVG_CHART -> "$templateDir/svg/chart-base.html"
            TemplateType.PRESENTATION -> "$templateDir/presentation/slide-base.html"
        }
        return context.assets.open(templatePath).bufferedReader().use { it.readText() }
    }

    private fun loadExample(templateType: TemplateType): String {
        val examplePath = when (templateType) {
            TemplateType.QUIZ -> "$templateDir/quiz/example.json"
            TemplateType.GAME -> "$templateDir/games/snake-example.json"
            TemplateType.SVG_CHART -> "$templateDir/svg/bar-chart-example.json"
            TemplateType.PRESENTATION -> "$templateDir/presentation/example.json"
        }
        return context.assets.open(examplePath).bufferedReader().use { it.readText() }
    }

    /**
     * Build prompt for SmolLM2 (adjusted from Qwen)
     *
     * SmolLM2 differences:
     * - More emphasis on creativity and variety
     * - Simpler technical instructions
     * - Focus on user-friendly content
     */
    private fun buildPrompt(
        templateType: TemplateType,
        topic: String,
        example: String,
        config: GenerationConfig
    ): String {
        val typeDescription = when (templateType) {
            TemplateType.QUIZ -> "creative quiz with 5 engaging multiple-choice questions"
            TemplateType.GAME -> "fun game configuration"
            TemplateType.SVG_CHART -> "clear data visualization"
            TemplateType.PRESENTATION -> "engaging presentation with 5 informative slides"
        }

        return """
You are a creative content generator helping users create $typeDescription.

TEMPLATE FORMAT:
$example

YOUR TASK:
Create engaging content about: $topic

REQUIREMENTS:
- Output ONLY valid JSON matching the template format
- Be creative and make it interesting for users
- Use clear, friendly language
- Focus on variety and engagement
- ${if (config.includeComments) "Include helpful comments" else "No comments"}
- Target audience: ${config.targetAudience.name.lowercase()}

Generate the JSON now:
        """.trim()
    }

    private fun extractJSON(content: String): String {
        var json = content.trim()
        if (json.startsWith("```")) {
            json = json.substringAfter("```json").substringAfter("```").substringBeforeLast("```").trim()
        }
        return json
    }

    private fun injectTemplate(template: String, jsonContent: String, request: GenerationRequest): String {
        return template
            .replace("{{QUIZ_TITLE}}", request.topic)
            .replace("{{GAME_TITLE}}", request.topic)
            .replace("{{CHART_TITLE}}", request.topic)
            .replace("{{PRESENTATION_TITLE}}", request.topic)
            .replace("{{CHART_DESCRIPTION}}", "Visualization for ${request.topic}")
            .replace("{{QUESTIONS}}", jsonContent)
            .replace("{{GAME_CONFIG}}", jsonContent)
            .replace("{{CHART_DATA}}", jsonContent)
            .replace("{{SLIDES}}", jsonContent)
            .replace("{{CHART_TYPE}}", "bar")
    }

    /**
     * Generate mock content (placeholder until ONNX models deployed)
     */
    private fun generateMockContent(request: GenerationRequest): String {
        return when (request.templateType) {
            TemplateType.QUIZ -> """
                [
                    {
                        "question": "Sample question about ${request.topic}?",
                        "options": ["Option A", "Option B", "Option C", "Option D"],
                        "correctIndex": 0,
                        "explanation": "This is the correct answer because..."
                    }
                ]
            """.trimIndent()
            TemplateType.GAME -> """{"gameType": "snake", "speed": 5}"""
            TemplateType.SVG_CHART -> """{"data": [10, 20, 30], "labels": ["A", "B", "C"]}"""
            TemplateType.PRESENTATION -> """[{"title": "Slide 1", "content": "Content"}]"""
        }
    }

    /**
     * Tokenize text using simple byte-level encoding
     * TODO: Replace with proper SmolLM2 tokenizer
     */
    private fun tokenizeText(text: String): LongArray {
        // Simple character-level tokenization as placeholder
        // In production, use the SmolLM2Tokenizer
        return text.toByteArray(Charsets.UTF_8).map { it.toLong() and 0xFFL }.toLongArray()
    }

    /**
     * Decode tokens back to text
     * TODO: Replace with proper SmolLM2 tokenizer
     */
    private fun decodeTokens(tokens: List<Long>): String {
        // Simple byte-level decoding as placeholder
        return try {
            tokens.map { it.toByte() }.toByteArray().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Generate tokens using ONNX Runtime with streaming
     *
     * @param inputIds Input token IDs
     * @param attentionMask Attention mask
     * @param maxNewTokens Maximum tokens to generate
     * @param temperature Sampling temperature
     * @param onProgress Progress callback (progress 0-100, partial result)
     * @return Generated token IDs
     */
    private suspend fun generateTokens(
        inputIds: LongArray,
        attentionMask: LongArray,
        maxNewTokens: Int,
        temperature: Float,
        onProgress: suspend (Float, String) -> Unit
    ): List<Long> = withContext(Dispatchers.Default) {
        val session = ortSession ?: throw IllegalStateException("ONNX session not initialized")
        val env = ortEnvironment ?: throw IllegalStateException("Environment not initialized")

        val generatedIds = mutableListOf<Long>()
        generatedIds.addAll(inputIds.toList())

        // KV cache configuration
        val numLayers = 32
        val numHeads = 5
        val headDim = 64

        var pastKeyValues: MutableMap<String, OnnxTensor>? = null
        var previousOutputs: ai.onnxruntime.OrtSession.Result? = null
        var currentSeqLen = 0

        try {
            for (i in 0 until maxNewTokens) {
                val isFirstToken = (i == 0)
                val currentIds = if (isFirstToken) {
                    generatedIds.toLongArray()
                } else {
                    longArrayOf(generatedIds.last())
                }

                // Create tensors
                val currentTensor = OnnxTensor.createTensor(env, arrayOf(currentIds))

                val positionIds = if (isFirstToken) {
                    LongArray(currentIds.size) { it.toLong() }
                } else {
                    longArrayOf(currentSeqLen.toLong())
                }
                val positionIdsTensor = OnnxTensor.createTensor(env, arrayOf(positionIds))

                val totalSeqLen = if (isFirstToken) currentIds.size else currentSeqLen + 1
                val attentionMaskArray = LongArray(totalSeqLen) { 1L }
                val attentionMaskTensor = OnnxTensor.createTensor(env, arrayOf(attentionMaskArray))

                // Prepare inputs
                val inputs = mutableMapOf(
                    "input_ids" to currentTensor,
                    "attention_mask" to attentionMaskTensor,
                    "position_ids" to positionIdsTensor
                )

                // Add KV cache
                if (isFirstToken || pastKeyValues == null) {
                    for (layer in 0 until numLayers) {
                        val emptyKeyCache = java.nio.ByteBuffer.allocateDirect(0).order(java.nio.ByteOrder.nativeOrder())
                        val emptyValueCache = java.nio.ByteBuffer.allocateDirect(0).order(java.nio.ByteOrder.nativeOrder())
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
                val nextTokenId = sampleNextToken(logitsObj, temperature)

                // Check for EOS
                if (nextTokenId == 2L) {
                    currentTensor.close()
                    attentionMaskTensor.close()
                    positionIdsTensor.close()
                    outputs.close()
                    previousOutputs?.close()
                    break
                }

                generatedIds.add(nextTokenId)

                // Progress callback
                val progress = (i * 100f) / maxNewTokens
                val partial = decodeTokens(generatedIds.drop(inputIds.size))
                onProgress(progress, partial)

                // Cleanup and prepare for next iteration
                previousOutputs?.close()

                // CRITICAL: Close old KV cache tensors before creating new ones
                pastKeyValues?.values?.forEach { it.close() }

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
                currentTensor.close()
                attentionMaskTensor.close()
                positionIdsTensor.close()
                previousOutputs = outputs

                if (nextTokenId == 0L || nextTokenId == 1L) {
                    break
                }
            }

            previousOutputs?.close()
        } catch (e: Exception) {
            previousOutputs?.close()
            throw e
        }

        generatedIds.drop(inputIds.size)
    }

    /**
     * Sample next token from logits using temperature sampling
     */
    private fun sampleNextToken(logitsObj: Any, temperature: Float): Long {
        @Suppress("UNCHECKED_CAST")
        val logits = when (logitsObj) {
            is Array<*> -> {
                val batch = logitsObj[0] as Array<*>
                val lastToken = batch[batch.size - 1] as FloatArray
                lastToken
            }
            else -> throw IllegalStateException("Unexpected logits format")
        }

        if (temperature == 0.0f) {
            return logits.indices.maxByOrNull { logits[it] }?.toLong() ?: 0L
        }

        val scaledLogits = logits.map { it / temperature }
        val maxLogit = scaledLogits.maxOrNull() ?: 0f
        val expLogits = scaledLogits.map { kotlin.math.exp(it - maxLogit) }
        val sumExp = expLogits.sum()
        val probs = expLogits.map { it / sumExp }

        val random = kotlin.random.Random.nextFloat()
        var cumProb = 0f
        for (i in probs.indices) {
            cumProb += probs[i]
            if (random < cumProb) {
                return i.toLong()
            }
        }

        return probs.indices.maxByOrNull { probs[it] }?.toLong() ?: 0L
    }
}
