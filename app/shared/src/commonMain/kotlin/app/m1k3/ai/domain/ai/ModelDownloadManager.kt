package app.m1k3.ai.domain.ai

/**
 * ModelDownloadManager - Manages downloadable GGUF model files
 *
 * Domain interface - Pure Kotlin, no platform dependencies.
 *
 * Handles checking, locating, and removing downloaded model files.
 * Download itself is handled by platform-specific implementations.
 *
 * @see LlmModel for model definitions
 */
interface ModelDownloadManager {
    /**
     * Check if a model is downloaded and available for use.
     *
     * @param modelId The LlmModel.id to check
     * @return true if the model file exists locally
     */
    fun isModelAvailable(modelId: String): Boolean

    /**
     * Get the local file path for a downloaded model.
     *
     * @param modelId The LlmModel.id to look up
     * @return Absolute path to the GGUF file, or null if not downloaded
     */
    fun getModelPath(modelId: String): String?

    /**
     * Delete a downloaded model to free storage.
     *
     * @param modelId The LlmModel.id to delete
     * @return true if the model was deleted, false if not found
     */
    fun deleteModel(modelId: String): Boolean
}
