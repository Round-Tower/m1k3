package app.m1k3.ai.assistant.avatar

import app.m1k3.ai.assistant.eco.EcoSavings
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * TDD Test Suite: PetEcoIntegration
 *
 * Tests the integration between eco metrics and pixel pet vitals.
 * Validates conversion formulas, achievement mapping, and session boosts.
 *
 * Test Coverage:
 * - Water → Health conversion (baseline + scaling)
 * - Energy → Energy conversion (baseline + scaling)
 * - CO2 → Happiness conversion (baseline + scaling)
 * - Achievement-based evolution mapping
 * - Session boost calculations (real-time feedback)
 * - Daily goal calculations
 * - Combined stat calculations
 * - Edge cases (0 values, negative values, overflow protection)
 */
class PetEcoIntegrationTest {

    // ==================== Water → Health Conversion Tests ====================

    @Test
    fun `calculateHealthFromWater returns baseline health for 0ml water`() {
        // Arrange
        val waterMl = 0L
        val baseHealth = 50f

        // Act
        val health = PetEcoIntegration.calculateHealthFromWater(waterMl, baseHealth)

        // Assert
        assertEquals(50f, health, "Health should equal baseline when no water saved")
    }

    @Test
    fun `calculateHealthFromWater adds 0_1 point per 100ml water`() {
        // Arrange - Formula: 100ml water = 0.1 health point
        val waterMl = 500L // 500ml
        val baseHealth = 50f

        // Act
        val health = PetEcoIntegration.calculateHealthFromWater(waterMl, baseHealth)

        // Assert
        assertEquals(50.5f, health, 0.01f, "500ml should add 0.5 health (500 / 100 * 0.1)")
    }

    @Test
    fun `calculateHealthFromWater scales linearly for 5000ml water`() {
        // Arrange
        val waterMl = 5000L // 5L (Bucket achievement)
        val baseHealth = 50f

        // Act
        val health = PetEcoIntegration.calculateHealthFromWater(waterMl, baseHealth)

        // Assert
        assertEquals(55f, health, 0.01f, "5000ml should add 5 health points")
    }

    @Test
    fun `calculateHealthFromWater caps at 100 health max`() {
        // Arrange
        val waterMl = 1000000L // 1,000,000ml (massive water savings)
        val baseHealth = 50f

        // Act
        val health = PetEcoIntegration.calculateHealthFromWater(waterMl, baseHealth)

        // Assert
        assertEquals(100f, health, "Health should cap at 100 regardless of water amount")
    }

    @Test
    fun `calculateHealthFromWater with 100000ml water gives significant boost`() {
        // Arrange
        val waterMl = 100000L // 100L (Bathtub achievement)
        val baseHealth = 50f

        // Act
        val health = PetEcoIntegration.calculateHealthFromWater(waterMl, baseHealth)

        // Assert
        assertEquals(100f, health, "100L water should max out health")
    }

    // ==================== Energy → Energy Conversion Tests ====================

    @Test
    fun `calculateEnergyFromPower returns baseline energy for 0 Wh`() {
        // Arrange
        val energyWh = 0L
        val baseEnergy = 50f

        // Act
        val energy = PetEcoIntegration.calculateEnergyFromPower(energyWh, baseEnergy)

        // Assert
        assertEquals(50f, energy, "Energy should equal baseline when no power saved")
    }

    @Test
    fun `calculateEnergyFromPower adds 0_15 point per 10 Wh`() {
        // Arrange - Formula: 10 Wh = 0.15 energy point
        val energyWh = 100L // 100 Wh
        val baseEnergy = 50f

        // Act
        val energy = PetEcoIntegration.calculateEnergyFromPower(energyWh, baseEnergy)

        // Assert
        assertEquals(51.5f, energy, 0.01f, "100 Wh should add 1.5 energy (100 / 10 * 0.15)")
    }

    @Test
    fun `calculateEnergyFromPower scales linearly for 1000 Wh`() {
        // Arrange
        val energyWh = 1000L // 1 kWh
        val baseEnergy = 50f

        // Act
        val energy = PetEcoIntegration.calculateEnergyFromPower(energyWh, baseEnergy)

        // Assert
        assertEquals(65f, energy, 0.01f, "1000 Wh should add 15 energy points")
    }

