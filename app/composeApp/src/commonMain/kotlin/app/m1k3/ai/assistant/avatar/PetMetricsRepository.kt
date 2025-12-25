package app.m1k3.ai.assistant.avatar

import app.m1k3.ai.assistant.database.MaDatabase
import kotlinx.datetime.Clock

/**
 * 間 AI Pet Metrics Repository
 *
 * Clean API for pixel pet state persistence and historical tracking.
 *
 * **Responsibilities:**
 * - Save pet state snapshots to database
 * - Load most recent pet state on app start
 * - Track evolution history and vital statistics
 * - Provide analytics data for pet dashboard
 *
 * **Snapshot Strategy:**
 * - Auto-save every 5 minutes (background task)
 * - Save on app pause/background (lifecycle event)
 * - Save on significant events (evolution, achievement unlock)
 * - Keep last 30 days of snapshots (data retention)
 *
 * **Usage Example:**
 * ```kotlin
 * val repo = PetMetricsRepository(database)
 *
 * // Save current pet state
 * repo.saveSnapshot(petState, sessionId = "chat_123")
 *
 * // Load on app start
 * val lastState = repo.loadLatestState()
 * if (lastState != null) {
 *     petViewModel.restoreState(lastState)
 * }
 *
 * // Get evolution history
 * val evolutions = repo.getEvolutionHistory()
 * ```
 */
class PetMetricsRepository(private val database: MaDatabase) {

    // === State Persistence ===

    /**
     * Save current pet state snapshot
     *
     * @param state Current pixel pet state
     * @param sessionId Optional chat session ID for tracking
     * @return Snapshot ID (database row ID)
     */
    fun saveSnapshot(
        state: PixelPetState,
        sessionId: String? = null
    ): Long {
        val timestamp = Clock.System.now().toEpochMilliseconds()

        database.petMetricsQueries.insertPetSnapshot(
            timestamp = timestamp,
            health = state.health.toDouble(),
            mood = state.mood.toDouble(),
            energy = state.energy.toDouble(),
            happiness = state.happiness.toDouble(),
            battery_level = state.batteryLevel.toDouble(),
            cpu_usage = state.cpuUsage.toDouble(),
            memory_usage = state.memoryUsage.toDouble(),
            temperature = state.temperature.toDouble(),
            last_interaction = state.lastInteraction.toEpochMilliseconds(),
            last_fed = state.lastFed.toEpochMilliseconds(),
            conversation_count = state.conversationCount.toLong(),
            pats_received = state.patsReceived.toLong(),
            experience_points = state.experiencePoints.toLong(),
            evolution_stage = state.evolutionStage.name,
            total_eco_credits = state.totalEcoCredits,
            lifetime_water_ml = state.lifetimeWaterMl,
            lifetime_energy_wh = state.lifetimeEnergyWh,
            lifetime_co2_g = state.lifetimeCO2G,
            daily_eco_goal = state.dailyEcoGoal.toLong(),
            current_achievement = state.currentAchievement,
            visual_theme = state.visualTheme.name,
            environment = state.environment.name,
            behavior_mode = state.behaviorMode.name,
            needs_attention = if (state.needsAttention) 1 else 0,
            session_id = sessionId
        )

        // Return the last inserted row ID
        return database.petMetricsQueries.transactionWithResult {
            database.petMetricsQueries.getLatestPetState().executeAsOne().id
        }
    }

    /**
     * Load most recent pet state
     *
     * Used on app start to restore pet from last session.
     *
     * @return Latest pet state or null if no snapshots exist
     */
    fun loadLatestState(): PixelPetState? {
        val snapshot = database.petMetricsQueries.getLatestPetState()
            .executeAsOneOrNull() ?: return null

        return snapshotToState(snapshot)
    }

    /**
     * Get pet state by snapshot ID
     */
    fun getStateById(id: Long): PixelPetState? {
        val snapshot = database.petMetricsQueries.getPetStateById(id)
            .executeAsOneOrNull() ?: return null

        return snapshotToState(snapshot)
    }

    // === Historical Data ===

