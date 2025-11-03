package domain.coding

/**
 * Model Registry - Smart Auto-Selection for Code Generation
 *
 * Automatically selects the best AI model for each template type:
 * - SmolLM2-360M: Better for creative, varied content (quizzes, presentations)
 * - Qwen2.5-Coder-0.5B: Better for technical, code-heavy tasks (games, charts)
 *
 * Users get optimal results without needing to choose models manually.
 *
 * Selection Strategy:
 * ```
 * QUIZ         → SmolLM2  (creative questions, varied topics)
 * PRESENTATION → SmolLM2  (storytelling, visual flow)
 * SVG_CHART    → Qwen    (data handling, precise calculations)
 * GAME         → Qwen    (complex logic, algorithms, physics)
 * ```
 */
object ModelRegistry {

    /**
     * Smart auto-selection: Choose best model for template type
     *
     * Selection is based on model strengths:
     * - SmolLM2-360M: General-purpose, creative, varied content
     * - Qwen2.5-Coder: Specialized coding, technical precision
     *
     * @param templateType Type of template to generate
     * @return Best model for this template
     */
    fun getBestModelForTemplate(templateType: TemplateType): CodingModel {
        return when (templateType) {
            TemplateType.QUIZ -> CodingModel.SMOL_LM2_360M
            TemplateType.PRESENTATION -> CodingModel.SMOL_LM2_360M
            TemplateType.SVG_CHART -> CodingModel.QWEN_CODER_0_5B
            TemplateType.GAME -> CodingModel.QWEN_CODER_0_5B
        }
    }

    /**
     * Get human-readable reason for model selection
     *
     * Explains to user why this model was chosen (educational + transparent)
     *
     * @param model Selected model
     * @param templateType Template being generated
     * @return Short explanation (e.g., "Best for creative content")
     */
    fun getSelectionReason(model: CodingModel, templateType: TemplateType): String {
        return when (model) {
            CodingModel.SMOL_LM2_360M -> when (templateType) {
                TemplateType.QUIZ -> "Best for creative, varied questions"
                TemplateType.PRESENTATION -> "Best for storytelling and visual flow"
                else -> "General-purpose model"
            }
            CodingModel.QWEN_CODER_0_5B -> when (templateType) {
                TemplateType.SVG_CHART -> "Best for data handling and calculations"
                TemplateType.GAME -> "Best for game logic and algorithms"
                else -> "Specialized coding model"
            }
        }
    }

    /**
     * Get detailed model information
     *
     * @param model Model to query
     * @return Complete metadata (size, context, capabilities)
     */
    fun getModelInfo(model: CodingModel): ModelInfo {
        return when (model) {
            CodingModel.SMOL_LM2_360M -> ModelInfo(
                name = "SmolLM2-360M-Instruct",
                version = "360M",
                sizeBytes = 180 * 1024 * 1024L, // 180MB (INT4)
                contextWindow = 8192, // 8K tokens
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
            CodingModel.QWEN_CODER_0_5B -> ModelInfo(
                name = "Qwen2.5-Coder-0.5B-Instruct",
                version = "0.5B",
                sizeBytes = 120 * 1024 * 1024L, // 120MB (INT4)
                contextWindow = 32768, // 32K tokens
                supportedLanguages = listOf(
                    "HTML", "CSS", "JavaScript", "Python", "Java", "Kotlin",
                    "TypeScript", "C++", "Go", "Rust", "and 82 more..."
                ),
                capabilities = listOf(
                    "Template-driven web development",
                    "Game logic and algorithms",
                    "Data visualization",
                    "Technical precision coding"
                )
            )
        }
    }

    /**
     * Get all available models
     *
     * @return List of all coding models
     */
    fun getAvailableModels(): List<CodingModel> {
        return CodingModel.entries
    }

    /**
     * Compare two models
     *
     * @param model1 First model
     * @param model2 Second model
     * @return Comparison summary
     */
    fun compareModels(model1: CodingModel, model2: CodingModel): ModelComparison {
        val info1 = getModelInfo(model1)
        val info2 = getModelInfo(model2)

        return ModelComparison(
            model1 = model1,
            model2 = model2,
            sizeDifference = info1.sizeBytes - info2.sizeBytes,
            contextDifference = info1.contextWindow - info2.contextWindow,
            recommendation = when {
                model1 == CodingModel.SMOL_LM2_360M ->
                    "Use ${info1.name} for creative content, ${info2.name} for technical tasks"
                else ->
                    "Use ${info2.name} for creative content, ${info1.name} for technical tasks"
            }
        )
    }
}

/**
 * Available coding models for template-driven generation
 */
enum class CodingModel {
    /**
     * SmolLM2-360M-Instruct
     * - Size: 180MB (INT4 quantized)
     * - Context: 8K tokens
     * - Specialization: General-purpose, creative content
     * - Best for: Quizzes, presentations, varied topics
     */
    SMOL_LM2_360M,

    /**
     * Qwen2.5-Coder-0.5B-Instruct
     * - Size: 120MB (INT4 quantized)
     * - Context: 32K tokens
     * - Specialization: Coding-focused, technical precision
     * - Best for: Games, charts, code-heavy tasks
     */
    QWEN_CODER_0_5B;

    /**
     * Get display name for UI
     */
    val displayName: String
        get() = when (this) {
            SMOL_LM2_360M -> "SmolLM2-360M"
            QWEN_CODER_0_5B -> "Qwen2.5-Coder"
        }

    /**
     * Get short description for UI
     */
    val shortDescription: String
        get() = when (this) {
            SMOL_LM2_360M -> "General-purpose"
            QWEN_CODER_0_5B -> "Coding specialist"
        }
}

/**
 * Model comparison result
 *
 * @property model1 First model
 * @property model2 Second model
 * @property sizeDifference Size difference in bytes (positive = model1 larger)
 * @property contextDifference Context difference in tokens (positive = model1 longer)
 * @property recommendation Usage recommendation
 */
data class ModelComparison(
    val model1: CodingModel,
    val model2: CodingModel,
    val sizeDifference: Long,
    val contextDifference: Int,
    val recommendation: String
)
