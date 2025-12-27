package app.m1k3.ai.assistant.eco

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.history.EcoStatsViewModel

/**
 * Remember an EcoStatsViewModel scoped to the composition.
 *
 * This composable creates and remembers an EcoStatsViewModel with all
 * required dependencies properly initialized from the database.
 *
 * **Usage:**
 * ```kotlin
 * val viewModel = rememberEcoStatsViewModel(database = database)
 *
 * val state by viewModel.collectAsState()
 * ```
 *
 * @param database The database for persistence
 * @return EcoStatsViewModel instance
 */
@Composable
fun rememberEcoStatsViewModel(database: MaDatabase): EcoStatsViewModel {
    val scope = rememberCoroutineScope()

    return remember(database) {
        val repository = EcoMetricsRepository(database)
        EcoStatsViewModel(
            repository = repository,
            scope = scope
        )
    }
}
