package app.m1k3.ai.assistant.history

import app.m1k3.ai.assistant.eco.EcoComparison
import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.eco.EcoStatsViewModel
import app.m1k3.ai.assistant.test.TestDatabaseFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.*

/**
 * EcoStatsViewModel Tests
 *
 * Basic sanity checks for EcoStatsViewModel state management and formatting.
 * Since all underlying repositories are extensively tested
 * these tests focus on simple state verification and utility functions.
 */
class EcoStatsViewModelTest {

    @Test
    fun `initial state is empty`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val repository = EcoMetricsRepository(database)
        
        val viewModel = EcoStatsViewModel(
            repository = repository

        )

        // Assert
        val state = viewModel.state.value
        assertNull(state.lifetimeStats, "Should start with no lifetime stats")
        assertNull(state.projectMetrics, "Should start with no project metrics")
        assertNull(state.cloudComparison, "Should start with no cloud comparison")
        assertFalse(state.isLoading, "Should not be loading initially")
        assertNull(state.error, "Should have no error initially")
    }

    @Test
    fun `formatEnergy handles small values`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val repository = EcoMetricsRepository(database)
        
        val viewModel = EcoStatsViewModel(
            repository = repository

        )

        // Act
        val formatted = viewModel.formatEnergy(500.0)

        // Assert
        assertEquals("500.0 Wh", formatted, "Should format small energy values in Wh")
    }

    @Test
    fun `formatEnergy handles large values`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val repository = EcoMetricsRepository(database)
        
        val viewModel = EcoStatsViewModel(
            repository = repository

        )

        // Act
        val formatted = viewModel.formatEnergy(2500.0)

        // Assert
        assertEquals("2.50 kWh", formatted, "Should format large energy values in kWh")
    }

    @Test
    fun `formatWater handles small values`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val repository = EcoMetricsRepository(database)
        
        val viewModel = EcoStatsViewModel(
            repository = repository

        )

        // Act
        val formatted = viewModel.formatWater(500.0)

        // Assert
        assertEquals("500 ml", formatted, "Should format small water values in ml")
    }

    @Test
    fun `formatWater handles large values`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val repository = EcoMetricsRepository(database)
        
        val viewModel = EcoStatsViewModel(
            repository = repository

        )

        // Act
        val formatted = viewModel.formatWater(2500.0)

        // Assert
        assertEquals("2.50 L", formatted, "Should format large water values in L")
    }

    @Test
    fun `formatCO2 handles small values`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val repository = EcoMetricsRepository(database)
        
        val viewModel = EcoStatsViewModel(
            repository = repository

        )

        // Act
        val formatted = viewModel.formatCO2(800.0)

        // Assert
        assertEquals("800 g", formatted, "Should format small CO2 values in g")
    }

    @Test
    fun `formatCO2 handles large values`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val repository = EcoMetricsRepository(database)
        
        val viewModel = EcoStatsViewModel(
            repository = repository

        )

        // Act
        val formatted = viewModel.formatCO2(1500.0)

        // Assert
        assertEquals("1.50 kg", formatted, "Should format large CO2 values in kg")
    }

    @Test
    fun `clearError resets error state`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val repository = EcoMetricsRepository(database)
        
        val viewModel = EcoStatsViewModel(
            repository = repository

        )

        // Act
        viewModel.clearError()

        // Assert
        assertNull(viewModel.state.value.error, "Should clear error")
    }

    @Test
    fun `reset clears all state`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val repository = EcoMetricsRepository(database)
        
        val viewModel = EcoStatsViewModel(
            repository = repository

        )

        // Act
        viewModel.reset()

        // Assert
        val state = viewModel.state.value
        assertNull(state.lifetimeStats, "Should clear lifetime stats")
        assertNull(state.projectMetrics, "Should clear project metrics")
        assertNull(state.cloudComparison, "Should clear cloud comparison")
        assertFalse(state.isLoading, "Should not be loading")
        assertNull(state.error, "Should clear error")
    }

    @Test
    fun `EcoComparison calculates savings percentages`() {
        // Arrange
        val comparison = EcoComparison(
            localEnergyWh = 10.0,
            localWaterMl = 5.0,
            localCo2G = 3.0,
            cloudEnergyWh = 1000.0,
            cloudWaterMl = 500.0,
            cloudCo2G = 300.0
        )

        // Assert
        assertEquals(99.0, comparison.energySavingsPercent, 0.1, "Should calculate 99% energy savings")
        assertEquals(99.0, comparison.waterSavingsPercent, 0.1, "Should calculate 99% water savings")
        assertEquals(99.0, comparison.co2SavingsPercent, 0.1, "Should calculate 99% CO2 savings")
    }
}
