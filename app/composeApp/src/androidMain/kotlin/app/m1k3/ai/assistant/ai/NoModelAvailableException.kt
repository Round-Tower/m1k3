package app.m1k3.ai.assistant.ai

import app.m1k3.ai.domain.ai.LlmModel

/**
 * NoModelAvailableException - thrown when a required model cannot be found.
 *
 * Signals the app to navigate to the model download screen.
 * Thrown by [LlamaCppEngine.resolveModelPath] when:
 * - No override path is set
 * - No downloaded model exists in filesDir/models/
 * - No bundled asset is available (no-bundle MVP builds)
 */
class NoModelAvailableException(
    val model: LlmModel,
    cause: Throwable? = null
) : Exception(
    "Model '${model.displayName}' (${model.filename}) is not available. Download required.",
    cause
)
