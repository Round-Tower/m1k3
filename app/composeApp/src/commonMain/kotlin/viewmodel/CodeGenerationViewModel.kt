package viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import domain.coding.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for code generation screen with Smart Model Auto-Selection
 *
 * This is a platform-specific ViewModel. See androidMain for Android implementation.
 *
 * Manages:
 * - Multi-engine system (SmolLM2-360M + Qwen2.5-Coder-0.5B)
 * - Smart auto-selection based on template type
 * - Engine swapping and lifecycle (load/unload)
 * - Dynamic feature module availability
 * - Generation state and progress
 * - User interactions and configuration
 *
 * State Flow:
 * 1. Check module availability
 * 2. If unavailable → prompt download
 * 3. Auto-select best model for template type
 * 4. Swap engines if template changed
 * 5. Generate code with streaming
 * 6. Display results with model indicator
 * 7. Unload model when done
 *
 * Smart Auto-Selection:
 * - QUIZ → SmolLM2-360M (creative, varied questions)
 * - PRESENTATION → SmolLM2-360M (storytelling, visual flow)
 * - SVG_CHART → Qwen2.5-Coder (data handling, calculations)
 * - GAME → Qwen2.5-Coder (game logic, algorithms)
 *
 * Platform-specific implementation requires Context on Android
 * See: androidMain/kotlin/viewmodel/CodeGenerationViewModel.android.kt
 */

/**
 * Shared ViewModel logic (platform-agnostic)
 */
abstract class BaseCodeGenerationViewModel : ViewModel() {

    // Current engine state
    protected var currentEngine: CodingEngine? = null
    protected var currentModel: CodingModel? = null

    // UI State
    private val _uiState = MutableStateFlow(CodeGenerationUiState())
    val uiState: StateFlow<CodeGenerationUiState> = _uiState.asStateFlow()

    // Generation events (for UI to observe)
    private val _generationEvents = MutableSharedFlow<GenerationEvent>()
    val generationEvents: SharedFlow<GenerationEvent> = _generationEvents.asSharedFlow()

    init {
        checkModuleAvailability()
    }

    /**
     * Create engine for specified model
     * Must be implemented by platform-specific code
     */
    protected abstract suspend fun createEngine(model: CodingModel): CodingEngine

    /**
     * Check if dynamic feature module is available
     */
    private fun checkModuleAvailability() {
        viewModelScope.launch {
            // Check availability by creating a temporary engine
            val tempModel = ModelRegistry.getBestModelForTemplate(TemplateType.QUIZ)
            val tempEngine = createEngine(tempModel)
            val isAvailable = tempEngine.isAvailable()

            _uiState.update { it.copy(isModuleAvailable = isAvailable) }

            // Don't auto-load - wait for user to select template first
        }
    }

