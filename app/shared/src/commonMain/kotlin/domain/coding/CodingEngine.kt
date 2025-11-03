package domain.coding

import kotlinx.coroutines.flow.Flow

/**
 * Issue severity level
 */
enum class IssueSeverity {
    ERROR,
    WARNING,
    INFO
}

/**
 * Issue type
 */
enum class IssueType {
    SYNTAX,
    LOGIC,
    STYLE,
    PERFORMANCE,
    SECURITY,
    INVALID_HTML,
    ACCESSIBILITY
}

/**
 * Validation issue
 */
data class ValidationIssue(
    val type: IssueType,
    val message: String,
    val suggestion: String? = null,
    val severity: IssueSeverity = IssueSeverity.WARNING,
    val line: Int? = null
)

/**
 * Validation result for generated code
 */
data class ValidationResult(
    val isValid: Boolean,
    val issues: List<ValidationIssue> = emptyList(),
    @Deprecated("Use issues with ValidationIssue instead")
    val errors: List<String> = emptyList(),
    @Deprecated("Use issues with ValidationIssue instead")
    val warnings: List<String> = emptyList(),
    @Deprecated("Use issues with ValidationIssue instead")
    val suggestions: List<String> = emptyList()
)

/**
 * Coding engine interface for template-driven code generation
 */
interface CodingEngine {
    /**
     * Check if model is available (downloaded, extracted)
     */
    suspend fun isAvailable(): Boolean

    /**
     * Load model into memory
     */
    suspend fun loadModel(): Result<Unit>

    /**
     * Unload model from memory
     */
    suspend fun unloadModel()

    /**
     * Generate code from request with streaming events
     */
    fun generateCode(request: GenerationRequest): Flow<GenerationEvent>

    /**
     * Validate generated HTML
     */
    suspend fun validateCode(html: String): ValidationResult

    /**
     * Get model information
     */
    fun getModelInfo(): ModelInfo

    /**
     * Estimate generation time for request
     */
    suspend fun estimateGenerationTime(request: GenerationRequest): Long
}
