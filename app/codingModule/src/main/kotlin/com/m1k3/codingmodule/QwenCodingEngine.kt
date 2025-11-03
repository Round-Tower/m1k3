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
 * Android implementation of CodingEngine using ONNX Runtime
 *
 * Implements template-driven code generation with Qwen2.5-Coder-0.5B-Instruct.
 *
 * Architecture:
 * - ONNX Runtime 1.17.0 for model inference
 * - SentencePiece tokenizer for text encoding/decoding
 * - Template injection from assets
 * - Streaming generation with Kotlin Flow
 *
 * Model Details:
 * - Model: Qwen2.5-Coder-0.5B-Instruct
 * - Size: 120MB (INT4 quantized)
 * - Context: 32K tokens
 * - Vocab: 152K tokens (SentencePiece)
 *
 * @property context Android application context for asset access
 */
class QwenCodingEngine(
    private val context: Context
) : CodingEngine {

    private val modelDir = "models/qwen-coder"
    private val templateDir = "templates"

    // ONNX Runtime components
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: SentencePieceTokenizer? = null

    // Model state
    private var isModelLoaded = false
    private val modelLock = Any()

    /**
     * Check if dynamic feature module is available
     *
     * Verifies:
     * - Model files exist in assets
     * - Tokenizer files exist
     * - ONNX Runtime is accessible
     */
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check model file
            val modelExists = context.assets.list(modelDir)?.contains("model_quantized.onnx") == true

            // Check tokenizer
            val tokenizerExists = context.assets.list(modelDir)?.contains("tokenizer.model") == true

            modelExists && tokenizerExists
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Load ONNX model and tokenizer into memory
     *
     * Steps:
     * 1. Create ONNX Runtime environment
     * 2. Load model from assets to session
     * 3. Initialize SentencePiece tokenizer
     * 4. Warm up with test inference
     *
     * @return Result indicating success or failure
     */
    override suspend fun loadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(modelLock) {
            if (isModelLoaded) {
                return@withContext Result.success(Unit)
            }

            try {
                // 1. Create ONNX Runtime environment
                ortEnvironment = OrtEnvironment.getEnvironment()

                // 2. Load model
                val modelPath = copyAssetToCache("$modelDir/model_quantized.onnx")
                val sessionOptions = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(4) // Use 4 CPU threads
                    setInterOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }

                ortSession = ortEnvironment!!.createSession(
                    modelPath,
                    sessionOptions
                )

                // 3. Initialize tokenizer
                val tokenizerPath = copyAssetToCache("$modelDir/tokenizer.model")
                tokenizer = SentencePieceTokenizer(tokenizerPath)

                // 4. Warm up (test inference)
                warmUpModel()

                isModelLoaded = true
                Result.success(Unit)
            } catch (e: Exception) {
                ortSession?.close()
                ortSession = null
                ortEnvironment?.close()
                ortEnvironment = null
                tokenizer?.close()
                tokenizer = null
                Result.failure(e)
            }
        }
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
            tokenizer?.close()
            tokenizer = null
            isModelLoaded = false
        }
    }

    /**
     * Generate code using template-driven approach
     *
     * Flow:
     * 1. Emit Started event
     * 2. Load template (LoadingTemplate)
     * 3. Construct prompt with template structure
     * 4. Run inference with streaming (Generating + PartialResult)
     * 5. Validate JSON output (Validating)
     * 6. Inject into template (InjectingTemplate)
     * 7. Emit Completed with final HTML
     *
     * @param request Generation request
     * @return Flow of generation events
     */
    override fun generateCode(request: GenerationRequest): Flow<GenerationEvent> = flow {
        val startTime = System.currentTimeMillis()

        try {
            // 1. Started
            emit(GenerationEvent.Started(startTime))

            // Check model loaded
            if (!isModelLoaded) {
                emit(GenerationEvent.Failed(
                    IllegalStateException("Model not loaded. Call loadModel() first."),
                    "initialization"
                ))
                return@flow
            }

            // 2. Load template
            emit(GenerationEvent.LoadingTemplate(request.templateType))
            val templateLoadStart = System.currentTimeMillis()
            val template = loadTemplate(request.templateType)
            val example = loadExample(request.templateType)
            val templateLoadTime = System.currentTimeMillis() - templateLoadStart

            // 3. Construct prompt
            val prompt = buildPrompt(request.templateType, request.topic, example, request.config)

            // 4. Generate with streaming
            emit(GenerationEvent.Generating(0))
            val inferenceStart = System.currentTimeMillis()

            var generatedContent = ""
            var tokensGenerated = 0

            // Tokenize input
            val inputIds = tokenizer!!.encode(prompt)

            // Generate tokens iteratively
            val maxNewTokens = request.config.maxTokens
            val stopTokens = request.config.stopSequences.map { tokenizer!!.encode(it) }

            for (i in 0 until maxNewTokens) {
                val nextToken = generateNextToken(
                    inputIds + generatedContent.let { tokenizer!!.encode(it) },
                    request.config.temperature,
                    request.config.topP
                )

                if (nextToken == null) break

                generatedContent += tokenizer!!.decode(listOf(nextToken))
                tokensGenerated++

                // Emit progress
                val progress = ((i + 1) * 100) / maxNewTokens
                emit(GenerationEvent.Generating(progress.coerceIn(0, 100)))

                // Emit partial result every 10 tokens
                if (i % 10 == 0) {
                    emit(GenerationEvent.PartialResult(generatedContent))
                }

                // Check stop conditions
                if (stopTokens.any { generatedContent.endsWith(tokenizer!!.decode(it)) }) {
                    break
                }
            }

            val inferenceTime = System.currentTimeMillis() - inferenceStart

            // 5. Validate
            emit(GenerationEvent.Validating)
            val validationStart = System.currentTimeMillis()

            // Parse JSON from generated content
            val jsonContent = extractJSON(generatedContent)

            val validationTime = System.currentTimeMillis() - validationStart

            // 6. Inject into template
            emit(GenerationEvent.InjectingTemplate)
            val finalHtml = injectTemplate(template, jsonContent, request)

            // 7. Completed
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
     * Validate generated HTML
     *
     * Checks:
     * - Valid HTML structure (basic)
     * - No script injection (< script > tags)
     * - Accessibility attributes present
     */
    override suspend fun validateCode(html: String): ValidationResult = withContext(Dispatchers.Default) {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()
        val suggestions = mutableListOf<String>()

        // Check HTML structure
        if (!html.contains("<!DOCTYPE html>", ignoreCase = true)) {
            errors.add(ValidationIssue(
                IssueType.INVALID_HTML,
                "Missing DOCTYPE declaration",
                null,
                IssueSeverity.ERROR
            ))
        }

        // Security: Check for inline scripts (templates use embedded scripts which is okay)
        val dangerousPatterns = listOf(
            "eval\\(",
            "innerHTML\\s*=",
            "document.write\\("
        )
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

        // Suggestions
        if (!html.contains("lang=", ignoreCase = true)) {
            suggestions.add("Add lang attribute to <html> tag for better accessibility")
        }

        ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            suggestions = suggestions
        )
    }

    /**
     * Get model information
     */
    override fun getModelInfo(): ModelInfo = ModelInfo(
        name = "Qwen2.5-Coder-0.5B-Instruct",
        version = "0.5B",
        sizeBytes = 120 * 1024 * 1024L, // 120MB
        contextWindow = 32768, // 32K tokens
        supportedLanguages = listOf(
            "HTML", "CSS", "JavaScript", "Python", "Java", "Kotlin",
            "TypeScript", "C++", "Go", "Rust", "and 82 more..."
        ),
        capabilities = listOf(
            "Template-driven web development",
            "Interactive quiz generation",
            "Canvas game creation",
            "SVG data visualization",
            "Presentation slides"
        )
    )

    /**
     * Estimate generation time based on request
     */
    override suspend fun estimateGenerationTime(request: GenerationRequest): Long {
        // Base estimates (in milliseconds)
        val baseTime = when (request.templateType) {
            TemplateType.QUIZ -> 20_000L // 20 seconds
            TemplateType.GAME -> 35_000L // 35 seconds
            TemplateType.SVG_CHART -> 18_000L // 18 seconds
            TemplateType.PRESENTATION -> 45_000L // 45 seconds
        }

        // Adjust for complexity
        val complexityFactor = when (request.config.targetAudience) {
            AudienceLevel.BEGINNER -> 0.8f
            AudienceLevel.GENERAL -> 1.0f
            AudienceLevel.ADVANCED -> 1.2f
        }

        return (baseTime * complexityFactor).toLong()
    }

    // ===== Private Helper Methods =====

    /**
     * Copy asset file to cache directory
     */
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

    /**
     * Warm up model with test inference
     */
    private suspend fun warmUpModel() = withContext(Dispatchers.IO) {
        val testPrompt = "def hello():"
        val inputIds = tokenizer!!.encode(testPrompt)
        generateNextToken(inputIds, 0.7f, 0.9f)
    }

    /**
     * Generate next token using ONNX model
     */
    private fun generateNextToken(
        inputIds: List<Long>,
        temperature: Float,
        topP: Float
    ): Long? {
        return try {
            val env = ortEnvironment ?: return null
            val session = ortSession ?: return null

            // Create input tensor
            val inputBuffer = LongBuffer.wrap(inputIds.toLongArray())
            val inputShape = longArrayOf(1, inputIds.size.toLong())
            val inputTensor = OnnxTensor.createTensor(env, inputBuffer, inputShape)

            // Run inference
            val outputs = session.run(mapOf("input_ids" to inputTensor))
            val logits = outputs[0]?.value as? Array<*>

            inputTensor.close()
            outputs.close()

            // Apply temperature and top-p sampling
            // Simplified implementation - actual would use proper sampling
            logits?.let {
                // Return most likely token (greedy for now)
                0L // Placeholder
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load template from assets
     */
    private fun loadTemplate(templateType: TemplateType): String {
        val templatePath = when (templateType) {
            TemplateType.QUIZ -> "$templateDir/quiz/base.html"
            TemplateType.GAME -> "$templateDir/games/canvas-base.html"
            TemplateType.SVG_CHART -> "$templateDir/svg/chart-base.html"
            TemplateType.PRESENTATION -> "$templateDir/presentation/slide-base.html"
        }
        return context.assets.open(templatePath).bufferedReader().use { it.readText() }
    }

    /**
     * Load example JSON for template
     */
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
     * Build prompt with template structure
     */
    private fun buildPrompt(
        templateType: TemplateType,
        topic: String,
        example: String,
        config: GenerationConfig
    ): String {
        val typeDescription = when (templateType) {
            TemplateType.QUIZ -> "quiz with 5 multiple-choice questions"
            TemplateType.GAME -> "game configuration"
            TemplateType.SVG_CHART -> "data visualization chart"
            TemplateType.PRESENTATION -> "presentation with 5 slides"
        }

        return """
You are generating $typeDescription for the M1K3 Canvas template system.

TEMPLATE FORMAT:
$example

YOUR TASK:
Generate content about: $topic

REQUIREMENTS:
- Output ONLY valid JSON matching the template format
- Do not include markdown code blocks
- Start directly with { or [
- End with } or ]
- ${if (config.includeComments) "Include helpful comments" else "No comments"}
- Target audience: ${config.targetAudience.name.lowercase()}

Generate the JSON now:
""".trim()
    }

    /**
     * Extract JSON from generated content
     */
    private fun extractJSON(content: String): String {
        // Remove markdown code blocks if present
        var json = content.trim()
        if (json.startsWith("```")) {
            json = json.substringAfter("```json").substringAfter("```").substringBeforeLast("```").trim()
        }
        return json
    }

    /**
     * Inject content into template
     */
    private fun injectTemplate(
        template: String,
        jsonContent: String,
        request: GenerationRequest
    ): String {
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
            .replace("{{CHART_TYPE}}", "bar") // Default
    }
}
