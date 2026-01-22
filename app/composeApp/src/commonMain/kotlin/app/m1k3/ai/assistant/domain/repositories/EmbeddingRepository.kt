package app.m1k3.ai.assistant.domain.repositories

/**
 * EmbeddingRepository - Domain contract for text embedding generation
 *
 * **ARCHITECTURE NOTE:**
 * This is a DOMAIN-LAYER interface (Clean Architecture).
 * - Used by: Domain use cases (CreateMemoryUseCase)
 * - Implemented by: Platform-specific adapters
 *
 * NOT to be confused with application-layer `EmbeddingEngineManager` which
 * handles lifecycle management (initialization, caching, status, release).
 *
 * ```
 * Domain Layer:     EmbeddingRepository (this) ← Used by use cases
 *                           ↑
 * Application Layer: EmbeddingEngineManager   ← Lifecycle management
 *                           ↑
 * Platform Layer:    GemmaEmbeddingEngine    ← ONNX/Core ML inference
 * ```
 *
 * Pure Kotlin interface with no platform dependencies.
 * Defines operations for converting text to semantic vectors.
 *
 * **Philosophy:**
 * Text → Vector → Semantic Search. Embeddings enable semantic understanding.
 * Repository abstracts ML framework (ONNX, Core ML) from business logic.
 *
 * **Responsibilities:**
 * - Load embedding model (MiniLM, Gemma)
 * - Generate embeddings for single text
 * - Generate embeddings for batches (efficient)
 * - Calculate semantic similarity between embeddings
 *
 * **Platform Implementations:**
 * - Android: ONNX Runtime (MiniLM-L6-v2, EmbeddingGemma)
 * - iOS: Core ML (MiniLM .mlmodel, future)
 *
 * **Models:**
 * - MiniLM-L6-v2: 384 dimensions, fast, general-purpose
 * - EmbeddingGemma: 384 dimensions, Google's model, better quality
 *
 * **Usage:**
 * ```kotlin
 * val embeddingRepo: EmbeddingRepository = get() // Koin injection
 *
 * // Load model
 * embeddingRepo.loadModel().onSuccess {
 *     println("Embedding model loaded: ${embeddingRepo.modelName}")
 *     println("Dimensions: ${embeddingRepo.embeddingDimensions}")
 * }
 *
 * // Generate single embedding
 * embeddingRepo.embed("How does photosynthesis work?").onSuccess { vector ->
 *     println("Generated ${vector.size}-dimensional vector")
 * }
 *
 * // Batch generation (more efficient)
 * val texts = listOf("First text", "Second text", "Third text")
 * embeddingRepo.embedBatch(texts).onSuccess { vectors ->
 *     println("Generated ${vectors.size} embeddings")
 * }
 *
 * // Calculate similarity
 * val sim = embeddingRepo.cosineSimilarity(vector1, vector2)
 * println("Similarity: ${(sim * 100).toInt()}%")
 * ```
 */
interface EmbeddingRepository {
    /**
     * Model name (e.g., "MiniLM-L6-v2", "EmbeddingGemma")
     *
     * Useful for diagnostics and logging.
     */
    val modelName: String

    /**
     * Embedding vector dimensions
     *
     * All embeddings from this model will have this dimension.
     * - MiniLM-L6-v2: 384
     * - EmbeddingGemma: 384
     * - BGE-small: 384
     */
    val embeddingDimensions: Int

    /**
     * Load embedding model into memory
     *
     * Platform implementations should:
     * 1. Load ONNX/Core ML model from assets
     * 2. Initialize inference session
     * 3. Warm up model with dummy input
     *
     * @return Result.success if loaded, Result.failure with exception
     */
    suspend fun loadModel(): Result<Unit>

    /**
     * Generate embedding for single text
     *
     * Converts text to semantic vector using loaded model.
     * Text is preprocessed (tokenization, normalization) before inference.
     *
     * **Performance:**
     * - Short text (<50 words): ~20-50ms on mid-range Android
     * - Long text (>200 words): ~100-200ms
     *
     * @param text Input text to embed
     * @return Result.success(FloatArray) with embedding vector, or Result.failure
     */
    suspend fun embed(text: String): Result<FloatArray>

    /**
     * Generate embeddings for batch of texts
     *
     * More efficient than calling embed() multiple times.
     * Platform implementations can use batch inference for speedup.
     *
     * **Performance:**
     * - 10 texts: ~150-300ms (vs ~500ms sequential)
     * - Batch processing amortizes model loading overhead
     *
     * @param texts List of input texts to embed
     * @return Result.success(List<FloatArray>) with embedding vectors, or Result.failure
     */
    suspend fun embedBatch(texts: List<String>): Result<List<FloatArray>>

    /**
     * Calculate cosine similarity between two embeddings
     *
     * Cosine similarity measures angle between vectors (not magnitude).
     * Returns value in [-1, 1] where:
     * - 1.0 = identical meaning
     * - 0.0 = unrelated
     * - -1.0 = opposite meaning (rare)
     *
     * **Formula:** cos(θ) = (A · B) / (||A|| × ||B||)
     *
     * **Usage:**
     * ```kotlin
     * val sim = cosineSimilarity(queryEmbedding, memoryEmbedding)
     * if (sim >= 0.8f) {
     *     println("Highly relevant!")
     * } else if (sim >= 0.5f) {
     *     println("Somewhat relevant")
     * } else {
     *     println("Not relevant")
     * }
     * ```
     *
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return Cosine similarity score [-1, 1]
     */
    fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float
}
