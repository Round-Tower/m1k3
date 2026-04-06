package app.m1k3.ai.assistant.avatar

/**
 * UI state for the Avatar Selection Gallery.
 *
 * Tracks available models, current selection, and loading state.
 */
data class AvatarGalleryState(
    /** All available avatar models */
    val models: List<ModelConfig> = emptyList(),

    /** Currently selected model ID */
    val selectedModelId: String = ModelRegistry.DEFAULT_MODEL_ID,

    /** Whether gallery is loading */
    val isLoading: Boolean = false
) {
    /** Get the currently selected model config, or null if not found */
    val selectedModel: ModelConfig?
        get() = models.firstOrNull { it.id == selectedModelId }

    /** Available categories for filtering */
    val categories: List<String>
        get() = models.map { it.category }.distinct().sorted()
}
