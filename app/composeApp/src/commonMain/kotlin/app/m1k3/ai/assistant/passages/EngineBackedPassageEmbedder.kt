package app.m1k3.ai.assistant.passages

import app.m1k3.ai.assistant.embedding.EmbeddingEngine
import app.m1k3.ai.assistant.embedding.EmbeddingTaskType
import app.m1k3.ai.assistant.utils.Logger
import app.m1k3.ai.domain.passages.services.PassageEmbedder

private val logger = Logger.withTag("EngineBackedPassageEmbedder")

/**
 * EngineBackedPassageEmbedder — adapts the platform [EmbeddingEngine] to the
 * domain [PassageEmbedder] contract.
 *
 * Ensures the engine is loaded on first use (lazy) and tolerates failures
 * without throwing — returns null so the repository falls back to keyword
 * search. Ship-safe: if the on-device model isn't installed, personal-
 * knowledge retrieval still works via `LIKE`.
 */
class EngineBackedPassageEmbedder(
    private val engine: EmbeddingEngine,
) : PassageEmbedder {
    override val modelId: String get() = engine.modelName
    override val dimension: Int get() = engine.embeddingDimensions

    override suspend fun embed(text: String): FloatArray? {
        if (text.isBlank()) return null
        if (!engine.isLoaded) {
            val loaded = engine.loadModel()
            if (loaded.isFailure) {
                logger.w(loaded.exceptionOrNull()) {
                    "Embedding engine failed to load; passage retrieval will degrade to keyword"
                }
                return null
            }
        }
        return engine
            .embed(text, EmbeddingTaskType.RETRIEVAL)
            .onFailure { err ->
                logger.w(err) { "Embedding failed for ${text.length}-char passage" }
            }.getOrNull()
    }
}
