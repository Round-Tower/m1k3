package app.m1k3.ai.assistant.history

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * HistoryViewModel - Manages conversation history UI state
 *
 * **Philosophy:**
 * Separates UI state management from data layer, providing clean reactive
 * state updates for the conversation history screen.
 *
 * **Features:**
 * - Load conversations for a project
 * - Search across conversation history
 * - Delete conversations
 * - Export conversations (JSON/Markdown)
 * - Refresh conversation list
 *
 * **Usage Example:**
 * ```kotlin
 * @Composable
 * fun HistoryScreen() {
 *     val historyVM = rememberHistoryViewModel(database)
 *     val state by historyVM.collectAsState()
 *
 *     LaunchedEffect(Unit) {
 *         historyVM.loadConversations("project_001")
 *     }
 *
 *     SearchBar(
 *         query = state.searchQuery,
 *         onQueryChange = { historyVM.searchConversations(it) }
 *     )
 *
 *     ConversationList(
 *         conversations = state.conversations,
 *         onDelete = { historyVM.deleteConversation(it.id) },
 *         onExport = { historyVM.exportConversation(it.id, ExportFormat.JSON) }
 *     )
 * }
 * ```
 */
class HistoryViewModel(
    private val conversationRepository: ConversationRepository,
    private val searchRepository: SearchRepository,
    private val exportManager: ExportManager
) : ViewModel() {
    // State flows
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    // Current project ID
    private var currentProjectId: String? = null

    /**
     * Load all conversations for a project.
     *
     * @param projectId Project to load conversations from
     */
    fun loadConversations(projectId: String) {
        currentProjectId = projectId
        _state.value = _state.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val conversations = conversationRepository.getConversationsByProject(projectId)
                _state.value = _state.value.copy(
                    conversations = conversations,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load conversations: ${e.message}"
                )
            }
        }
    }

    /**
     * Search conversations by query.
     *
     * Performs keyword search across conversation history.
     *
     * @param query Search query
     */
    fun searchConversations(query: String) {
        _state.value = _state.value.copy(searchQuery = query)

        if (query.isBlank()) {
            // Clear search results when query is empty
            _state.value = _state.value.copy(searchResults = null)
            return
        }

        viewModelScope.launch {
            try {
                val results = searchRepository.searchMessages(
                    query = query,
                    projectId = currentProjectId,
                    limit = 50
                )
                _state.value = _state.value.copy(
                    searchResults = results,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Search failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Delete a conversation.
     *
     * Removes conversation and all its messages from the database.
     *
     * @param conversationId Conversation ID to delete
     */
    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            try {
                conversationRepository.deleteConversation(conversationId)

                // Refresh conversation list
                currentProjectId?.let { projectId ->
                    val conversations = conversationRepository.getConversationsByProject(projectId)
                    _state.value = _state.value.copy(
                        conversations = conversations,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to delete conversation: ${e.message}"
                )
            }
        }
    }

    /**
     * Export a conversation to JSON or Markdown.
     *
     * @param conversationId Conversation to export
     * @param format Export format (JSON or MARKDOWN)
     * @return Export string or null if conversation doesn't exist
     */
    fun exportConversation(conversationId: Long, format: ExportFormat): String? {
        return when (format) {
            ExportFormat.JSON -> exportManager.exportConversationToJson(conversationId)
            ExportFormat.MARKDOWN -> exportManager.exportConversationToMarkdown(conversationId)
        }
    }

    /**
     * Export entire project to JSON or Markdown.
     *
     * @param projectId Project to export
     * @param format Export format (JSON or MARKDOWN)
     * @return Export string with all conversations
     */
    fun exportProject(projectId: String, format: ExportFormat): String {
        return when (format) {
            ExportFormat.JSON -> exportManager.exportProjectToJson(projectId)
            ExportFormat.MARKDOWN -> exportManager.exportProjectToMarkdown(projectId)
        }
    }

    /**
     * Refresh conversation list.
     *
     * Reloads conversations from database.
     */
    fun refreshConversations() {
        currentProjectId?.let { projectId ->
            loadConversations(projectId)
        }
    }

    /**
     * Select a conversation for viewing.
     *
     * @param conversation Conversation to select
     */
    fun selectConversation(conversation: ConversationInfo?) {
        _state.value = _state.value.copy(selectedConversation = conversation)
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Clear search results and query.
     */
    fun clearSearch() {
        _state.value = _state.value.copy(
            searchQuery = "",
            searchResults = null
        )
    }

    /**
     * Reset view model to initial state.
     */
    fun reset() {
        _state.value = HistoryState()
        currentProjectId = null
    }
}

/**
 * UI state for conversation history screen.
 */
data class HistoryState(
    val conversations: List<ConversationInfo> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SearchResult>? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedConversation: ConversationInfo? = null
)

/**
 * Export format options.
 */
enum class ExportFormat {
    JSON,
    MARKDOWN
}

/**
 * Collect history state as Compose State.
 *
 * @return Current history state
 */
@Composable
fun HistoryViewModel.collectAsState(): State<HistoryState> {
    return state.collectAsState()
}
