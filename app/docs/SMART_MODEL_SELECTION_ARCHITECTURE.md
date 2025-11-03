# Smart Model Selection Architecture

## Overview
This document describes the architecture for smart auto-selection between SmolLM2-360M and Qwen2.5-Coder-0.5B based on template type.

## Current Architecture (Single Engine)

```kotlin
class CodeGenerationViewModel(
    private val codingEngine: CodingEngine  // Single engine injected
) : ViewModel()
```

**Limitation:** Can only use one model, no swapping.

## Proposed Architecture (Multi-Engine with Auto-Selection)

### Option 1: Engine Factory Pattern (RECOMMENDED)

```kotlin
class CodeGenerationViewModel(
    private val context: Context  // Android context for engine creation
) : ViewModel() {

    private var currentEngine: CodingEngine? = null
    private var currentModel: CodingModel? = null

    fun generateCode() {
        // Auto-select best model
        val bestModel = ModelRegistry.getBestModelForTemplate(
            uiState.value.selectedTemplate
        )

        // Swap engine if needed
        if (currentModel != bestModel) {
            currentEngine?.unloadModel()
            currentEngine = createEngine(bestModel)
            currentEngine?.loadModel()
            currentModel = bestModel
        }

        // Generate with current engine
        currentEngine?.generateCode(request)...
    }

    private fun createEngine(model: CodingModel): CodingEngine {
        return when (model) {
            CodingModel.SMOL_LM2_360M -> SmolLM2CodingEngine(context)
            CodingModel.QWEN_CODER_0_5B -> QwenCodingEngine(context)
        }
    }
}
```

**Pros:**
- ✅ Simple, clean API
- ✅ Easy model swapping
- ✅ Lazy loading (only load engine when needed)
- ✅ Resource efficient (unload old before loading new)

**Cons:**
- ⚠️ Requires changing ViewModel constructor
- ⚠️ Needs dependency injection update

### Option 2: Dual-Engine Pre-Loaded

```kotlin
class CodeGenerationViewModel(
    private val smolEngine: SmolLM2CodingEngine,
    private val qwenEngine: QwenCodingEngine
) : ViewModel() {

    fun generateCode() {
        val engine = when (ModelRegistry.getBestModelForTemplate(template)) {
            SMOL_LM2_360M -> smolEngine
            QWEN_CODER_0_5B -> qwenEngine
        }

        engine.generateCode(request)...
    }
}
```

**Pros:**
- ✅ Both engines always available
- ✅ Instant switching (no load time)

**Cons:**
- ❌ Memory wasteful (2 models loaded simultaneously)
- ❌ Slower startup (load both models)
- ❌ Not scalable (hard to add more models)

### Option 3: ModelEngineManager (Most Flexible)

```kotlin
interface ModelEngineManager {
    suspend fun getEngine(model: CodingModel): CodingEngine
    suspend fun releaseEngine(model: CodingModel)
}

class CodeGenerationViewModel(
    private val engineManager: ModelEngineManager
) : ViewModel() {

    fun generateCode() {
        val bestModel = ModelRegistry.getBestModelForTemplate(template)
        val engine = engineManager.getEngine(bestModel)
        engine.generateCode(request)...
    }
}
```

**Pros:**
- ✅ Most flexible
- ✅ Easy testing (mock manager)
- ✅ Can implement caching, pooling, etc.
- ✅ Clean separation of concerns

**Cons:**
- ⚠️ More complex
- ⚠️ Additional abstraction layer

## Recommendation: Option 1 (Engine Factory)

**Rationale:**
- Simple and straightforward
- Resource efficient (only one model loaded at a time)
- Easy to understand and maintain
- Scalable (easy to add more models)
- Minimal code changes

## Implementation Plan

### Step 1: Update ViewModel Constructor

```kotlin
// BEFORE
class CodeGenerationViewModel(
    private val codingEngine: CodingEngine
) : ViewModel()

// AFTER
class CodeGenerationViewModel(
    private val context: Context
) : ViewModel() {
    private var currentEngine: CodingEngine? = null
    private var currentModel: CodingModel? = null
}
```

### Step 2: Add Engine Creation Logic

```kotlin
private suspend fun ensureCorrectEngine(templateType: TemplateType) {
    val bestModel = ModelRegistry.getBestModelForTemplate(templateType)

    if (currentModel != bestModel) {
        // Unload old engine
        currentEngine?.unloadModel()

        // Create new engine
        currentEngine = when (bestModel) {
            CodingModel.SMOL_LM2_360M -> SmolLM2CodingEngine(context)
            CodingModel.QWEN_CODER_0_5B -> QwenCodingEngine(context)
        }

        // Load new engine
        currentEngine?.loadModel()
        currentModel = bestModel

        // Update UI state
        _uiState.update {
            it.copy(
                autoSelectedModel = bestModel,
                modelSelectionReason = ModelRegistry.getSelectionReason(bestModel, templateType),
                modelInfo = currentEngine?.getModelInfo()
            )
        }
    }
}
```

