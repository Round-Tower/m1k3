package app.m1k3.ai.assistant.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.m1k3.ai.assistant.database.MaDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * InitializationViewModel
 *
 * Manages app initialization state for database and knowledge setup.
 * Emits StateFlow events for reactive UI observation.
 *
 * **Architecture:**
 * - Sealed InitializationState for type-safe state management
 * - Database injected from Koin (created by DatabaseFactory)
 * - Injected AndroidDatabaseInitializer for knowledge import
 * - viewModelScope for automatic lifecycle management
 * - Retry support for error recovery
 *
 * **Usage:**
 * ```kotlin
 * val viewModel = koinViewModel<InitializationViewModel>()
 * val state by viewModel.state.collectAsState()
 *
 * LaunchedEffect(Unit) {
 *     viewModel.initialize()
 * }
 *
 * when (state) {
 *     is InitializationState.Loading -> LoadingScreen()
 *     is InitializationState.Success -> MaAppContent(state.knowledgeStatus)
 *     is InitializationState.Error -> ErrorScreen(onRetry = viewModel::retry)
 *     else -> {}
 * }
 * ```
 */
class InitializationViewModel(
    private val database: MaDatabase,
    private val databaseInitializer: IDatabaseInitializer
) : ViewModel() {

    private val _state = MutableStateFlow<InitializationState>(InitializationState.NotStarted)
    val state: StateFlow<InitializationState> = _state.asStateFlow()

    /**
     * Initialize knowledge base
     *
     * Database is already created by Koin. This just imports knowledge.
     *
     * Emits state transitions:
     * 1. Loading (with progress message)
     * 2. Success (with knowledge status) OR
     * 3. Error (with error message)
     */
    fun initialize() {
        viewModelScope.launch {
            try {
                _state.value = InitializationState.Loading("Loading knowledge base...")

                // Import knowledge (optional - don't fail if it errors)
                val knowledgeResult = databaseInitializer.importKnowledge(database)
                val knowledgeStatus = when (knowledgeResult) {
                    is KnowledgeImportResult.Success -> {
                        "✅ Knowledge ready: ${knowledgeResult.totalDocs} curated documents"
                    }
                    is KnowledgeImportResult.AlreadyImported -> {
                        "✅ Knowledge ready: ${knowledgeResult.existingDocs} documents"
                    }
                    is KnowledgeImportResult.Error -> {
                        "⚠️ Knowledge unavailable: ${knowledgeResult.message}"
                    }
                }

                _state.value = InitializationState.Success(
                    knowledgeStatus = knowledgeStatus
                )
            } catch (e: Exception) {
                _state.value = InitializationState.Error(
                    message = "Initialization failed: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    /**
     * Retry initialization after error
     *
     * Resets state to NotStarted and calls initialize() again.
     */
    fun retry() {
        _state.value = InitializationState.NotStarted
        initialize()
    }
}

/**
 * Sealed class representing initialization state
 *
 * Type-safe state management for initialization flow.
 */
sealed class InitializationState {
    /**
     * Initial state - no initialization attempted
     */
    data object NotStarted : InitializationState()

    /**
     * Loading state with progress message
     *
     * @param message Current initialization step (e.g., "Initializing database...")
     */
    data class Loading(val message: String) : InitializationState()

    /**
     * Success state with knowledge loaded
     *
     * Database is available via Koin injection.
     *
     * @param knowledgeStatus Human-readable knowledge import status
     */
    data class Success(
        val knowledgeStatus: String
    ) : InitializationState()

    /**
     * Error state with failure message
     *
     * @param message Error description for user
     */
    data class Error(val message: String) : InitializationState()
}