    /**
     * Ensure the correct engine is loaded for the selected template type
     *
     * This is the core of the smart auto-selection system:
     * 1. Determines best model for current template
     * 2. If different from current model, swaps engines
     * 3. Updates UI state with model selection info
     */
    protected suspend fun ensureCorrectEngine(templateType: TemplateType) {
        val bestModel = ModelRegistry.getBestModelForTemplate(templateType)

        if (currentModel != bestModel) {
            // Unload old engine
            currentEngine?.unloadModel()

            // Create new engine
            currentEngine = createEngine(bestModel)

            // Load new engine
            val loadResult = currentEngine?.loadModel()

            if (loadResult?.isSuccess == true) {
                currentModel = bestModel

                // Update UI state with auto-selected model info
                _uiState.update {
                    it.copy(
                        isModelLoaded = true,
                        isLoadingModel = false,
                        autoSelectedModel = bestModel,
                        modelSelectionReason = ModelRegistry.getSelectionReason(bestModel, templateType),
                        modelInfo = currentEngine?.getModelInfo()
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isModelLoaded = false,
                        isLoadingModel = false,
                        error = "Failed to load ${bestModel.displayName}: ${loadResult?.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    /**
     * Load the appropriate model for current template
     * Uses smart auto-selection via ensureCorrectEngine()
     */
    fun loadModel() {
        if (_uiState.value.isModelLoaded || _uiState.value.isLoadingModel) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModel = true, error = null) }

            // Smart selection based on current template
            ensureCorrectEngine(_uiState.value.selectedTemplate)
        }
    }

    /**
     * Unload the model to free memory
     */
    fun unloadModel() {
        viewModelScope.launch {
            currentEngine?.unloadModel()
            currentEngine = null
            currentModel = null
            _uiState.update {
                it.copy(
                    isModelLoaded = false,
                    modelInfo = null,
                    autoSelectedModel = null,
                    modelSelectionReason = ""
                )
            }
        }
    }

    /**
     * Generate code with current configuration
     * Automatically ensures correct engine is loaded for template type
     */
    fun generateCode() {
        val currentState = _uiState.value

        // Validation
        if (currentState.topic.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a topic") }
            return
        }

        if (currentState.isGenerating) {
            return
        }

        // Create request
        val request = GenerationRequest(
            templateType = currentState.selectedTemplate,
            topic = currentState.topic,
            config = GenerationConfig(
                maxTokens = currentState.maxTokens,
                temperature = currentState.temperature,
                topP = currentState.topP,
                includeComments = currentState.includeComments,
                targetAudience = currentState.audienceLevel
            )
        )

        // Start generation and track the job for cancellation
        generationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    progress = 0,
                    generatedHtml = null,
                    error = null,
                    metrics = null
                )
            }

            // Ensure correct engine is loaded for this template type
            ensureCorrectEngine(currentState.selectedTemplate)

            // Proceed with generation if engine loaded successfully
            if (currentEngine == null) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        error = "Failed to load appropriate model"
                    )
                }
                return@launch
            }

            currentEngine!!.generateCode(request)
                .catch { e ->
                    // Handle flow exceptions
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            error = "Generation error: ${e.message}",
                            generationStage = "Failed"
                        )
                    }
                }
                .collect { event ->
                    // Emit to SharedFlow for UI to observe
                    _generationEvents.emit(event)

                    // Update state based on event
                    when (event) {
                    is GenerationEvent.Started -> {
                        _uiState.update { it.copy(generationStage = "Starting...") }
                    }

                    is GenerationEvent.LoadingTemplate -> {
                        _uiState.update {
                            it.copy(generationStage = "Loading ${event.templateType.name.lowercase()} template...")
                        }
                    }

                    is GenerationEvent.Generating -> {
                        _uiState.update {
                            it.copy(
                                progress = event.progress.toInt(),
                                generationStage = "Generating code... ${event.progress.toInt()}%"
                            )
                        }
                    }

                    is GenerationEvent.Progress -> {
                        _uiState.update {
                            it.copy(
                                progress = event.progress.toInt(),
                                generationStage = event.stage
                            )
                        }
                    }

                    is GenerationEvent.PartialResult -> {
                        _uiState.update {
                            it.copy(
                                partialResult = event.partial,
                                generationStage = "Generating... (${event.partial.length} chars)"
                            )
                        }
                    }

                    is GenerationEvent.Validating -> {
                        _uiState.update { it.copy(generationStage = "Validating output...") }
                    }

                    is GenerationEvent.InjectingTemplate -> {
                        _uiState.update { it.copy(generationStage = "Injecting content...") }
                    }

                    is GenerationEvent.Completed -> {
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                progress = 100,
                                generatedHtml = event.html,
                                metrics = event.metrics,
                                generationStage = "Complete!",
                                partialResult = null
                            )
                        }
                    }

                    is GenerationEvent.Failed -> {
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                error = "Generation failed at ${event.stage}: ${event.error.message}",
                                generationStage = "Failed"
                            )
                        }
                    }
                }
            }
        }
    }

    // Track the generation job for cancellation
    private var generationJob: kotlinx.coroutines.Job? = null

    /**
     * Cancel ongoing generation
     */
    fun cancelGeneration() {
        // Cancel the coroutine job running generation
        generationJob?.cancel()
        generationJob = null

        // Reset state
        _uiState.update {
            it.copy(
                isGenerating = false,
                progress = 0,
                generationStage = "Cancelled",
                partialResult = null
            )
        }
    }

    /**
     * Update selected template type
     * Triggers smart model swapping if needed
     */
    fun setTemplateType(templateType: TemplateType) {
        _uiState.update { it.copy(selectedTemplate = templateType) }

        // If model is already loaded, check if we need to swap engines
        if (_uiState.value.isModelLoaded) {
            viewModelScope.launch {
                ensureCorrectEngine(templateType)
            }
        }
    }

    /**
     * Update topic
     */
    fun setTopic(topic: String) {
        _uiState.update { it.copy(topic = topic, error = null) }
    }

    /**
     * Update temperature parameter
     */
    fun setTemperature(temperature: Float) {
        _uiState.update { it.copy(temperature = temperature.coerceIn(0f, 1f)) }
    }

    /**
     * Update top-p parameter
     */
    fun setTopP(topP: Float) {
        _uiState.update { it.copy(topP = topP.coerceIn(0f, 1f)) }
    }

    /**
     * Update max tokens
     */
    fun setMaxTokens(maxTokens: Int) {
        _uiState.update { it.copy(maxTokens = maxTokens.coerceIn(128, 4096)) }
    }

    /**
     * Toggle include comments
     */
    fun toggleIncludeComments() {
        _uiState.update { it.copy(includeComments = !it.includeComments) }
    }

    /**
     * Set audience level
     */
    fun setAudienceLevel(level: AudienceLevel) {
        _uiState.update { it.copy(audienceLevel = level) }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Reset generation state (for new generation)
     */
    fun resetGeneration() {
        _uiState.update {
            it.copy(
                generatedHtml = null,
                metrics = null,
                progress = 0,
                partialResult = null,
                generationStage = "",
                error = null
            )
        }
    }

    /**
     * Get estimated generation time for current configuration
     */
    suspend fun getEstimatedTime(): Long {
        val currentState = _uiState.value
        val request = GenerationRequest(
            templateType = currentState.selectedTemplate,
            topic = currentState.topic,
            config = GenerationConfig(
                targetAudience = currentState.audienceLevel
            )
        )

        // Ensure correct engine is created for estimate
        val bestModel = ModelRegistry.getBestModelForTemplate(currentState.selectedTemplate)
        val engine = currentEngine ?: createEngine(bestModel)

        return engine.estimateGenerationTime(request)
    }

    override fun onCleared() {
        super.onCleared()
        // Lightweight cleanup - just null out references
        // viewModelScope is already cancelled at this point
        // Heavy cleanup (ONNX session) should happen in Activity.onDestroy() with proper lifecycle

        // Cancel any ongoing generation job
        generationJob?.cancel()
        generationJob = null

        // Null out engine references to allow GC
        // Note: Actual ONNX cleanup happens in Activity lifecycle with timeout protection
        currentEngine = null
        currentModel = null
    }

    // Extension function for cleaner state updates
    private fun MutableStateFlow<CodeGenerationUiState>.update(
        function: (CodeGenerationUiState) -> CodeGenerationUiState
    ) {
        value = function(value)
    }
}

