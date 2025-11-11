package app.m1k3.ai.assistant.embedding

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Embedding Repository - Manage Embedding Engine Lifecycle
 *
 * Responsibilities:
 * - Thread-safe embedding engine initialization
 * - Singleton pattern (one ONNX model instance per app)
 * - Caching and lifecycle management
 * - Load status tracking for UI feedback
 *
 * Architecture:
 * ```
 * EmbeddingRepository → EmbeddingModelManager → MiniLmEmbeddingEngine (ONNX Runtime)
 * ```
 *
 * Thread Safety:
 * - Uses Mutex for initialization locking
 * - Multiple calls to initialize() are idempotent
 * - Safe to call from any coroutine/thread
 *
 * Usage:
 * ```kotlin
 * val repository = EmbeddingRepository.getInstance(context)
 *
 * // Initialize in background
 * scope.launch {
 *     repository.initialize().onSuccess {
 *         println("Embeddings ready!")
 *     }
 * }
 *
 * // Get cached engine
 * val engine = repository.getEngine()
 * ```
 */
interface EmbeddingRepository {
    /**
     * Initialize embedding engine asynchronously.
     *
     * Thread-safe, idempotent. Multiple calls return cached result.
     *
     * @return Result<EmbeddingEngine> with loaded engine or error
     */
    suspend fun initialize(): Result<EmbeddingEngine>

    /**
     * Get cached embedding engine.
     *
     * @return EmbeddingEngine if initialized, null otherwise
     */
    fun getEngine(): EmbeddingEngine?

    /**
     * Get current loading status for UI feedback.
     *
     * @return EmbeddingLoadStatus with state and timing info
     */
    fun getStatus(): EmbeddingLoadStatus

    /**
     * Release embedding engine resources.
     *
     * Called on app termination or memory pressure.
     */
    suspend fun release()

    /**
     * Check if engine is ready to use.
     *
     * @return true if engine is loaded and ready
     */
    fun isReady(): Boolean = getEngine()?.isLoaded == true
}

/**
 * Embedding load status for UI feedback
 */
data class EmbeddingLoadStatus(
    val state: LoadState,
    val loadTimeMs: Long? = null,
    val error: String? = null,
    val modelName: String? = null,
    val dimensions: Int? = null
)

/**
 * Loading state enum
 */
enum class LoadState {
    /** Not started */
    IDLE,

    /** Currently loading model */
    LOADING,

    /** Successfully loaded and ready */
    READY,

    /** Failed to load (see error message) */
    ERROR
}