    @Test
    fun `calculateEnergyFromPower caps at 100 energy max`() {
        // Arrange
        val energyWh = 10000L // 10 kWh (massive energy savings)
        val baseEnergy = 50f

        // Act
        val energy = PetEcoIntegration.calculateEnergyFromPower(energyWh, baseEnergy)

        // Assert
        assertEquals(100f, energy, "Energy should cap at 100 regardless of power amount")
    }

    // ==================== CO2 → Happiness Conversion Tests ====================

    @Test
    fun `calculateHappinessFromCO2 returns baseline happiness for 0g CO2`() {
        // Arrange
        val co2G = 0L
        val baseHappiness = 50f

        // Act
        val happiness = PetEcoIntegration.calculateHappinessFromCO2(co2G, baseHappiness)

        // Assert
        assertEquals(50f, happiness, "Happiness should equal baseline when no CO2 prevented")
    }

    @Test
    fun `calculateHappinessFromCO2 adds 0_2 point per 20g CO2`() {
        // Arrange - Formula: 20g CO2 = 0.2 happiness point
        val co2G = 100L // 100g CO2
        val baseHappiness = 50f

        // Act
        val happiness = PetEcoIntegration.calculateHappinessFromCO2(co2G, baseHappiness)

        // Assert
        assertEquals(51f, happiness, 0.01f, "100g CO2 should add 1 happiness (100 / 20 * 0.2)")
    }

    @Test
    fun `calculateHappinessFromCO2 scales linearly for 1000g CO2`() {
        // Arrange
        val co2G = 1000L // 1kg CO2
        val baseHappiness = 50f

        // Act
        val happiness = PetEcoIntegration.calculateHappinessFromCO2(co2G, baseHappiness)

        // Assert
        assertEquals(60f, happiness, 0.01f, "1000g CO2 should add 10 happiness points")
    }

    @Test
    fun `calculateHappinessFromCO2 caps at 100 happiness max`() {
        // Arrange
        val co2G = 100000L // 100kg CO2 (massive CO2 prevention)
        val baseHappiness = 50f

        // Act
        val happiness = PetEcoIntegration.calculateHappinessFromCO2(co2G, baseHappiness)

        // Assert
        assertEquals(100f, happiness, "Happiness should cap at 100 regardless of CO2 amount")
    }

    // ==================== Achievement → Evolution Mapping Tests ====================

    @Test
    fun `getEvolutionStageFromWater returns BASIC for 0-499ml`() {
        // Arrange & Act & Assert
        assertEquals(EvolutionStage.BASIC, PetEcoIntegration.getEvolutionStageFromWater(0))
        assertEquals(EvolutionStage.BASIC, PetEcoIntegration.getEvolutionStageFromWater(499))
    }

    @Test
    fun `getEvolutionStageFromWater returns BASIC for 500-4999ml`() {
        // Arrange - Water Bottle achievement (500ml)
        val waterMl = 500L

        // Act
        val stage = PetEcoIntegration.getEvolutionStageFromWater(waterMl)

        // Assert
        assertEquals(EvolutionStage.BASIC, stage, "500ml unlocks Water Bottle but stays BASIC")
    }

    @Test
    fun `getEvolutionStageFromWater returns INTERMEDIATE for 5000ml`() {
        // Arrange - Bucket achievement (5L)
        val waterMl = 5000L

        // Act
        val stage = PetEcoIntegration.getEvolutionStageFromWater(waterMl)

        // Assert
        assertEquals(EvolutionStage.INTERMEDIATE, stage, "5L (Bucket) unlocks INTERMEDIATE")
    }

    @Test
    fun `getEvolutionStageFromWater returns ADVANCED for 100000ml`() {
        // Arrange - Bathtub achievement (100L)
        val waterMl = 100000L

        // Act
        val stage = PetEcoIntegration.getEvolutionStageFromWater(waterMl)

        // Assert
        assertEquals(EvolutionStage.ADVANCED, stage, "100L (Bathtub) unlocks ADVANCED")
    }