/**
 * UI State for code generation screen with smart model auto-selection
 */
data class CodeGenerationUiState(
    // Module availability
    val isModuleAvailable: Boolean = false,
    val isLoadingModel: Boolean = false,
    val isModelLoaded: Boolean = false,
    val modelInfo: ModelInfo? = null,

    // Smart auto-selection (NEW)
    val autoSelectedModel: CodingModel? = null,
    val modelSelectionReason: String = "",

    // Generation state
    val isGenerating: Boolean = false,
    val progress: Int = 0,
    val generationStage: String = "",
    val partialResult: String? = null,

    // Configuration
    val selectedTemplate: TemplateType = TemplateType.QUIZ,
    val topic: String = "",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 2048,
    val includeComments: Boolean = false,
    val audienceLevel: AudienceLevel = AudienceLevel.GENERAL,

    // Results
    val generatedHtml: String? = null,
    val metrics: GenerationMetrics? = null,

    // Error handling
    val error: String? = null
) {
    /**
     * Check if user can start generation
     */
    val canGenerate: Boolean
        get() = isModelLoaded &&
                !isGenerating &&
                topic.isNotBlank()

    /**
     * Get human-readable template name
     */
    val templateDisplayName: String
        get() = when (selectedTemplate) {
            TemplateType.QUIZ -> "Quiz"
            TemplateType.GAME -> "Game"
            TemplateType.SVG_CHART -> "Chart"
            TemplateType.PRESENTATION -> "Presentation"
        }

    /**
     * Get template description
     */
    val templateDescription: String
        get() = when (selectedTemplate) {
            TemplateType.QUIZ -> "Interactive quiz with 5 multiple-choice questions"
            TemplateType.GAME -> "Canvas-based game with controls and scoring"
            TemplateType.SVG_CHART -> "Data visualization (bar or line chart)"
            TemplateType.PRESENTATION -> "Full-screen presentation slides"
        }

    /**
     * Check if configuration is advanced (non-default)
     */
    val hasAdvancedConfig: Boolean
        get() = temperature != 0.7f ||
                topP != 0.9f ||
                maxTokens != 2048 ||
                includeComments ||
                audienceLevel != AudienceLevel.GENERAL

    /**
     * Get progress as 0-1 float
     */
    val progressFloat: Float
        get() = progress / 100f

    /**
     * Check if there are results to display
     */
    val hasResults: Boolean
        get() = generatedHtml != null

    /**
     * Get formatted metrics display
     */
    val metricsDisplay: String?
        get() = metrics?.let {
            """
                Duration: ${it.durationMs / 1000f}s
                Tokens: ${it.tokensGenerated}
                Speed: ${"%.1f".format(it.tokensPerSecond)} tok/s
                Template load: ${it.templateLoadTimeMs}ms
                Inference: ${it.inferenceTimeMs}ms
                Validation: ${it.validationTimeMs}ms
            """.trimIndent()
        }
}
