package app.m1k3.ai.assistant.avatar

import app.m1k3.ai.assistant.eco.EcoCalculator
import app.m1k3.ai.assistant.eco.EcoSavings
import kotlin.math.min

/**
 * 間 AI Pet-Eco Integration System
 *
 * Converts eco credits (water, energy, CO2) into pixel pet vital statistics.
 * Implements the hybrid feeding system:
 * - Daily eco credits maintain vitals
 * - Achievement tiers unlock evolution stages
 * - Session responses provide immediate stat boosts
 *
 * Philosophy: Environmental consciousness translates to pet wellbeing.
 * The more eco-friendly the AI usage, the healthier and happier the pet.
 */
object PetEcoIntegration {

    // === ECO → STAT CONVERSION FORMULAS ===

    /**
     * Convert water saved (ml) to health points
     *
     * Formula: health = baseHealth + (waterMl / 100) * 0.1
     * - 100ml water = 0.1 health point
     * - 1000ml (1L) water = 1.0 health point
     * - 10,000ml (10L) water = 10 health points
     *
     * Health caps at 100
     */
    fun calculateHealthFromWater(
        waterMl: Long,
        baseHealth: Float = 50f
    ): Float {
        val waterBonus = (waterMl.toFloat() / 100f) * 0.1f
        return min(baseHealth + waterBonus, 100f)
    }

    /**
     * Convert energy saved (Wh) to energy points
     *
     * Formula: energy = baseEnergy + (energyWh / 10) * 0.15
     * - 10 Wh energy = 0.15 energy point
     * - 100 Wh energy = 1.5 energy points
     * - 1000 Wh energy = 15 energy points
     *
     * Energy caps at 100
     */
    fun calculateEnergyFromPower(
        energyWh: Long,
        baseEnergy: Float = 50f
    ): Float {
        val energyBonus = (energyWh.toFloat() / 10f) * 0.15f
        return min(baseEnergy + energyBonus, 100f)
    }

    /**
     * Convert CO2 prevented (g) to happiness points
     *
     * Formula: happiness = baseHappiness + (co2G / 20) * 0.2
     * - 20g CO2 = 0.2 happiness point
     * - 100g CO2 = 1.0 happiness point
     * - 1000g CO2 = 10 happiness points
     *
     * Happiness caps at 100
     */
    fun calculateHappinessFromCO2(
        co2G: Long,
        baseHappiness: Float = 50f
    ): Float {
        val happinessBonus = (co2G.toFloat() / 20f) * 0.2f
        return min(baseHappiness + happinessBonus, 100f)
    }

    /**
     * Calculate mood from overall eco performance
     *
     * Mood is derived from a balanced eco profile:
     * - All three metrics contributing = high mood
     * - Imbalanced usage = lower mood
     *
     * Formula: mood = avg(waterRatio, energyRatio, co2Ratio) * 100
     */
    fun calculateMoodFromEcoBalance(
        waterMl: Long,
        energyWh: Long,
        co2G: Long,
        totalTokens: Long
    ): Float {
        if (totalTokens == 0L) return 50f

        // Calculate expected eco metrics for token count
        val expectedWater = (totalTokens / 100f) * 120f  // 120ml per 100 tokens
        val expectedEnergy = (totalTokens / 100f) * 3f   // 3 Wh per 100 tokens
        val expectedCO2 = (totalTokens / 100f) * 2f      // 2g per 100 tokens

        // Calculate ratios (how close to expected)
        val waterRatio = min(waterMl.toFloat() / expectedWater, 1f)
        val energyRatio = min(energyWh.toFloat() / expectedEnergy, 1f)
        val co2Ratio = min(co2G.toFloat() / expectedCO2, 1f)

        // Average the ratios
        val balanceScore = (waterRatio + energyRatio + co2Ratio) / 3f

        return min(balanceScore * 100f, 100f)
    }

    // === TOTAL ECO CREDITS CALCULATION ===

    /**
     * Calculate total eco credits (weighted sum)
     *
     * Water is base unit (1:1)
     * Energy is 10x multiplier (reflects higher value)
     * CO2 is 5x multiplier (environmental impact)
     *
     * Example: 500ml water + 10 Wh + 5g CO2 = 500 + 100 + 25 = 625 credits
     */
    fun getTotalEcoCredits(
        waterMl: Long,
        energyWh: Long,
        co2G: Long
    ): Long {
        return waterMl + (energyWh * 10L) + (co2G * 5L)
    }