    @Test
    fun `getEvolutionStageFromWater returns EXPERT for 1000000ml`() {
        // Arrange - Swimming Pool achievement (1000L)
        val waterMl = 1000000L

        // Act
        val stage = PetEcoIntegration.getEvolutionStageFromWater(waterMl)

        // Assert
        assertEquals(EvolutionStage.EXPERT, stage, "1000L (Pool) unlocks EXPERT")
    }

    @Test
    fun `getEvolutionStageFromWater returns LEGENDARY for 2500000ml`() {
        // Arrange - Olympic Pool achievement (2500L)
        val waterMl = 2500000L

        // Act
        val stage = PetEcoIntegration.getEvolutionStageFromWater(waterMl)

        // Assert
        assertEquals(EvolutionStage.LEGENDARY, stage, "2500L (Olympic Pool) unlocks LEGENDARY")
    }

    @Test
    fun `getEvolutionStageFromWater stays LEGENDARY above 2500000ml`() {
        // Arrange
        val waterMl = 10000000L // 10,000L

        // Act
        val stage = PetEcoIntegration.getEvolutionStageFromWater(waterMl)

        // Assert
        assertEquals(EvolutionStage.LEGENDARY, stage, "Max evolution is LEGENDARY")
    }

    // ==================== Session Boost Tests (Real-Time Feedback) ====================

    @Test
    fun `applySessionBoost adds health boost from water saved`() {
        // Arrange
        val initialState = PixelPetState(health = 70f)
        val savings = EcoSavings(
            tokensProcessed = 100,
            waterSavedMl = 120, // Baseline for 100 tokens
            energySavedWh = 3000,
            co2PreventedG = 2,
            bytesSent = 0
        )

        // Act
        val boostedState = PetEcoIntegration.applySessionBoost(initialState, savings)

        // Assert
        assertTrue(boostedState.health > initialState.health, "Health should increase from water boost")
        assertEquals(70.12f, boostedState.health, 0.01f, "120ml should add 0.12 health")
    }

    @Test
    fun `applySessionBoost adds energy boost from power saved`() {
        // Arrange
        val initialState = PixelPetState(energy = 60f)
        val savings = EcoSavings(
            tokensProcessed = 100,
            waterSavedMl = 120,
            energySavedWh = 3000, // 3 Wh = 0.045 energy points
            co2PreventedG = 2,
            bytesSent = 0
        )

        // Act
        val boostedState = PetEcoIntegration.applySessionBoost(initialState, savings)

        // Assert
        assertTrue(boostedState.energy > initialState.energy, "Energy should increase from power boost")
        assertEquals(60.45f, boostedState.energy, 0.01f, "3 Wh should add 0.45 energy")
    }

    @Test
    fun `applySessionBoost adds happiness boost from CO2 prevented`() {
        // Arrange
        val initialState = PixelPetState(happiness = 55f)
        val savings = EcoSavings(
            tokensProcessed = 100,
            waterSavedMl = 120,
            energySavedWh = 3000,
            co2PreventedG = 2, // 2g = 0.02 happiness points
            bytesSent = 0
        )

        // Act
        val boostedState = PetEcoIntegration.applySessionBoost(initialState, savings)

        // Assert
        assertTrue(boostedState.happiness > initialState.happiness, "Happiness should increase from CO2 boost")
        assertEquals(55.2f, boostedState.happiness, 0.01f, "2g CO2 should add 0.2 happiness")
    }

    @Test
    fun `applySessionBoost respects max values (100 cap)`() {
        // Arrange - Pet already near max
        val initialState = PixelPetState(health = 99.9f, energy = 99.9f, happiness = 99.9f)
        val savings = EcoSavings(
            tokensProcessed = 1000,
            waterSavedMl = 1200,
            energySavedWh = 30000,
            co2PreventedG = 20,
            bytesSent = 0
        )

        // Act
        val boostedState = PetEcoIntegration.applySessionBoost(initialState, savings)

        // Assert
        assertEquals(100f, boostedState.health, "Health should cap at 100")
        assertEquals(100f, boostedState.energy, "Energy should cap at 100")
        assertEquals(100f, boostedState.happiness, "Happiness should cap at 100")
    }

