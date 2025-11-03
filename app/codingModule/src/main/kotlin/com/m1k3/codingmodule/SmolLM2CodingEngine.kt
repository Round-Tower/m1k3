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
                    setIntraOpNumThreads(4)
                    setInterOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }

                ortSession = ortEnvironment!!.createSession(modelPath, sessionOptions)

                // 3. Warm up model
                warmUpModel()

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
            emit(GenerationEvent.Started(startTime))

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

            // Generate with streaming
            emit(GenerationEvent.Generating(0))
            val inferenceStart = System.currentTimeMillis()

            var generatedContent = ""
            var tokensGenerated = 0

            // Simplified generation for placeholder
            // In production, this would use actual SmolLM2 inference
            val maxNewTokens = request.config.maxTokens.coerceAtMost(8192 - 1000) // 8K context limit

            for (i in 0 until maxNewTokens step 50) {
                // Placeholder: would call actual ONNX inference
                val progress = (i * 100) / maxNewTokens
                emit(GenerationEvent.Generating(progress.coerceIn(0, 100)))

                if (i % 100 == 0) {
                    emit(GenerationEvent.PartialResult(generatedContent))
                }
            }

            // Mock generated content
            generatedContent = generateMockContent(request)
            tokensGenerated = generatedContent.length / 4

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
            errors = errors,
            warnings = warnings,
            suggestions = suggestions
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
}
