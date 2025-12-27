package app.m1k3.ai.assistant.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.m1k3.ai.assistant.database.MaDatabase

/**
 * Remember a HistoryViewModel scoped to the composition.
 *
 * This composable creates and remembers a HistoryViewModel with all
 * required dependencies properly initialized from the database.
 *
 * **Usage:**
 * ```kotlin
 * val viewModel = rememberHistoryViewModel(
 *     database = database,
 *     projectId = "default"
 * )
 *
 * val state by viewModel.collectAsState()
 * ```
 *
 * @param database The database for persistence
 * @param projectId Project ID for scoping conversations
 * @return HistoryViewModel instance
 */
@Composable
fun rememberHistoryViewModel(
    database: MaDatabase,
    projectId: String
): HistoryViewModel {
    val scope = rememberCoroutineScope()

    return remember(projectId, database) {
        createHistoryViewModel(
            database = database,
            scope = scope
        )
    }
}

/**
 * Create a HistoryViewModel with all dependencies.
 *
 * This is the factory function that wires up all dependencies.
 */
private fun createHistoryViewModel(
    database: MaDatabase,
    scope: kotlinx.coroutines.CoroutineScope
): HistoryViewModel {
    // Create repositories from database
    val conversationRepo = ConversationRepository(database)
    val searchRepo = SearchRepository(database)
    val exportManager = ExportManager(database)

    return HistoryViewModel(
        conversationRepository = conversationRepo,
        searchRepository = searchRepo,
        exportManager = exportManager,
        scope = scope
    )
}
