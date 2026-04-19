package app.m1k3.ai.assistant.passages

import app.m1k3.ai.domain.passages.Source
import app.m1k3.ai.domain.passages.SourceKind
import app.m1k3.ai.domain.passages.repositories.PassageRepository
import app.m1k3.ai.domain.passages.usecases.ImportTextUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * DocumentsViewModel — state for the Documents screen (list + import + delete).
 *
 * Pure Kotlin. Takes a [CoroutineScope] rather than owning one so tests can
 * inject a [kotlinx.coroutines.test.TestScope] and Compose hosts can supply
 * the composition scope.
 */
class DocumentsViewModel(
    private val importTextUseCase: ImportTextUseCase,
    private val repository: PassageRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(DocumentsUiState())
    val state: StateFlow<DocumentsUiState> = _state.asStateFlow()

    /**
     * Load sources from storage. Safe to call multiple times (refresh).
     */
    fun load() {
        scope.launch { refresh() }
    }

    /**
     * Import raw text as a new [Source].
     *
     * Blank title falls back to the first non-blank line of content.
     */
    fun import(
        title: String,
        content: String,
        kind: SourceKind,
    ) {
        scope.launch {
            val effectiveTitle =
                title.trim().ifBlank {
                    content.lineSequence().firstOrNull { it.isNotBlank() }?.take(60) ?: "Untitled note"
                }
            val uri = "note:${effectiveTitle.hashCode().toUInt()}"
            importTextUseCase
                .execute(effectiveTitle, content, kind, uri)
                .onSuccess { refresh() }
                .onFailure { err ->
                    _state.update { it.copy(errorMessage = err.message ?: "Import failed") }
                }
        }
    }

    /**
     * Delete a source and cascade its passages.
     */
    fun delete(sourceId: String) {
        scope.launch {
            repository.deleteSource(sourceId).onSuccess { refresh() }
        }
    }

    /**
     * Clear a transient error so the UI can dismiss its surface.
     */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private suspend fun refresh() {
        _state.update { it.copy(isLoading = true) }
        val sources = repository.getAllSources()
        _state.update { it.copy(sources = sources, isLoading = false) }
    }
}

/**
 * UI state for [DocumentsViewModel].
 */
data class DocumentsUiState(
    val sources: List<Source> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && sources.isEmpty()
}