    @Test
    fun `applySessionBoost increments conversation count`() {
        // Arrange
        val initialState = PixelPetState(conversationCount = 10)
        val savings = EcoSavings(100, 120, 3000, 2, 0)

        // Act
        val boostedState = PetEcoIntegration.applySessionBoost(initialState, savings)

        // Assert
        assertEquals(11, boostedState.conversationCount, "Conversation count should increment")
    }

    @Test
    fun `applySessionBoost updates lifetime eco totals`() {
        // Arrange
        val initialState = PixelPetState(
            lifetimeWaterMl = 1000,
            lifetimeEnergyWh = 5000,
            lifetimeCO2G = 10
        )
        val savings = EcoSavings(100, 120, 3000, 2, 0)

        // Act
        val boostedState = PetEcoIntegration.applySessionBoost(initialState, savings)

        // Assert
        assertEquals(1120, boostedState.lifetimeWaterMl, "Lifetime water should accumulate")
        assertEquals(8000, boostedState.lifetimeEnergyWh, "Lifetime energy should accumulate")
        assertEquals(12, boostedState.lifetimeCO2G, "Lifetime CO2 should accumulate")
    }

    @Test
    fun `applySessionBoost triggers evolution when threshold reached`() {
        // Arrange - Pet has 4800ml (just below Bucket threshold)
        val initialState = PixelPetState(
            lifetimeWaterMl = 4800,
            evolutionStage = EvolutionStage.BASIC
        )
        val savings = EcoSavings(100, 300, 3000, 2, 0) // +300ml pushes to 5100ml

        // Act
        val boostedState = PetEcoIntegration.applySessionBoost(initialState, savings)

        // Assert
        assertEquals(5100, boostedState.lifetimeWaterMl)
        assertEquals(EvolutionStage.INTERMEDIATE, boostedState.evolutionStage, "Should evolve to INTERMEDIATE")
    }

    // ==================== Daily Goal Calculation Tests ====================

    @Test
    fun `calculateDailyGoalProgress returns 0 percent for 0 credits`() {
        // Arrange
        val currentCredits = 0L
        val dailyGoal = 1000

        // Act
        val progress = PetEcoIntegration.calculateDailyGoalProgress(currentCredits, dailyGoal)

        // Assert
        assertEquals(0f, progress, "Progress should be 0% with no credits")
    }

    @Test
    fun `calculateDailyGoalProgress returns 50 percent for half goal`() {
        // Arrange
        val currentCredits = 500L
        val dailyGoal = 1000

        // Act
        val progress = PetEcoIntegration.calculateDailyGoalProgress(currentCredits, dailyGoal)

        // Assert
        assertEquals(0.5f, progress, 0.01f, "Progress should be 50% at half goal")
    }

    @Test
    fun `calculateDailyGoalProgress returns 100 percent at goal`() {
        // Arrange
        val currentCredits = 1000L
        val dailyGoal = 1000

        // Act
        val progress = PetEcoIntegration.calculateDailyGoalProgress(currentCredits, dailyGoal)

        // Assert
        assertEquals(1f, progress, 0.01f, "Progress should be 100% at goal")
    }

    @Test
    fun `calculateDailyGoalProgress caps at 100 percent above goal`() {
        // Arrange
        val currentCredits = 2000L
        val dailyGoal = 1000

        // Act
        val progress = PetEcoIntegration.calculateDailyGoalProgress(currentCredits, dailyGoal)

        // Assert
        assertEquals(1f, progress, "Progress should cap at 100% above goal")
    }

    // ==================== Combined Stats Calculation Tests ====================

    @Test
    fun `calculateCombinedStats integrates all eco metrics`() {
        // Arrange
        val waterMl = 5000L // INTERMEDIATE evolution
        val energyWh = 1000L
        val co2G = 1000L

        // Act
        val stats = PetEcoIntegration.calculateCombinedStats(waterMl, energyWh, co2G)

        // Assert
        assertEquals(55f, stats.health, 0.01f, "Health from 5000ml water")
        assertEquals(65f, stats.energy, 0.01f, "Energy from 1000 Wh")
        assertEquals(60f, stats.happiness, 0.01f, "Happiness from 1000g CO2")
        assertEquals(EvolutionStage.INTERMEDIATE, stats.evolutionStage)
    }

