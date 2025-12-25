package app.m1k3.ai.assistant.embedding

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Android implementation of EmbeddingRepository
 *
 * Wraps EmbeddingModelManager with proper lifecycle and state management.
 *
 * Thread Safety:
 * - Uses Mutex for initialization locking
 * - @Volatile for status visibility across threads
 * - All suspend functions run on Dispatchers.IO
 *
 * Performance:
 * - Caches engine after first load (singleton pattern)
 * - Model load time: 2-5 seconds (ONNX Runtime initialization)
 * - Memory footprint: ~18MB (MiniLM-L6) or ~180MB (Gemma)
 *
 * Usage:
 * ```kotlin
 * val repository = EmbeddingRepositoryImpl(context)
 *
 * // Initialize async
 * scope.launch {
 *     repository.initialize().onSuccess { engine ->
 *         println("Loaded: ${engine.modelName}")
 *     }
 * }
 * ```
 */
class EmbeddingRepositoryImpl(
    private val context: Context
) : EmbeddingRepository {

    companion object {
        private const val TAG = "EmbeddingRepository"

        @Volatile
        private var INSTANCE: EmbeddingRepositoryImpl? = null

        /**
         * Get singleton instance (application-scoped)
         */
        fun getInstance(context: Context): EmbeddingRepositoryImpl {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EmbeddingRepositoryImpl(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val modelManager = EmbeddingModelManager(context)
    private val initMutex = Mutex()

    @Volatile
    private var cachedEngine: EmbeddingEngine? = null

    @Volatile
    private var currentStatus = EmbeddingLoadStatus(LoadState.IDLE)

    /**
     * Initialize embedding engine with thread-safe singleton pattern.
     *
     * Flow:
     * 1. Check if already loaded (fast path)
     * 2. Acquire mutex lock
     * 3. Double-check loading (another coroutine may have loaded)
     * 4. Load model on IO dispatcher
     * 5. Cache engine and update status
     * 6. Release lock
     *
     * @return Result<EmbeddingEngine> with loaded engine or error
     */
    override suspend fun initialize(): Result<EmbeddingEngine> = withContext(Dispatchers.IO) {
        // Fast path: already loaded
        cachedEngine?.let {
            if (it.isLoaded) {
                return@withContext Result.success(it)
            }
        }

        // Slow path: acquire lock and load
        initMutex.withLock {
            // Double-check: another coroutine may have loaded while we waited
            cachedEngine?.let {
                if (it.isLoaded) {
                    return@withContext Result.success(it)
                }
            }

            try {
                currentStatus = EmbeddingLoadStatus(LoadState.LOADING)
                println("🔄 [$TAG] Initializing embedding engine...")

                val startTime = System.currentTimeMillis()

                // Get engine from manager (automatically selects MiniLM or Gemma)
                val engine = modelManager.getEmbeddingEngine()

                // Load ONNX model (this is the slow part: 2-5 seconds)
                val loadResult = engine.loadModel()

                if (loadResult.isSuccess) {
                    val loadTime = System.currentTimeMillis() - startTime

                    cachedEngine = engine
                    currentStatus = EmbeddingLoadStatus(
                        state = LoadState.READY,
                        loadTimeMs = loadTime,
                        modelName = engine.modelName,
                        dimensions = engine.embeddingDimensions
                    )

                    println("✅ [$TAG] Loaded ${engine.modelName} in ${loadTime}ms (${engine.embeddingDimensions}-dim)")
                    Result.success(engine)
                } else {
                    val error = loadResult.exceptionOrNull()?.message ?: "Unknown error"
                    currentStatus = EmbeddingLoadStatus(
                        state = LoadState.ERROR,
                        error = error
                    )

                    println("❌ [$TAG] Failed to load embedding model: $error")
                    Result.failure(loadResult.exceptionOrNull() ?: Exception(error))
                }
            } catch (e: Exception) {
                currentStatus = EmbeddingLoadStatus(
                    state = LoadState.ERROR,
                    error = e.message ?: "Initialization failed"
                )

                println("❌ [$TAG] Initialization exception: ${e.message}")
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Get cached embedding engine (null if not initialized).
     *
     * Thread-safe: uses @Volatile for visibility.
     *
     * @return EmbeddingEngine if loaded, null otherwise
     */
    override fun getEngine(): EmbeddingEngine? = cachedEngine

    /**
     * Get current loading status.
     *
     * Thread-safe: uses @Volatile for visibility.
     *
     * @return EmbeddingLoadStatus with state and timing
     */
    override fun getStatus(): EmbeddingLoadStatus = currentStatus

    /**
     * Release embedding engine resources.
     *
     * Unloads ONNX model and frees memory.
     * Thread-safe: uses mutex lock.
     */
    override suspend fun release(): Unit = withContext(Dispatchers.IO) {
        initMutex.withLock {
            cachedEngine?.let { engine ->
                println("🔄 [$TAG] Releasing ${engine.modelName}...")
                engine.unloadModel()
                cachedEngine = null
                currentStatus = EmbeddingLoadStatus(LoadState.IDLE)
                println("✅ [$TAG] Resources released")
            }
        }
    }

    /**
     * Check if engine is ready to use.
     *
     * @return true if engine is loaded and ready
     */
    override fun isReady(): Boolean {
        return cachedEngine?.isLoaded == true &&
                currentStatus.state == LoadState.READY
    }
}