    // === ACHIEVEMENT → EVOLUTION MAPPING ===

    /**
     * Map eco achievement to evolution stage
     *
     * Achievement tiers determine evolution:
     * - No achievement (0-499ml): EGG → BASIC
     * - Water Bottle (500ml): BASIC → INTERMEDIATE
     * - Bucket (5L): INTERMEDIATE → ADVANCED
     * - Bathtub (100L): ADVANCED → EXPERT
     * - Pool (1000L): EXPERT → LEGENDARY
     * - Olympic Pool (2500L): LEGENDARY (max)
     */
    fun getEvolutionStageFromWater(waterMl: Long): EvolutionStage {
        return when {
            waterMl >= 2_500_000 -> EvolutionStage.LEGENDARY  // Olympic Pool (2500L)
            waterMl >= 1_000_000 -> EvolutionStage.EXPERT     // Pool (1000L)
            waterMl >= 100_000 -> EvolutionStage.ADVANCED     // Bathtub (100L)
            waterMl >= 5_000 -> EvolutionStage.INTERMEDIATE   // Bucket (5L)
            waterMl >= 500 -> EvolutionStage.BASIC            // Water Bottle (500ml)
            else -> EvolutionStage.BASIC                       // Starting stage
        }
    }

    /**
     * Get achievement name from eco achievement
     */
    fun getAchievementName(waterMl: Long): String? {
        val achievement = EcoCalculator.getAchievement(waterMl.toInt())
        return achievement?.title
    }

    // === SESSION-BASED IMMEDIATE BOOSTS ===

    /**
     * Calculate immediate stat boosts from a single AI response
     *
     * This provides real-time feedback: each AI response "feeds" the pet
     * with a small boost proportional to the eco savings.
     *
     * Returns (healthBoost, energyBoost, happinessBoost)
     */
    fun calculateSessionBoosts(savings: EcoSavings): Triple<Float, Float, Float> {
        // Small immediate boosts (0.1-2.0 points per response)
        val healthBoost = min((savings.waterSavedMl / 100f) * 0.1f, 2f)
        val energyBoost = min((savings.energySavedWh / 1000f) * 0.15f, 2f)
        val happinessBoost = min((savings.co2PreventedG / 10f) * 0.2f, 2f)

        return Triple(healthBoost, energyBoost, happinessBoost)
    }

    // === PARTICLE COUNTS FOR VISUAL FEEDBACK ===

    /**
     * Calculate number of water droplet particles to spawn
     *
     * 10ml water = 1 droplet, capped at 50 droplets max
     */
    fun getWaterDropletCount(waterMl: Int): Int {
        return min(waterMl / 10, 50)
    }

    /**
     * Calculate number of energy sparkle particles to spawn
     *
     * 1 Wh = 1 sparkle, capped at 30 sparkles max
     */
    fun getEnergySparkleCount(energyWh: Int): Int {
        return min(energyWh / 1000, 30)
    }

    /**
     * Calculate number of CO2 leaf particles to spawn
     *
     * 2g CO2 = 1 leaf, capped at 20 leaves max
     */
    fun getCO2LeafCount(co2G: Int): Int {
        return min(co2G / 2, 20)
    }

    // === COMPREHENSIVE PET STATE UPDATE ===