    /**
     * Get evolution history
     *
     * Returns timestamps when pet evolved to new stages.
     * Filters snapshots where evolution_stage changed from previous snapshot.
     *
     * @return List of evolution events with timestamps and achievements
     */
    fun getEvolutionHistory(): List<EvolutionHistoryEntry> {
        val allSnapshots = database.petMetricsQueries.getEvolutionHistory()
            .executeAsList()

        // Filter for evolution changes (where stage differs from previous)
        val evolutionEvents = mutableListOf<EvolutionHistoryEntry>()
        var previousStage: String? = null

        allSnapshots.forEach { row ->
            if (previousStage != null && row.evolution_stage != previousStage) {
                // Evolution occurred!
                evolutionEvents.add(
                    EvolutionHistoryEntry(
                        timestamp = row.timestamp,
                        stage = EvolutionStage.valueOf(row.evolution_stage),
                        waterSavedMl = row.lifetime_water_ml,
                        achievement = row.current_achievement
                    )
                )
            }
            previousStage = row.evolution_stage
        }

        return evolutionEvents
    }

    /**
     * Get pet vital statistics over time
     *
     * @param sinceTimestamp Start timestamp (default: 30 days ago)
     * @return List of vital stats for charting
     */
    fun getVitalStats(sinceTimestamp: Long = getTimestamp30DaysAgo()): List<VitalStatsEntry> {
        return database.petMetricsQueries.getPetVitalStats(sinceTimestamp)
            .executeAsList()
            .map { row ->
                VitalStatsEntry(
                    timestamp = row.timestamp,
                    health = row.health.toFloat(),
                    mood = row.mood.toFloat(),
                    energy = row.energy.toFloat(),
                    happiness = row.happiness.toFloat()
                )
            }
    }

    /**
     * Get eco credit progression over time
     *
     * @param sinceTimestamp Start timestamp (default: 30 days ago)
     * @return List of eco progression data for charting
     */
    fun getEcoProgressionStats(sinceTimestamp: Long = getTimestamp30DaysAgo()): List<EcoProgressionEntry> {
        return database.petMetricsQueries.getEcoProgressionStats(sinceTimestamp)
            .executeAsList()
            .map { row ->
                EcoProgressionEntry(
                    timestamp = row.timestamp,
                    waterMl = row.lifetime_water_ml,
                    energyWh = row.lifetime_energy_wh,
                    co2G = row.lifetime_co2_g,
                    totalCredits = row.total_eco_credits,
                    stage = EvolutionStage.valueOf(row.evolution_stage)
                )
            }
    }

    /**
     * Get wellbeing score over time
     *
     * @param sinceTimestamp Start timestamp (default: 30 days ago)
     * @return List of wellbeing scores for trending
     */
    fun getWellbeingStats(sinceTimestamp: Long = getTimestamp30DaysAgo()): List<WellbeingEntry> {
        return database.petMetricsQueries.getWellbeingStats(sinceTimestamp)
            .executeAsList()
            .map { row ->
                WellbeingEntry(
                    timestamp = row.timestamp,
                    wellbeingScore = row.wellbeing_score.toFloat(),
                    stage = EvolutionStage.valueOf(row.evolution_stage)
                )
            }
    }

    // === Analytics ===

    /**
     * Get average vitals over date range
     *
     * @param startTimestamp Start of range
     * @param endTimestamp End of range
     * @return Average vital statistics
     */
    fun getAverageVitals(
        startTimestamp: Long = getTimestamp30DaysAgo(),
        endTimestamp: Long = Clock.System.now().toEpochMilliseconds()
    ): AverageVitalsStats? {
        val result = database.petMetricsQueries.getAverageVitals(startTimestamp, endTimestamp)
            .executeAsOneOrNull() ?: return null

        return AverageVitalsStats(
            avgHealth = result.avg_health?.toFloat() ?: 0f,
            avgMood = result.avg_mood?.toFloat() ?: 0f,
            avgEnergy = result.avg_energy?.toFloat() ?: 0f,
            avgHappiness = result.avg_happiness?.toFloat() ?: 0f,
            avgWellbeing = result.avg_wellbeing?.toFloat() ?: 0f
        )
    }

    /**
     * Get pet activity summary (all time)
     *
     * @return Comprehensive activity statistics
     */
    fun getActivitySummary(): ActivitySummary {
        val result = database.petMetricsQueries.getActivitySummary().executeAsOne()

        return ActivitySummary(
            snapshotCount = result.snapshot_count.toInt(),
            totalConversations = result.total_conversations?.toInt() ?: 0,
            totalPats = result.total_pats?.toInt() ?: 0,
            totalWaterSavedMl = result.total_water_saved ?: 0L,
            totalEnergySavedWh = result.total_energy_saved ?: 0L,
            totalCO2PreventedG = result.total_co2_prevented ?: 0L,
            highestEvolution = result.highest_evolution?.let { EvolutionStage.valueOf(it) }
                ?: EvolutionStage.BASIC
        )
    }

