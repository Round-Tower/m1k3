package app.m1k3.ai.assistant.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.m1k3.ai.assistant.database.MaDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * InitializationViewModel.
 *
 * Signals the first-frame init handshake for MainActivity. The database is
 * created by Koin at module load; the ViewModel just emits the state so the
 * composable tree can render the right screen.
 *
 * History: knowledge seeding (TriviaFact) retired 2026-04-20. The `database`
 * parameter is still here so a future init step (e.g. schema warmup, passage
 * index rebuild) can consume it without another wiring change.
 */
class InitializationViewModel(
    @Suppress("unused") private val database: MaDatabase,
    private val databaseInitializer: IDatabaseInitializer,
) : ViewModel() {
    private val _state = MutableStateFlow<InitializationState>(InitializationState.NotStarted)
    val state: StateFlow<InitializationState> = _state.asStateFlow()

    fun initialize() {
        viewModelScope.launch {
            try {
                _state.value = InitializationState.Loading("Opening database…")

                when (val dbResult = databaseInitializer.initializeDatabase()) {
                    is DatabaseInitResult.Success -> {
                        _state.value = InitializationState.Success
                    }

                    is DatabaseInitResult.Error -> {
                        _state.value = InitializationState.Error(dbResult.message)
                    }
                }
            } catch (e: Exception) {
                _state.value =
                    InitializationState.Error(
                        message = "Initialization failed: ${e.message ?: "Unknown error"}",
                    )
            }
        }
    }

    fun retry() {
        _state.value = InitializationState.NotStarted
        initialize()
    }
}

/**
 * Sealed class representing initialization state.
 */
sealed class InitializationState {
    data object NotStarted : InitializationState()

    data class Loading(
        val message: String,
    ) : InitializationState()

    data object Success : InitializationState()

    data class Error(
        val message: String,
    ) : InitializationState()
}
