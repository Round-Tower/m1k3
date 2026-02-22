package app.m1k3.ai.assistant.embedding

/**
 * Embedding Engine Interface - Semantic Text Embeddings
 *
 * **ARCHITECTURE NOTE:**
 * This is a PLATFORM-LAYER interface for actual embedding engines.
 *
 * ```
 * Domain Layer:     domain.repositories.EmbeddingRepository ← Use cases
 *                           ↑
 * Application Layer: EmbeddingEngineManager                ← Lifecycle
 *                           ↑
 * Platform Layer:    EmbeddingEngine (this)               ← ML inference
 *                           ↑
 * Implementation:    GemmaEmbeddingEngine                 ← ONNX Runtime
 * ```
 *
 * Provides text-to-vector conversion for semantic search and RAG.
 * Supports multiple embedding models (Gemma, MiniLM, etc.)
 *
 * Technical specs:
 * - Model: ONNX quantized for mobile inference
 * - Dimensions: 384 (EmbeddingGemma)
 * - Context: 2048 tokens max
 * - Quantization: INT8 for speed/size balance
 *
 * Privacy: 100% on-device, no network required
 */
interface EmbeddingEngine {
    /**
     * Model information
     */
    val modelName: String
    val embeddingDimensions: Int
    val maxTokens: Int
    val isLoaded: Boolean

    /**
     * Load embedding model into memory
     *
     * @return Result indicating success or error
     */
    suspend fun loadModel(): Result<Unit>

    /**
     * Unload model and free resources
     */
    suspend fun unloadModel()

    /**
     * Generate embedding vector for text
     *
     * @param text Input text to embed
     * @param taskType Optional task-specific prompt (search, retrieval, classification)
     * @return Embedding vector (normalized)
     */
    suspend fun embed(text: String, taskType: EmbeddingTaskType = EmbeddingTaskType.RETRIEVAL): Result<FloatArray>

    /**
     * Generate embeddings for multiple texts (batch processing)
     *
     * @param texts List of input texts
     * @param taskType Task-specific prompt
     * @return List of embedding vectors
     */
    suspend fun embedBatch(texts: List<String>, taskType: EmbeddingTaskType = EmbeddingTaskType.RETRIEVAL): Result<List<FloatArray>>

    /**
     * Compute cosine similarity between two embeddings
     *
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return Similarity score (-1.0 to 1.0, higher = more similar)
     */
    fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) { "Embeddings must have same dimensions" }

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        return if (norm1 > 0f && norm2 > 0f) {
            dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))
        } else {
            0f
        }
    }

    /**
     * Normalize embedding vector (L2 normalization)
     *
     * @param embedding Input embedding
     * @return Normalized embedding
     */
    fun normalize(embedding: FloatArray): FloatArray {
        var norm = 0f
        for (value in embedding) {
            norm += value * value
        }
        norm = kotlin.math.sqrt(norm)

        return if (norm > 0f) {
            FloatArray(embedding.size) { i -> embedding[i] / norm }
        } else {
            embedding
        }
    }
}

/**
 * Embedding task types for task-specific prompts.
 *
 * @deprecated Use app.m1k3.ai.domain.embedding.EmbeddingTaskType instead.
 * This typealias exists for backward compatibility.
 */
typealias EmbeddingTaskType = app.m1k3.ai.domain.embedding.EmbeddingTaskType

/**
 * Embedding result with metadata.
 *
 * @deprecated Use app.m1k3.ai.domain.embedding.EmbeddingResult instead.
 * This typealias exists for backward compatibility.
 */
typealias EmbeddingResult = app.m1k3.ai.domain.embedding.EmbeddingResult