    /**
     * Update entire pet state from lifetime eco metrics
     *
     * This is the primary integration point: takes current pet state
     * and lifetime eco metrics, returns updated state with all vitals
     * recalculated from eco data.
     *
     * Used for:
     * - Initial load from database
     * - Periodic recalculation (every 5 minutes)
     * - Manual refresh
     */
    fun updatePetStateFromEco(
        currentState: PixelPetState,
        lifetimeWaterMl: Long,
        lifetimeEnergyWh: Long,
        lifetimeCO2G: Long,
        totalTokens: Long
    ): PixelPetState {
        // Calculate new vitals from eco metrics
        val newHealth = calculateHealthFromWater(lifetimeWaterMl, baseHealth = 50f)
        val newEnergy = calculateEnergyFromPower(lifetimeEnergyWh, baseEnergy = 50f)
        val newHappiness = calculateHappinessFromCO2(lifetimeCO2G, baseHappiness = 50f)
        val newMood = calculateMoodFromEcoBalance(
            lifetimeWaterMl,
            lifetimeEnergyWh,
            lifetimeCO2G,
            totalTokens
        )

        // Determine evolution stage from water (primary metric)
        val newEvolutionStage = getEvolutionStageFromWater(lifetimeWaterMl)
        val newVisualTheme = VisualTheme.fromEvolutionStage(newEvolutionStage)
        val newEnvironment = Environment.fromEvolutionStage(newEvolutionStage)

        // Calculate total eco credits
        val newTotalCredits = getTotalEcoCredits(lifetimeWaterMl, lifetimeEnergyWh, lifetimeCO2G)

        // Get current achievement
        val achievementName = getAchievementName(lifetimeWaterMl)

        return currentState.copy(
            health = newHealth,
            energy = newEnergy,
            happiness = newHappiness,
            mood = newMood,
            evolutionStage = newEvolutionStage,
            visualTheme = newVisualTheme,
            environment = newEnvironment,
            totalEcoCredits = newTotalCredits,
            lifetimeWaterMl = lifetimeWaterMl,
            lifetimeEnergyWh = lifetimeEnergyWh,
            lifetimeCO2G = lifetimeCO2G,
            currentAchievement = achievementName
        )
    }

    /**
     * Apply session boost to current state
     *
     * This adds a small immediate boost when an AI response is generated.
     * Boosts are capped to prevent exceeding 100.
     *
     * Used for:
     * - Real-time feedback after each AI response
     * - Visual particle animation trigger
     */
    fun applySessionBoost(
        currentState: PixelPetState,
        savings: EcoSavings
    ): PixelPetState {
        val (healthBoost, energyBoost, happinessBoost) = calculateSessionBoosts(savings)

        // Calculate new lifetime totals
        val newLifetimeWaterMl = currentState.lifetimeWaterMl + savings.waterSavedMl
        val newLifetimeEnergyWh = currentState.lifetimeEnergyWh + savings.energySavedWh
        val newLifetimeCO2G = currentState.lifetimeCO2G + savings.co2PreventedG

        return currentState.copy(
            health = min(currentState.health + healthBoost, 100f),
            energy = min(currentState.energy + energyBoost, 100f),
            happiness = min(currentState.happiness + happinessBoost, 100f),
            // Update lifetime metrics
            lifetimeWaterMl = newLifetimeWaterMl,
            lifetimeEnergyWh = newLifetimeEnergyWh,
            lifetimeCO2G = newLifetimeCO2G,
            totalEcoCredits = getTotalEcoCredits(
                newLifetimeWaterMl,
                newLifetimeEnergyWh,
                newLifetimeCO2G
            ),
            conversationCount = currentState.conversationCount + 1,
            currentAchievement = getAchievementName(newLifetimeWaterMl)
        )
    }
}

/**
 * Usage Examples:
 * ```kotlin
 * // Convert lifetime eco metrics to pet stats
 * val health = PetEcoIntegration.calculateHealthFromWater(5000) // 5L → 5 health points
 * val energy = PetEcoIntegration.calculateEnergyFromPower(100)  // 100 Wh → 1.5 energy points
 * val happiness = PetEcoIntegration.calculateHappinessFromCO2(200) // 200g → 2 happiness points
 *
 * // Get evolution stage from achievement
 * val stage = PetEcoIntegration.getEvolutionStageFromWater(5000) // 5L → INTERMEDIATE
 *
 * // Calculate particle counts for animation
 * val droplets = PetEcoIntegration.getWaterDropletCount(120) // 120ml → 12 droplets
 *
 * // Update entire pet state from eco metrics
 * val updatedState = PetEcoIntegration.updatePetStateFromEco(
 *     currentState = petState,
 *     lifetimeWaterMl = 5000,
 *     lifetimeEnergyWh = 100,
 *     lifetimeCO2G = 50,
 *     totalTokens = 4000
 * )
 *
 * // Apply immediate session boost
 * val ecoSavings = EcoSavings(waterSavedMl = 120, energySavedWh = 3000, co2PreventedG = 2)
 * val boostedState = PetEcoIntegration.applySessionBoost(petState, ecoSavings)
 * ```
 */