    @Test
    fun `calculateCombinedStats with massive eco values caps at 100`() {
        // Arrange
        val waterMl = 1000000L
        val energyWh = 100000L
        val co2G = 100000L

        // Act
        val stats = PetEcoIntegration.calculateCombinedStats(waterMl, energyWh, co2G)

        // Assert
        assertEquals(100f, stats.health, "Health should cap at 100")
        assertEquals(100f, stats.energy, "Energy should cap at 100")
        assertEquals(100f, stats.happiness, "Happiness should cap at 100")
        assertEquals(EvolutionStage.EXPERT, stats.evolutionStage)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `applySessionBoost handles 0 token session gracefully`() {
        // Arrange
        val initialState = PixelPetState()
        val savings = EcoSavings(0, 0, 0, 0, 0)

        // Act
        val boostedState = PetEcoIntegration.applySessionBoost(initialState, savings)

        // Assert
        assertEquals(initialState.health, boostedState.health, "Health unchanged for 0 tokens")
        assertEquals(initialState.energy, boostedState.energy, "Energy unchanged for 0 tokens")
        assertEquals(initialState.happiness, boostedState.happiness, "Happiness unchanged for 0 tokens")
        assertEquals(1, boostedState.conversationCount, "Conversation count still increments")
    }

    @Test
    fun `calculateHealthFromWater handles negative baseline gracefully`() {
        // Arrange - Edge case: negative baseline (shouldn't happen but test defensive code)
        val waterMl = 1000L
        val baseHealth = -10f

        // Act
        val health = PetEcoIntegration.calculateHealthFromWater(waterMl, baseHealth)

        // Assert
        assertTrue(health >= 0f, "Health should never be negative")
    }

    // ==================== Realistic Usage Scenarios ====================

    @Test
    fun `new user first interaction scenario`() {
        // Scenario: Brand new pixel pet, first AI interaction (100 tokens)
        // Arrange
        val newPetState = PixelPetState() // Default values
        val firstInteraction = EcoSavings(100, 120, 3000, 2, 0)

        // Act
        val afterFirstChat = PetEcoIntegration.applySessionBoost(newPetState, firstInteraction)

        // Assert
        assertEquals(1, afterFirstChat.conversationCount, "First conversation")
        assertEquals(120, afterFirstChat.lifetimeWaterMl, "First water savings")
        assertEquals(EvolutionStage.BASIC, afterFirstChat.evolutionStage, "Still BASIC (need 500ml)")
        assertTrue(afterFirstChat.health > newPetState.health, "Health improved slightly")
    }

    @Test
    fun `daily power user scenario - 20 interactions`() {
        // Scenario: User has 20 conversations in a day (150 tokens each = 3000 total)
        // Arrange
        var petState = PixelPetState()

        // Act - Simulate 20 interactions
        repeat(20) {
            val savings = EcoSavings(150, 180, 4500, 3, 0)
            petState = PetEcoIntegration.applySessionBoost(petState, savings)
        }

        // Assert
        assertEquals(20, petState.conversationCount, "20 conversations tracked")
        assertEquals(3600, petState.lifetimeWaterMl, "3.6L water saved")
        assertEquals(90000, petState.lifetimeEnergyWh, "90 Wh energy saved")
        assertEquals(60, petState.lifetimeCO2G, "60g CO2 prevented")
        assertEquals(EvolutionStage.BASIC, petState.evolutionStage, "Still BASIC (need 5L for INTERMEDIATE)")
    }

    @Test
    fun `evolution unlocking scenario - gradual progression`() {
        // Scenario: User progresses through evolution stages over time
        // Arrange
        var petState = PixelPetState()

        // Act - Simulate reaching Bucket achievement (5L = INTERMEDIATE)
        val largeInteraction = EcoSavings(500, 6000, 15000, 10, 0) // 6L water in one go
        petState = PetEcoIntegration.applySessionBoost(petState, largeInteraction)

        // Assert
        assertEquals(6000, petState.lifetimeWaterMl)
        assertEquals(EvolutionStage.INTERMEDIATE, petState.evolutionStage, "Evolved to INTERMEDIATE at 5L+")
        assertTrue(petState.health > 55f, "Health significantly boosted")
    }
}
