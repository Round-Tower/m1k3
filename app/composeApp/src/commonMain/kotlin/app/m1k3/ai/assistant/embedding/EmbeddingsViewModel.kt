package app.m1k3.ai.assistant.embedding

import app.m1k3.ai.assistant.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Embeddings ViewModel - UI State Management for Embedding Engine
 *
 * Responsibilities:
 * - Expose embedding engine state to UI (loading, ready, error)
 * - Manage initialization lifecycle
 * - Provide clean API for UI components
 * - Handle multi-source search (future: knowledge + eco + device context)
 *
 * Architecture:
 * ```
 * ChatScreen → EmbeddingsViewModel → EmbeddingEngineManager → EmbeddingEngine
 * ```
 *
 * State Management:
 * - Uses StateFlow for reactive UI updates
 * - Automatically initializes on first access
 * - Survives configuration changes (scoped to activity/viewmodel)
 *
 * Usage:
 * ```kotlin
 * @Composable
 * fun ChatScreen() {
 *     val embeddingsVM = rememberEmbeddingsViewModel()
 *     val state by embeddingsVM.state.collectAsState()
 *
 *     when (state.loadStatus.state) {
 *         LoadState.LOADING -> Text("Loading embeddings...")
 *         LoadState.READY -> Text("✅ Ready (${state.loadStatus.loadTimeMs}ms)")
 *         LoadState.ERROR -> Text("❌ ${state.loadStatus.error}")
 *         LoadState.IDLE -> Text("⚪ Not initialized")
 *     }
 *
 *     // Use engine
 *     val engine = embeddingsVM.getEngine()
 *     engine?.embed("Hello world")
 * }
 * ```
 */
class EmbeddingsViewModel(
    private val engineManager: EmbeddingEngineManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "EmbeddingsViewModel"
    }

    private val logger = Logger.withTag(TAG)

    // Observable state for UI
    private val _state = MutableStateFlow(EmbeddingState())
    val state: StateFlow<EmbeddingState> = _state.asStateFlow()

    init {
        // Auto-initialize on creation
        initialize()
    }

    /**
     * Initialize embeddings in background.
     *
     * Called automatically in init block. Safe to call multiple times (idempotent).
     * Updates state as: IDLE → LOADING → READY/ERROR
     */
    fun initialize() {
        // Don't re-initialize if already ready
        if (_state.value.loadStatus.state == LoadState.READY) {
            logger.i { "Already initialized, skipping" }
            return
        }

        scope.launch {
            // Update to loading state
            _state.value = _state.value.copy(
                loadStatus = engineManager.getStatus().copy(state = LoadState.LOADING)
            )

            logger.i { "Initializing..." }

            // Initialize engine manager (thread-safe, idempotent)
            engineManager.initialize()
                .onSuccess { engine ->
                    val status = engineManager.getStatus()
                    _state.value = EmbeddingState(
                        loadStatus = status,
                        modelName = engine.modelName,
                        modelSize = when (engine.modelName.lowercase()) {
                            "minilm-l6-v2" -> "18MB"
                            "gemma-embedding" -> "180MB"
                            else -> "Unknown"
                        },
                        dimensions = engine.embeddingDimensions
                    )
                    logger.i { "Ready: ${engine.modelName} (${status.loadTimeMs}ms)" }
                }
                .onFailure { error ->
                    val status = engineManager.getStatus()
                    _state.value = _state.value.copy(loadStatus = status)
                    logger.e(error) { "Initialization failed: ${error.message}" }
                }
        }
    }

    /**
     * Get embedding engine (null if not ready).
     *
     * Safe to call from any thread.
     *
     * @return EmbeddingEngine if loaded, null otherwise
     */
    fun getEngine(): EmbeddingEngine? = engineManager.getEngine()

    /**
     * Check if embeddings are ready to use.
     *
     * @return true if engine is loaded and ready
     */
    fun isReady(): Boolean = engineManager.isReady()

    /**
     * Search across embedding sources (Phase 3b/3c - future expansion).
     *
     * Currently supports:
     * - KNOWLEDGE: RAG knowledge base (1,401 documents)
     *
     * Future sources (Phase 3b/3c):
     * - ECO_CREDITS: Eco savings history, achievements, trends
     * - DEVICE_CONTEXT: Health, wellbeing, location data
     *
     * @param query Natural language search query
     * @param sources Which embedding sources to search
     * @param topK Number of results to return
     * @return List of search results with similarity scores
     */
    suspend fun search(
        query: String,
        sources: Set<EmbeddingSource> = setOf(EmbeddingSource.KNOWLEDGE),
        topK: Int = 5
    ): List<EmbeddingSearchResult> {
        val engine = getEngine() ?: run {
            logger.w { "Search called but engine not ready" }
            return emptyList()
        }

        // Phase 3a: Only knowledge base supported
        // Phase 3b/3c: Add eco credits and device context
        if (sources != setOf(EmbeddingSource.KNOWLEDGE)) {
            logger.w { "Only KNOWLEDGE source supported in Phase 3a" }
        }

        // TODO Phase 3b/3c: Implement multi-source search
        // For now, return empty list (RAGManager handles knowledge base search)
        return emptyList()
    }

    /**
     * Release resources (called on ViewModel clear).
     */
    fun release() {
        scope.launch {
            engineManager.release()
            _state.value = EmbeddingState()
            logger.i { "Released" }
        }
    }
}

/**
 * Embedding state for UI display
 */
data class EmbeddingState(
    val loadStatus: EmbeddingLoadStatus = EmbeddingLoadStatus(LoadState.IDLE),
    val modelName: String = "MiniLM-L6-v2",
    val modelSize: String = "18MB",
    val dimensions: Int = 384
)

/**
 * Embedding source types (multi-source architecture)
 */
enum class EmbeddingSource {
    /** Knowledge base facts (Phase 3a - current) */
    KNOWLEDGE,

    /** Eco credits, savings, achievements (Phase 3b - future) */
    ECO_CREDITS,

    /** Health, wellbeing, location (Phase 3c - future) */
    DEVICE_CONTEXT
}

/**
 * Search result from embedding lookup
 */
data class EmbeddingSearchResult(
    val source: EmbeddingSource,
    val content: String,
    val similarity: Float,
    val metadata: Map<String, Any> = emptyMap()
)