    // === Data Retention ===

    /**
     * Delete snapshots older than specified timestamp
     *
     * Default: Keep last 30 days of data
     *
     * @param olderThan Timestamp threshold (default: 30 days ago)
     * @return Number of snapshots deleted
     */
    fun deleteOldSnapshots(olderThan: Long = getTimestamp30DaysAgo()): Int {
        val countBefore = database.petMetricsQueries.countPetSnapshots().executeAsOne()
        database.petMetricsQueries.deleteOldSnapshots(olderThan)
        val countAfter = database.petMetricsQueries.countPetSnapshots().executeAsOne()

        return (countBefore - countAfter).toInt()
    }

    /**
     * Reset pet completely (delete all history)
     *
     * ⚠️ WARNING: This cannot be undone!
     */
    fun resetPet() {
        database.petMetricsQueries.deleteAllPetData()
    }

    // === Helper Functions ===

    /**
     * Convert database snapshot to PixelPetState
     */
    private fun snapshotToState(snapshot: app.m1k3.ai.assistant.database.PetMetrics): PixelPetState {
        return PixelPetState(
            health = snapshot.health.toFloat(),
            mood = snapshot.mood.toFloat(),
            energy = snapshot.energy.toFloat(),
            happiness = snapshot.happiness.toFloat(),
            batteryLevel = snapshot.battery_level.toFloat(),
            cpuUsage = snapshot.cpu_usage.toFloat(),
            memoryUsage = snapshot.memory_usage.toFloat(),
            temperature = snapshot.temperature.toFloat(),
            lastInteraction = kotlinx.datetime.Instant.fromEpochMilliseconds(snapshot.last_interaction),
            lastFed = kotlinx.datetime.Instant.fromEpochMilliseconds(snapshot.last_fed),
            conversationCount = snapshot.conversation_count.toInt(),
            patsReceived = snapshot.pats_received.toInt(),
            experiencePoints = snapshot.experience_points.toInt(),
            evolutionStage = EvolutionStage.valueOf(snapshot.evolution_stage),
            totalEcoCredits = snapshot.total_eco_credits,
            lifetimeWaterMl = snapshot.lifetime_water_ml,
            lifetimeEnergyWh = snapshot.lifetime_energy_wh,
            lifetimeCO2G = snapshot.lifetime_co2_g,
            dailyEcoGoal = snapshot.daily_eco_goal.toInt(),
            currentAchievement = snapshot.current_achievement,
            visualTheme = VisualTheme.valueOf(snapshot.visual_theme),
            environment = Environment.valueOf(snapshot.environment),
            behaviorMode = PetBehaviorMode.valueOf(snapshot.behavior_mode),
            needsAttention = snapshot.needs_attention == 1L
        )
    }

    /**
     * Get timestamp for 30 days ago
     */
    private fun getTimestamp30DaysAgo(): Long {
        return Clock.System.now().toEpochMilliseconds() - (30L * 24 * 60 * 60 * 1000)
    }
}

// === Data Classes for Analytics ===

data class EvolutionHistoryEntry(
    val timestamp: Long,
    val stage: EvolutionStage,
    val waterSavedMl: Long,
    val achievement: String?
)

data class VitalStatsEntry(
    val timestamp: Long,
    val health: Float,
    val mood: Float,
    val energy: Float,
    val happiness: Float
)

data class EcoProgressionEntry(
    val timestamp: Long,
    val waterMl: Long,
    val energyWh: Long,
    val co2G: Long,
    val totalCredits: Long,
    val stage: EvolutionStage
)

data class WellbeingEntry(
    val timestamp: Long,
    val wellbeingScore: Float,
    val stage: EvolutionStage
)

data class AverageVitalsStats(
    val avgHealth: Float,
    val avgMood: Float,
    val avgEnergy: Float,
    val avgHappiness: Float,
    val avgWellbeing: Float
)

data class ActivitySummary(
    val snapshotCount: Int,
    val totalConversations: Int,
    val totalPats: Int,
    val totalWaterSavedMl: Long,
    val totalEnergySavedWh: Long,
    val totalCO2PreventedG: Long,
    val highestEvolution: EvolutionStage
)
