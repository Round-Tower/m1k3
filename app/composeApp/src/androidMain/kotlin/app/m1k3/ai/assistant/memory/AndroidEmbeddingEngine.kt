package app.m1k3.ai.assistant.memory

import app.m1k3.ai.domain.repositories.EmbeddingRepository
import app.m1k3.ai.assistant.embedding.EmbeddingEngine as PlatformEmbeddingEngine
import app.m1k3.ai.assistant.embedding.EmbeddingTaskType

/**
 * Android adapter for platform EmbeddingEngine → domain EmbeddingRepository
 *
 * Bridges the platform embedding.EmbeddingEngine implementation
 * to the domain.repositories.EmbeddingRepository interface.
 *
 * **Architecture:**
 * ```
 * Domain Layer:     MemoryManager → EmbeddingRepository (interface)
 *                                          ↓
 * Adapter:                       AndroidEmbeddingEngine
 *                                          ↓
 * Platform Layer:              embedding.EmbeddingEngine
 *                                          ↓
 * Implementation:              GemmaEmbeddingEngine (ONNX)
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val platformEngine: embedding.EmbeddingEngine = GemmaEmbeddingEngine(context)
 * val repository: EmbeddingRepository = AndroidEmbeddingEngine(platformEngine)
 * val memoryManager = MemoryManager(
 *     embeddingRepository = repository,
 *     vectorSearch = vectorSearchEngine,
 *     ...
 * )
 * ```
 */
class AndroidEmbeddingEngine(
    private val platformEngine: PlatformEmbeddingEngine
) : EmbeddingRepository {

    override val modelName: String
        get() = platformEngine.modelName

    override val embeddingDimensions: Int
        get() = platformEngine.embeddingDimensions

    override suspend fun loadModel(): Result<Unit> {
        return platformEngine.loadModel()
    }

    override suspend fun embed(text: String): Result<FloatArray> {
        return try {
            if (!platformEngine.isLoaded) {
                return Result.failure(
                    IllegalStateException("Embedding model not loaded. Call loadModel() first.")
                )
            }
            platformEngine.embed(text, EmbeddingTaskType.RETRIEVAL)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun embedBatch(texts: List<String>): Result<List<FloatArray>> {
        return try {
            if (!platformEngine.isLoaded) {
                return Result.failure(
                    IllegalStateException("Embedding model not loaded. Call loadModel() first.")
                )
            }
            platformEngine.embedBatch(texts, EmbeddingTaskType.RETRIEVAL)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        return platformEngine.cosineSimilarity(embedding1, embedding2)
    }
}