### Step 3: Update generateCode()

```kotlin
fun generateCode() {
    viewModelScope.launch {
        // Ensure correct engine is loaded
        ensureCorrectEngine(uiState.value.selectedTemplate)

        // Generate with current engine
        currentEngine?.generateCode(request)?.collect { event ->
            _generationEvents.emit(event)
            // Handle events...
        }
    }
}
```

### Step 4: Update UI State

```kotlin
data class CodeGenerationUiState(
    // ... existing fields ...

    // NEW: Auto-selection fields
    val autoSelectedModel: CodingModel? = null,
    val modelSelectionReason: String = ""
)
```

## Migration Path

1. ✅ Create ModelRegistry.kt
2. ✅ Create SmolLM2CodingEngine.kt
3. ⏳ Update CodeGenerationViewModel.kt
4. ⏳ Update CodeGenerationScreen.kt (show model indicator)
5. ⏳ Update dependency injection (if using DI framework)
6. ⏳ Write tests

## Testing Strategy

```kotlin
@Test
fun `test auto-selects SmolLM2 for quiz`() = runTest {
    val viewModel = CodeGenerationViewModel(context)

    viewModel.setTemplateType(TemplateType.QUIZ)
    viewModel.generateCode()

    assertEquals(CodingModel.SMOL_LM2_360M, viewModel.uiState.value.autoSelectedModel)
    assertEquals("Best for creative, varied questions", viewModel.uiState.value.modelSelectionReason)
}

@Test
fun `test auto-selects Qwen for game`() = runTest {
    val viewModel = CodeGenerationViewModel(context)

    viewModel.setTemplateType(TemplateType.GAME)
    viewModel.generateCode()

    assertEquals(CodingModel.QWEN_CODER_0_5B, viewModel.uiState.value.autoSelectedModel)
    assertEquals("Best for game logic and algorithms", viewModel.uiState.value.modelSelectionReason)
}

@Test
fun `test swaps engines when template changes`() = runTest {
    val viewModel = CodeGenerationViewModel(context)

    // Start with quiz (SmolLM2)
    viewModel.setTemplateType(TemplateType.QUIZ)
    viewModel.generateCode()
    val firstModel = viewModel.uiState.value.autoSelectedModel

    // Switch to game (Qwen)
    viewModel.setTemplateType(TemplateType.GAME)
    viewModel.generateCode()
    val secondModel = viewModel.uiState.value.autoSelectedModel

    assertNotEquals(firstModel, secondModel)
    assertEquals(CodingModel.SMOL_LM2_360M, firstModel)
    assertEquals(CodingModel.QWEN_CODER_0_5B, secondModel)
}
```

## Future Enhancements

### Phase 2: Model Performance Tracking
Track which model performs better for each user and adjust routing:

```kotlin
object ModelRegistry {
    private val performanceData = mutableMapOf<Pair<TemplateType, CodingModel>, Float>()

    fun recordPerformance(template: TemplateType, model: CodingModel, rating: Float) {
        performanceData[template to model] = rating
    }

    fun getBestModelForTemplate(template: TemplateType): CodingModel {
        // Check user preferences first
        val userPreferred = getUserPreferredModel(template)
        if (userPreferred != null) return userPreferred

        // Fall back to default routing
        return getDefaultModelForTemplate(template)
    }
}
```

### Phase 3: A/B Testing
Randomly assign users to different models to collect data:

```kotlin
fun getBestModelForTemplate(template: TemplateType, userId: String): CodingModel {
    if (isInABTest(userId)) {
        return getRandomModel()
    }
    return getDefaultModelForTemplate(template)
}
```

### Phase 4: User Override
Let advanced users manually select model:

```kotlin
data class CodeGenerationUiState(
    val autoSelectedModel: CodingModel? = null,
    val userOverrideModel: CodingModel? = null,  // NEW
    val modelSelectionMode: ModelSelectionMode = AUTO  // NEW
)

enum class ModelSelectionMode {
    AUTO,    // Smart selection
    MANUAL   // User chooses
}
```

## Conclusion

**Recommendation:** Implement Option 1 (Engine Factory Pattern)

This provides the best balance of:
- Simplicity
- Resource efficiency
- Scalability
- Maintainability

The architecture is ready for future enhancements (performance tracking, A/B testing, user override) without major refactoring.

---

**Status:** Architecture Designed ✅
**Next Step:** Implement ViewModel changes
**Estimated Time:** 30 minutes
