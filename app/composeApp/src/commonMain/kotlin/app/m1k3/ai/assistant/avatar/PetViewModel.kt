package app.m1k3.ai.assistant.avatar

import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.eco.EcoSavings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * 間 AI Pixel Pet ViewModel
 *
 * Manages pixel pet state with eco credit integration.
 *
 * **Responsibilities:**
 * - Subscribe to eco metrics updates
 * - Update pet vitals from eco data (water → health, energy → energy, CO2 → happiness)
 * - Process user interactions (pat, feed, play)
 * - Trigger evolution when achievements unlocked
 * - Manage particle effects queue for visual feedback
 * - Apply system stress (battery, CPU, temp) to pet health
 *
 * **Integration Points:**
 * - EcoMetricsRepository: Lifetime eco data
 * - ChatViewModel: Real-time AI response eco savings
 * - SystemMetricsProvider: Device health metrics
 * - PetMetricsRepository: Persistence
 *
 * **State Flow:**
 * 1. Eco metrics recorded → EcoMetricsRepository
 * 2. ViewModel polls/observes eco changes
 * 3. PetEcoIntegration converts eco → pet stats
 * 4. Pet state updated, UI reacts
 * 5. Particle effects spawned for visual feedback
 * 6. State persisted to database
 */
class PetViewModel(
    private val ecoRepo: EcoMetricsRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    // === State Management ===

    private val _petState = MutableStateFlow(PixelPetState())
    val petState: StateFlow<PixelPetState> = _petState.asStateFlow()

    private val _particleEffects = MutableStateFlow<List<ParticleEffect>>(emptyList())
    val particleEffects: StateFlow<List<ParticleEffect>> = _particleEffects.asStateFlow()

    private val _evolutionEvent = MutableStateFlow<EvolutionEvent?>(null)
    val evolutionEvent: StateFlow<EvolutionEvent?> = _evolutionEvent.asStateFlow()

    private var updateJob: Job? = null
    private var stressMonitorJob: Job? = null

    // === Initialization ===

    init {
        startPeriodicEcoSync()
        startStressMonitoring()
    }

    /**
     * Start periodic sync with eco metrics (every 30 seconds)
     */
    private fun startPeriodicEcoSync() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive) {
                updatePetFromEcoMetrics()
                delay(30_000) // 30 seconds
            }
        }
    }

    /**
     * Monitor system stress and apply degradation
     */
    private fun startStressMonitoring() {
        stressMonitorJob?.cancel()
        stressMonitorJob = scope.launch {
            while (isActive) {
                applySystemStressDegradation()
                delay(60_000) // 1 minute
            }
        }
    }

    // === Eco Integration ===

    /**
     * Update pet state from lifetime eco metrics
     *
     * Called:
     * - Every 30 seconds (automatic sync)
     * - When app resumes from background
     * - After manual refresh
     */
    fun updatePetFromEcoMetrics() {
        scope.launch {
            val lifetimeStats = ecoRepo.getLifetimeStats() ?: return@launch

            val updatedState = PetEcoIntegration.updatePetStateFromEco(
                currentState = _petState.value,
                lifetimeWaterMl = lifetimeStats.totalWaterMl,
                lifetimeEnergyWh = lifetimeStats.totalEnergyWh,
                lifetimeCO2G = lifetimeStats.totalCo2G,
                totalTokens = lifetimeStats.totalTokens
            )

            // Check if evolution occurred
            if (updatedState.evolutionStage != _petState.value.evolutionStage) {
                triggerEvolution(updatedState.evolutionStage, updatedState.currentAchievement)
            }

            _petState.update { updatedState }
        }
    }

    /**
     * Process eco savings from a single AI response
     *
     * This is called immediately after each AI response to provide
     * real-time feedback with particle animations.
     *
     * Flow:
     * 1. Apply session boost to vitals
     * 2. Spawn particle effects (water droplets, energy sparkles, CO2 leaves)
     * 3. Update UI with new state
     * 4. Check for achievement unlock → evolution
     */
    fun onEcoMetricsRecorded(savings: EcoSavings) {
        scope.launch {
            // Apply immediate boost
            val boostedState = PetEcoIntegration.applySessionBoost(_petState.value, savings)

            // Spawn particles for visual feedback
            spawnEcoParticles(savings)

            // Update state
            _petState.update { boostedState }

            // Check for evolution (water-based achievement system)
            val newStage = PetEcoIntegration.getEvolutionStageFromWater(boostedState.lifetimeWaterMl)
            if (newStage != boostedState.evolutionStage) {
                val achievementName = PetEcoIntegration.getAchievementName(boostedState.lifetimeWaterMl)
                triggerEvolution(newStage, achievementName)
            }
        }
    }

    // === Particle Effects ===

    /**
     * Spawn eco-themed particles based on savings
     */
    private fun spawnEcoParticles(savings: EcoSavings) {
        val newParticles = mutableListOf<ParticleEffect>()

        // Water droplets (blue)
        val dropletCount = PetEcoIntegration.getWaterDropletCount(savings.waterSavedMl)
        repeat(dropletCount) {
            newParticles.add(
                ParticleEffect(
                    type = ParticleType.WATER_DROPLET,
                    x = (0..100).random().toFloat(),
                    y = (0..30).random().toFloat(),
                    lifetime = 1500L // 1.5 seconds
                )
            )
        }

        // Energy sparkles (yellow/orange)
        val sparkleCount = PetEcoIntegration.getEnergySparkleCount(savings.energySavedWh)
        repeat(sparkleCount) {
            newParticles.add(
                ParticleEffect(
                    type = ParticleType.ENERGY_SPARKLE,
                    x = (0..100).random().toFloat(),
                    y = (30..70).random().toFloat(),
                    lifetime = 1200L // 1.2 seconds
                )
            )
        }

        // CO2 leaves (green)
        val leafCount = PetEcoIntegration.getCO2LeafCount(savings.co2PreventedG)
        repeat(leafCount) {
            newParticles.add(
                ParticleEffect(
                    type = ParticleType.CO2_LEAF,
                    x = (0..100).random().toFloat(),
                    y = (70..100).random().toFloat(),
                    lifetime = 2000L // 2 seconds
                )
            )
        }

        _particleEffects.update { current -> current + newParticles }

        // Auto-cleanup particles after lifetime
        scope.launch {
            delay(2500) // Max lifetime + buffer
            _particleEffects.update { particles ->
                particles.filter { particle ->
                    Clock.System.now().toEpochMilliseconds() - particle.spawnTime < particle.lifetime
                }
            }
        }
    }

    /**
     * Clear all active particles
     */
    fun clearParticles() {
        _particleEffects.update { emptyList() }
    }

    // === User Interactions ===

    /**
     * Process user tap/pat interaction
     */
    fun onPat(type: InteractionType = InteractionType.PAT) {
        _petState.update { current ->
            val newHappiness = (current.happiness + type.happinessBoost).coerceIn(0f, 100f)
            val newEnergy = (current.energy + type.energyBoost).coerceIn(0f, 100f)

            // Spawn heart particle
            spawnInteractionParticle(ParticleType.HEART)

            current.copy(
                happiness = newHappiness,
                energy = newEnergy,
                patsReceived = current.patsReceived + 1,
                lastInteraction = Clock.System.now()
            )
        }
    }

    /**
     * Feed the pet (triggered by user action or task completion)
     */
    fun feedPet() {
        onPat(InteractionType.FEED)
    }

    /**
     * Spawn particle for interaction
     */
    private fun spawnInteractionParticle(type: ParticleType) {
        val particle = ParticleEffect(
            type = type,
            x = 50f, // Center
            y = 50f,
            lifetime = 1000L
        )
        _particleEffects.update { it + particle }
    }

    // === Evolution System ===

    /**
     * Trigger evolution sequence
     */
    private fun triggerEvolution(newStage: EvolutionStage, achievementName: String?) {
        val event = EvolutionEvent(
            fromStage = _petState.value.evolutionStage,
            toStage = newStage,
            achievementUnlocked = achievementName,
            timestamp = Clock.System.now()
        )

        _evolutionEvent.update { event }

        // Spawn evolution burst particles
        repeat(50) {
            spawnInteractionParticle(ParticleType.STAR)
        }

        // Update state with new stage
        _petState.update { current ->
            current.copy(
                evolutionStage = newStage,
                visualTheme = VisualTheme.fromEvolutionStage(newStage),
                environment = Environment.fromEvolutionStage(newStage),
                currentAchievement = achievementName
            )
        }

        // Clear evolution event after animation completes
        scope.launch {
            delay(3000) // 3 second evolution animation
            _evolutionEvent.update { null }
        }
    }

    /**
     * Acknowledge evolution (dismiss notification)
     */
    fun acknowledgeEvolution() {
        _evolutionEvent.update { null }
    }

    // === System Stress ===

    /**
     * Update system metrics (battery, CPU, memory, temperature)
     *
     * Called by MainActivity or system monitor
     */
    fun updateSystemMetrics(
        batteryLevel: Float,
        cpuUsage: Float,
        memoryUsage: Float,
        temperature: Float
    ) {
        _petState.update { current ->
            current.copy(
                batteryLevel = batteryLevel,
                cpuUsage = cpuUsage,
                memoryUsage = memoryUsage,
                temperature = temperature,
                behaviorMode = PetBehaviorMode.fromState(current)
            )
        }
    }

    /**
     * Apply system stress degradation
     *
     * Called every minute to simulate pet care mechanics:
     * - Low battery → energy drain
     * - High CPU → mood decline
     * - High temp → health damage
     */
    private fun applySystemStressDegradation() {
        _petState.update { current ->
            var newHealth = current.health
            var newEnergy = current.energy
            var newMood = current.mood

            // Battery stress (< 15%)
            if (current.batteryLevel < 15f) {
                newEnergy = (newEnergy - (PixelPetState.ENERGY_DECAY_LOW_BATTERY / 60f)).coerceAtLeast(0f)
            }

            // CPU stress (> 80%)
            if (current.cpuUsage > 80f) {
                newMood = (newMood - (PixelPetState.MOOD_DECAY_ERRORS / 60f)).coerceAtLeast(0f)
            }

            // Temperature stress (> 70°C)
            if (current.temperature > 70f) {
                newHealth = (newHealth - (PixelPetState.HEALTH_DECAY_STRESSED / 60f)).coerceAtLeast(0f)
            }

            // Passive recovery when conditions are good
            if (current.batteryLevel > 80f && current.cpuUsage < 50f && current.temperature < 60f) {
                newHealth = (newHealth + (PixelPetState.HEALTH_RECOVERY_REST / 60f)).coerceAtMost(100f)
            }

            current.copy(
                health = newHealth,
                energy = newEnergy,
                mood = newMood
            )
        }
    }

    // === Lifecycle ===

    /**
     * Clean up coroutines
     */
    fun onCleared() {
        updateJob?.cancel()
        stressMonitorJob?.cancel()
    }
}

// === Supporting Data Classes ===

/**
 * Particle effect for visual feedback
 */
data class ParticleEffect(
    val type: ParticleType,
    val x: Float,  // 0-100 (percentage of container width)
    val y: Float,  // 0-100 (percentage of container height)
    val lifetime: Long, // milliseconds
    val spawnTime: Long = Clock.System.now().toEpochMilliseconds()
)

/**
 * Particle types with distinct visuals
 */
enum class ParticleType {
    WATER_DROPLET,  // Blue water drop
    ENERGY_SPARKLE, // Yellow/orange star
    CO2_LEAF,       // Green leaf
    HEART,          // Pink heart (interaction)
    STAR            // Gold star (evolution)
}

/**
 * Evolution event for UI notification
 */
data class EvolutionEvent(
    val fromStage: EvolutionStage,
    val toStage: EvolutionStage,
    val achievementUnlocked: String?,
    val timestamp: kotlinx.datetime.Instant
)
