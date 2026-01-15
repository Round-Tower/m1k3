package app.m1k3.ai.assistant.history

import androidx.compose.runtime.*
import app.m1k3.ai.assistant.eco.EcoCalculator
import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.eco.LifetimeStats
import app.m1k3.ai.assistant.eco.ProjectStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * EcoStatsViewModel - Manages eco-metrics statistics UI state
 *
 * **Philosophy:**
 * Transparency in environmental impact. EcoStatsViewModel provides users with
 * clear visibility into energy, water, and carbon savings from local AI inference
 * compared to cloud-based alternatives.
 *
 * **Features:**
 * - Load project-wide eco metrics
 * - Load conversation-specific eco metrics
 * - Calculate daily/weekly/monthly aggregates
 * - Compare against cloud AI baseline (100x multiplier)
 * - Track trends over time
 *
 * **Usage Example:**
 * ```kotlin
 * @Composable
 * fun EcoStatsScreen() {
 *     val ecoVM = rememberEcoStatsViewModel(repository, calculator)
 *     val state by ecoVM.collectAsState()
 *
 *     LaunchedEffect(Unit) {
 *         ecoVM.loadProjectStats("project_001")
 *     }
 *
 *     EcoMetricsCard(
 *         energySavedWh = state.totalMetrics?.totalEnergyWh ?: 0.0,
 *         waterSavedMl = state.totalMetrics?.totalWaterMl ?: 0.0,
 *         co2SavedGrams = state.totalMetrics?.totalCo2Grams ?: 0.0
 *     )
 * }
 * ```
 */
class EcoStatsViewModel(
    private val repository: EcoMetricsRepository,
    private val scope: CoroutineScope
) {
    // State flows
    private val _state = MutableStateFlow(EcoStatsState())
    val state: StateFlow<EcoStatsState> = _state.asStateFlow()

    // Current project ID
    private var currentProjectId: String? = null

    /**
     * Load eco-metrics for a project.
     *
     * Calculates total savings and comparison against cloud AI baseline.
     *
     * @param projectId Project to load metrics for
     */
    fun loadProjectStats(projectId: String) {
        currentProjectId = projectId
        _state.value = _state.value.copy(isLoading = true, error = null)

        scope.launch {
            try {
                // Get all project stats and find the requested project
                val allProjectStats = repository.getProjectStats()
                val projectMetrics = allProjectStats.find { it.projectId == projectId }

                // Calculate cloud AI baseline for comparison (100x multiplier)
                val cloudComparison = if (projectMetrics != null) {
                    EcoComparison(
                        localEnergyWh = projectMetrics.energyWh.toDouble(),
                        localWaterMl = projectMetrics.waterMl.toDouble(),
                        localCo2G = projectMetrics.co2G.toDouble(),
                        cloudEnergyWh = projectMetrics.energyWh.toDouble() * 100.0,
                        cloudWaterMl = projectMetrics.waterMl.toDouble() * 100.0,
                        cloudCo2G = projectMetrics.co2G.toDouble() * 100.0
                    )
                } else null

                _state.value = _state.value.copy(
                    projectMetrics = projectMetrics,
                    cloudComparison = cloudComparison,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load eco metrics: ${e.message}"
                )
            }
        }
    }

    /**
     * Load lifetime eco-metrics (all projects).
     */
    fun loadLifetimeStats() {
        _state.value = _state.value.copy(isLoading = true, error = null)

        scope.launch {
            try {
                val lifetimeStats = repository.getLifetimeStats()

                // Calculate cloud AI baseline for comparison (100x multiplier)
                val cloudComparison = if (lifetimeStats != null) {
                    EcoComparison(
                        localEnergyWh = lifetimeStats.totalEnergyWh.toDouble(),
                        localWaterMl = lifetimeStats.totalWaterMl.toDouble(),
                        localCo2G = lifetimeStats.totalCo2G.toDouble(),
                        cloudEnergyWh = lifetimeStats.totalEnergyWh.toDouble() * 100.0,
                        cloudWaterMl = lifetimeStats.totalWaterMl.toDouble() * 100.0,
                        cloudCo2G = lifetimeStats.totalCo2G.toDouble() * 100.0
                    )
                } else null

                _state.value = _state.value.copy(
                    lifetimeStats = lifetimeStats,
                    cloudComparison = cloudComparison,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load lifetime stats: ${e.message}"
                )
            }
        }
    }

    /**
     * Refresh project stats.
     *
     * Reloads metrics from database.
     */
    fun refreshStats() {
        currentProjectId?.let { projectId ->
            loadProjectStats(projectId)
        }
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Reset view model to initial state.
     */
    fun reset() {
        _state.value = EcoStatsState()
        currentProjectId = null
    }

    /**
     * Format energy for display (with appropriate unit).
     *
     * @param energyWh Energy in watt-hours
     * @return Formatted string (e.g., "1.2 kWh" or "500 Wh")
     */
    fun formatEnergy(energyWh: Double): String {
        return when {
            energyWh >= 1000.0 -> String.format("%.2f kWh", energyWh / 1000.0)
            else -> String.format("%.1f Wh", energyWh)
        }
    }

    /**
     * Format water for display (with appropriate unit).
     *
     * @param waterMl Water in milliliters
     * @return Formatted string (e.g., "2.5 L" or "500 ml")
     */
    fun formatWater(waterMl: Double): String {
        return when {
            waterMl >= 1000.0 -> String.format("%.2f L", waterMl / 1000.0)
            else -> String.format("%.0f ml", waterMl)
        }
    }

    /**
     * Format CO2 for display (with appropriate unit).
     *
     * @param co2Grams CO2 in grams
     * @return Formatted string (e.g., "1.5 kg" or "800 g")
     */
    fun formatCO2(co2Grams: Double): String {
        return when {
            co2Grams >= 1000.0 -> String.format("%.2f kg", co2Grams / 1000.0)
            else -> String.format("%.0f g", co2Grams)
        }
    }
}

/**
 * UI state for eco-metrics screen.
 */
data class EcoStatsState(
    val lifetimeStats: LifetimeStats? = null,
    val projectMetrics: ProjectStats? = null,
    val cloudComparison: EcoComparison? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Comparison between local AI and cloud AI environmental impact.
 */
data class EcoComparison(
    val localEnergyWh: Double,
    val localWaterMl: Double,
    val localCo2G: Double,
    val cloudEnergyWh: Double,
    val cloudWaterMl: Double,
    val cloudCo2G: Double
) {
    /**
     * Calculate savings percentages.
     */
    val energySavingsPercent: Double
        get() = ((cloudEnergyWh - localEnergyWh) / cloudEnergyWh) * 100.0

    val waterSavingsPercent: Double
        get() = ((cloudWaterMl - localWaterMl) / cloudWaterMl) * 100.0

    val co2SavingsPercent: Double
        get() = ((cloudCo2G - localCo2G) / cloudCo2G) * 100.0
}

/**
 * Create and remember eco stats view model.
 *
 * @param repository Eco-metrics repository
 * @return Eco stats view model scoped to composition
 */
@Composable
fun rememberEcoStatsViewModel(
    repository: EcoMetricsRepository
): EcoStatsViewModel {
    val scope = rememberCoroutineScope()
    return remember {
        EcoStatsViewModel(
            repository = repository,
            scope = scope
        )
    }
}

/**
 * Collect eco stats state as Compose State.
 *
 * @return Current eco stats state
 */
@Composable
fun EcoStatsViewModel.collectAsState(): State<EcoStatsState> {
    return state.collectAsState()
}
