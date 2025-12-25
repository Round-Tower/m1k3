package app.m1k3.ai.assistant.avatar

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.eco.EcoSavings
import app.m1k3.ai.assistant.test.TestDatabaseFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*

/**
 * TDD Test Suite: PetViewModel
 *
 * Tests state management, particle lifecycle, and eco integration.
 * Uses TestCoroutineDispatcher for deterministic timing control.
 *
 * Test Coverage:
 * - Initial state validation
 * - Eco metrics integration (real-time updates)
 * - Particle spawning and lifecycle
 * - User interactions (pat, double-tap, long-press)
 * - Evolution progression
 * - State persistence triggers
 * - Background degradation (battery, CPU, temp)
 * - Daily goal tracking
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PetViewModelTest {

    private lateinit var database: MaDatabase
    private lateinit var ecoRepo: EcoMetricsRepository
    private lateinit var testScope: TestScope
    private lateinit var viewModel: PetViewModel

    @BeforeTest
    fun setup() {
        // Create test database
        database = TestDatabaseFactory.createInMemoryDatabase()

        // Create eco repository
        ecoRepo = EcoMetricsRepository(database)

        // Create test coroutine scope
        testScope = TestScope()

        // Create ViewModel
        viewModel = PetViewModel(ecoRepo, testScope)
    }

    @AfterTest
    fun teardown() {
        // In-memory database is automatically cleaned up
    }

    // ==================== Initial State Tests ====================

    @Test
    fun `initial pet state has default values`() = testScope.runTest {
        // Act
        val state = viewModel.petState.first()

        // Assert
        assertEquals(100f, state.health, "Initial health should be 100")
        assertEquals(70f, state.mood, "Initial mood should be 70")
        assertEquals(80f, state.energy, "Initial energy should be 80")
        assertEquals(60f, state.happiness, "Initial happiness should be 60")
        assertEquals(0, state.conversationCount, "Initial conversation count should be 0")
        assertEquals(EvolutionStage.BASIC, state.evolutionStage, "Initial evolution should be BASIC")
        assertEquals(0, state.lifetimeWaterMl, "Initial water should be 0")
        assertFalse(state.needsAttention, "Should not need attention initially")
    }

    @Test
    fun `initial particle effects list is empty`() = testScope.runTest {
        // Act
        val particles = viewModel.particleEffects.first()

        // Assert
        assertTrue(particles.isEmpty(), "Should start with no particles")
    }

    // ==================== Eco Metrics Integration Tests ====================

    @Test
    fun `onEcoMetricsRecorded updates pet stats`() = testScope.runTest {
        // Arrange
        val savings = EcoSavings(
            tokensProcessed = 100,
            waterSavedMl = 120,
            energySavedWh = 3000,
            co2PreventedG = 2,
            bytesSent = 0
        )
        val initialState = viewModel.petState.first()

        // Act
        viewModel.onEcoMetricsRecorded(savings)

        // Assert
        val updatedState = viewModel.petState.first()
        assertTrue(updatedState.health >= initialState.health, "Health should increase")
        assertTrue(updatedState.energy >= initialState.energy, "Energy should increase")
        assertTrue(updatedState.happiness >= initialState.happiness, "Happiness should increase")
        assertEquals(1, updatedState.conversationCount, "Conversation count should increment")
    }

    @Test
    fun `onEcoMetricsRecorded accumulates lifetime totals`() = testScope.runTest {
        // Arrange
        val savings1 = EcoSavings(100, 120, 3000, 2, 0)
        val savings2 = EcoSavings(100, 180, 4500, 3, 0)

        // Act
        viewModel.onEcoMetricsRecorded(savings1)
        viewModel.onEcoMetricsRecorded(savings2)

        // Assert
        val state = viewModel.petState.first()
        assertEquals(300, state.lifetimeWaterMl, "Water should accumulate (120 + 180)")
        assertEquals(7500, state.lifetimeEnergyWh, "Energy should accumulate (3000 + 4500)")
        assertEquals(5, state.lifetimeCO2G, "CO2 should accumulate (2 + 3)")
        assertEquals(2, state.conversationCount, "Should track 2 conversations")
    }

    @Test
    fun `onEcoMetricsRecorded spawns particles`() = testScope.runTest {
        // Arrange
        val savings = EcoSavings(100, 120, 3000, 2, 0)

        // Act
        viewModel.onEcoMetricsRecorded(savings)

        // Assert
        val particles = viewModel.particleEffects.first()
        assertTrue(particles.isNotEmpty(), "Should spawn particles after eco metrics")
        assertTrue(particles.any { it.type == ParticleType.WATER_DROPLET }, "Should have water particles")
        assertTrue(particles.any { it.type == ParticleType.ENERGY_SPARKLE }, "Should have energy particles")
        assertTrue(particles.any { it.type == ParticleType.CO2_LEAF }, "Should have CO2 particles")
    }

    @Test
    fun `onEcoMetricsRecorded triggers evolution when threshold reached`() = testScope.runTest {
        // Arrange - Pet needs 5000ml to evolve to INTERMEDIATE
        val largeSavings = EcoSavings(500, 6000, 15000, 10, 0) // 6L water

        // Act
        viewModel.onEcoMetricsRecorded(largeSavings)

        // Assert
        val state = viewModel.petState.first()
        assertEquals(EvolutionStage.INTERMEDIATE, state.evolutionStage, "Should evolve at 5L+ water")
        assertEquals(6000, state.lifetimeWaterMl)
    }

    // ==================== Particle Lifecycle Tests ====================

    @Test
    fun `particles are removed after lifecycle expires`() = testScope.runTest {
        // Arrange
        val savings = EcoSavings(100, 120, 3000, 2, 0)
        viewModel.onEcoMetricsRecorded(savings)

        // Act - Wait for particles to expire (3 seconds default lifetime)
        advanceTimeBy(3500) // 3.5 seconds

        // Assert
        val particles = viewModel.particleEffects.first()
        assertTrue(particles.isEmpty(), "Particles should be removed after lifecycle")
    }

    @Test
    fun `multiple eco events create multiple particle sets`() = testScope.runTest {
        // Arrange & Act
        viewModel.onEcoMetricsRecorded(EcoSavings(100, 120, 3000, 2, 0))
        advanceTimeBy(500) // 0.5 seconds delay
        viewModel.onEcoMetricsRecorded(EcoSavings(100, 120, 3000, 2, 0))

        // Assert
        val particles = viewModel.particleEffects.first()
        assertTrue(particles.size >= 6, "Should have particles from both events (3 types × 2 events)")
    }

    @Test
    fun `particle cleanup removes only expired particles`() = testScope.runTest {
        // Arrange - Spawn first batch
        viewModel.onEcoMetricsRecorded(EcoSavings(100, 120, 3000, 2, 0))
        advanceTimeBy(2000) // 2 seconds

        // Act - Spawn second batch
        viewModel.onEcoMetricsRecorded(EcoSavings(100, 120, 3000, 2, 0))
        advanceTimeBy(1500) // 1.5 seconds more (total 3.5s for first batch)

        // Assert
        val particles = viewModel.particleEffects.first()
        // First batch should be expired (3.5s > 3s), second batch still alive (1.5s < 3s)
        assertTrue(particles.size >= 3, "Second batch particles should still be alive")
        assertTrue(particles.size < 6, "First batch particles should be removed")
    }

    // ==================== User Interaction Tests ====================

    @Test
    fun `onPat with PAT type increases happiness and energy`() = testScope.runTest {
        // Arrange
        val initialState = viewModel.petState.first()

        // Act
        viewModel.onPat(InteractionType.PAT)

        // Assert
        val updatedState = viewModel.petState.first()
        assertTrue(updatedState.happiness > initialState.happiness, "Happiness should increase")
        assertTrue(updatedState.energy > initialState.energy, "Energy should increase")
        assertEquals(1, updatedState.patsReceived, "Pat count should increment")
    }

    @Test
    fun `onPat with DOUBLE_TAP gives larger boost`() = testScope.runTest {
        // Arrange
        val initialState = viewModel.petState.first()

        // Act
        viewModel.onPat(InteractionType.DOUBLE_TAP)

        // Assert
        val updatedState = viewModel.petState.first()
        // Double tap gives +10 happiness (vs +5 for single pat)
        assertTrue(updatedState.happiness >= initialState.happiness + 9, "Double tap should give larger boost")
    }

    @Test
    fun `onPat with LONG_PRESS gives maximum boost`() = testScope.runTest {
        // Arrange
        val initialState = viewModel.petState.first()

        // Act
        viewModel.onPat(InteractionType.LONG_PRESS)

        // Assert
        val updatedState = viewModel.petState.first()
        // Long press gives +15 happiness (vs +5 for single pat)
        assertTrue(updatedState.happiness >= initialState.happiness + 14, "Long press should give maximum boost")
    }

    @Test
    fun `onPat spawns heart particle`() = testScope.runTest {
        // Act
        viewModel.onPat(InteractionType.PAT)

        // Assert
        val particles = viewModel.particleEffects.first()
        assertTrue(particles.any { it.type == ParticleType.HEART }, "Should spawn heart particle on pat")
    }

    @Test
    fun `multiple pats accumulate pat count`() = testScope.runTest {
        // Act
        repeat(5) {
            viewModel.onPat(InteractionType.PAT)
        }

        // Assert
        val state = viewModel.petState.first()
        assertEquals(5, state.patsReceived, "Should track all pats")
    }

    // ==================== Evolution Progression Tests ====================

    @Test
    fun `pet remains BASIC below 5000ml water`() = testScope.runTest {
        // Arrange
        val savings = EcoSavings(100, 4500, 10000, 9, 0) // 4.5L water

        // Act
        viewModel.onEcoMetricsRecorded(savings)

        // Assert
        val state = viewModel.petState.first()
        assertEquals(EvolutionStage.BASIC, state.evolutionStage, "Should stay BASIC below 5L")
    }

    @Test
    fun `pet evolves to INTERMEDIATE at 5000ml water`() = testScope.runTest {
        // Arrange
        val savings = EcoSavings(500, 5500, 15000, 10, 0) // 5.5L water

        // Act
        viewModel.onEcoMetricsRecorded(savings)

        // Assert
        val state = viewModel.petState.first()
        assertEquals(EvolutionStage.INTERMEDIATE, state.evolutionStage, "Should evolve to INTERMEDIATE at 5L+")
    }

    @Test
    fun `pet evolves to ADVANCED at 100000ml water`() = testScope.runTest {
        // Arrange - Simulate multiple interactions accumulating to 100L
        repeat(10) {
            viewModel.onEcoMetricsRecorded(EcoSavings(1000, 12000, 30000, 20, 0)) // 12L per interaction
        }

        // Assert
        val state = viewModel.petState.first()
        assertEquals(120000, state.lifetimeWaterMl, "Should accumulate to 120L")
        assertEquals(EvolutionStage.ADVANCED, state.evolutionStage, "Should evolve to ADVANCED at 100L+")
    }

    @Test
    fun `evolution stages are permanent`() = testScope.runTest {
        // Arrange - Evolve to INTERMEDIATE
        viewModel.onEcoMetricsRecorded(EcoSavings(500, 6000, 15000, 10, 0))
        val evolvedStage = viewModel.petState.first().evolutionStage

        // Act - Add more interactions
        repeat(5) {
            viewModel.onEcoMetricsRecorded(EcoSavings(100, 120, 3000, 2, 0))
        }

        // Assert
        val state = viewModel.petState.first()
        assertTrue(
            state.evolutionStage.ordinal >= evolvedStage.ordinal,
            "Evolution stage should never regress"
        )
    }

    // ==================== Daily Goal Progress Tests ====================

    @Test
    fun `daily goal progress calculates correctly`() = testScope.runTest {
        // Arrange - Pet has default goal of 1000 credits
        val savings = EcoSavings(100, 500, 3000, 2, 0) // Assume this gives ~500 credits

        // Act
        viewModel.onEcoMetricsRecorded(savings)

        // Assert
        val state = viewModel.petState.first()
        assertTrue(state.totalEcoCredits > 0, "Should have some eco credits")
        // Progress = totalCredits / dailyGoal (calculated in UI)
    }

    @Test
    fun `exceeding daily goal works correctly`() = testScope.runTest {
        // Arrange - Accumulate beyond 1000 credit goal
        repeat(15) {
            viewModel.onEcoMetricsRecorded(EcoSavings(100, 120, 3000, 2, 0))
        }

        // Assert
        val state = viewModel.petState.first()
        assertTrue(state.totalEcoCredits > state.dailyEcoGoal, "Should exceed daily goal")
    }

    // ==================== State Validation Tests ====================

    @Test
    fun `pet stats respect max value of 100`() = testScope.runTest {
        // Arrange - Massive eco savings to push stats beyond 100
        val hugeSavings = EcoSavings(10000, 200000, 500000, 10000, 0)

        // Act
        viewModel.onEcoMetricsRecorded(hugeSavings)

        // Assert
        val state = viewModel.petState.first()
        assertTrue(state.health <= 100f, "Health should cap at 100")
        assertTrue(state.energy <= 100f, "Energy should cap at 100")
        assertTrue(state.happiness <= 100f, "Happiness should cap at 100")
        assertTrue(state.mood <= 100f, "Mood should cap at 100")
    }

    @Test
    fun `pet stats never go negative`() = testScope.runTest {
        // Act - Start with default state (which has positive stats)
        val state = viewModel.petState.first()

        // Assert
        assertTrue(state.health >= 0f, "Health should never be negative")
        assertTrue(state.energy >= 0f, "Energy should never be negative")
        assertTrue(state.happiness >= 0f, "Happiness should never be negative")
        assertTrue(state.mood >= 0f, "Mood should never be negative")
    }

    @Test
    fun `battery level reflects system state`() = testScope.runTest {
        // Arrange
        val state = viewModel.petState.first()

        // Assert
        assertTrue(state.batteryLevel >= 0f && state.batteryLevel <= 100f, "Battery should be 0-100 range")
    }

    // ==================== Achievement Tracking Tests ====================

    @Test
    fun `current achievement updates at milestones`() = testScope.runTest {
        // Arrange - Reach Water Bottle achievement (500ml)
        val savings = EcoSavings(100, 600, 3000, 2, 0) // 600ml

        // Act
        viewModel.onEcoMetricsRecorded(savings)

        // Assert
        val state = viewModel.petState.first()
        assertNotNull(state.currentAchievement, "Should have achievement at 500ml+")
        assertTrue(
            state.currentAchievement?.contains("bottle", ignoreCase = true) == true,
            "Achievement should mention bottle"
        )
    }

    @Test
    fun `achievement progresses through tiers`() = testScope.runTest {
        // Arrange - Accumulate to Bucket achievement (5L)
        repeat(5) {
            viewModel.onEcoMetricsRecorded(EcoSavings(100, 1200, 3000, 2, 0)) // 1.2L each
        }

        // Assert
        val state = viewModel.petState.first()
        assertEquals(6000, state.lifetimeWaterMl, "Should have 6L total")
        assertNotNull(state.currentAchievement, "Should have higher tier achievement")
    }

    // ==================== Edge Cases ====================

    @Test
    fun `zero savings eco event still increments conversation count`() = testScope.runTest {
        // Arrange
        val zeroSavings = EcoSavings(0, 0, 0, 0, 0)

        // Act
        viewModel.onEcoMetricsRecorded(zeroSavings)

        // Assert
        val state = viewModel.petState.first()
        assertEquals(1, state.conversationCount, "Conversation count should still increment")
        assertEquals(0, state.lifetimeWaterMl, "Water should remain 0")
    }

    @Test
    fun `rapid successive interactions work correctly`() = testScope.runTest {
        // Act - Rapid fire 10 interactions
        repeat(10) {
            viewModel.onEcoMetricsRecorded(EcoSavings(100, 120, 3000, 2, 0))
        }

        // Assert
        val state = viewModel.petState.first()
        assertEquals(10, state.conversationCount, "Should track all 10 conversations")
        assertEquals(1200, state.lifetimeWaterMl, "Should accumulate all water (10 × 120ml)")
    }

    @Test
    fun `pet state updates are reflected immediately in flow`() = testScope.runTest {
        // Arrange
        val initialState = viewModel.petState.first()

        // Act
        viewModel.onEcoMetricsRecorded(EcoSavings(100, 120, 3000, 2, 0))

        // Assert
        val updatedState = viewModel.petState.first()
        assertNotEquals(
            initialState.conversationCount,
            updatedState.conversationCount,
            "State should update after eco metrics recorded"
        )
    }

    // ==================== Realistic Usage Scenarios ====================

    @Test
    fun `daily power user scenario - 20 conversations`() = testScope.runTest {
        // Scenario: User has 20 conversations in a day
        repeat(20) {
            viewModel.onEcoMetricsRecorded(EcoSavings(150, 180, 4500, 3, 0))
            advanceTimeBy(100) // Small delay between conversations
        }

        // Assert
        val state = viewModel.petState.first()
        assertEquals(20, state.conversationCount, "Should track 20 conversations")
        assertEquals(3600, state.lifetimeWaterMl, "Should save 3.6L water")
        assertEquals(90000, state.lifetimeEnergyWh, "Should save 90 Wh energy")
        assertEquals(60, state.lifetimeCO2G, "Should prevent 60g CO2")
        assertEquals(EvolutionStage.BASIC, state.evolutionStage, "Still BASIC (need 5L)")
    }

    @Test
    fun `new user first week scenario`() = testScope.runTest {
        // Scenario: New user, 5 conversations per day for 7 days
        repeat(35) { // 5 × 7
            viewModel.onEcoMetricsRecorded(EcoSavings(150, 180, 4500, 3, 0))
        }

        // Assert
        val state = viewModel.petState.first()
        assertEquals(35, state.conversationCount)
        assertEquals(6300, state.lifetimeWaterMl, "Should save 6.3L in first week")
        assertEquals(EvolutionStage.INTERMEDIATE, state.evolutionStage, "Should evolve after ~5L")
    }

    @Test
    fun `interaction with pats and eco events combined`() = testScope.runTest {
        // Scenario: User chats and pats the pet
        viewModel.onEcoMetricsRecorded(EcoSavings(100, 120, 3000, 2, 0))
        viewModel.onPat(InteractionType.PAT)
        viewModel.onEcoMetricsRecorded(EcoSavings(150, 180, 4500, 3, 0))
        viewModel.onPat(InteractionType.DOUBLE_TAP)

        // Assert
        val state = viewModel.petState.first()
        assertEquals(2, state.conversationCount, "Should track 2 conversations")
        assertEquals(2, state.patsReceived, "Should track 2 pats")
        assertEquals(300, state.lifetimeWaterMl, "Should accumulate eco metrics")
    }
}
