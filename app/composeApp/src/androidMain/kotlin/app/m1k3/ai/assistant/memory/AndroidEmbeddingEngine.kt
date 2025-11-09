package app.m1k3.ai.assistant.memory

import app.m1k3.ai.assistant.embedding.EmbeddingEngine as PlatformEmbeddingEngine
import app.m1k3.ai.assistant.embedding.EmbeddingTaskType

/**
 * Android wrapper for EmbeddingEngine platform interface
 *
 * Bridges the existing platform embedding.EmbeddingEngine implementation
 * to the new memory.EmbeddingEngine interface required by MemoryManager.
 *
 * This adapter pattern allows the new memory system to work with existing
 * embedding infrastructure without duplicating code.
 *
 * **Architecture:**
 * ```
 * MemoryManager → memory.EmbeddingEngine (interface)
 *                        ↓
 *             AndroidEmbeddingEngine (adapter)
 *                        ↓
 *             embedding.EmbeddingEngine (platform)
 *                        ↓
 *             GemmaEmbeddingEngine (ONNX)
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val platformEngine: embedding.EmbeddingEngine = GemmaEmbeddingEngine(context)
 * val memoryEngine: memory.EmbeddingEngine = AndroidEmbeddingEngine(platformEngine)
 * val memoryManager = MemoryManager(
 *     embeddingEngine = memoryEngine,
 *     vectorSearch = androidVectorSearch,
 *     ...
 * )
 * ```
 */
class AndroidEmbeddingEngine(
    private val platformEngine: PlatformEmbeddingEngine
) : app.m1k3.ai.assistant.memory.EmbeddingEngine {

    override val dimensions: Int
        get() = platformEngine.embeddingDimensions

    /**
     * Generate embeddings for multiple texts
     *
     * Maps the new memory interface to the existing platform embedding interface.
     * Uses RETRIEVAL task type for memory storage (default for semantic memory).
     *
     * @param texts List of text strings to embed
     * @return Result with list of embedding vectors
     */
    override suspend fun embed(texts: List<String>): Result<List<FloatArray>> {
        return try {
            // Check if model is loaded
            if (!platformEngine.isLoaded) {
                return Result.failure(
                    IllegalStateException("Embedding model not loaded. Call loadModel() first.")
                )
            }

            // Use embedBatch from platform engine with RETRIEVAL task type
            // (most appropriate for semantic memory storage)
            platformEngine.embedBatch(texts, EmbeddingTaskType.RETRIEVAL)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
