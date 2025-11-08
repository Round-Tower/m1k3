package app.m1k3.ai.assistant.avatar

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours

/**
 * 間 AI Pixel Pet Care System
 *
 * Tamagotchi-inspired virtual pet mechanics that reflect AI assistant state.
 * The robot's wellbeing is tied to system health, usage patterns, and user interaction.
 *
 * Philosophy: The avatar is not just a visual indicator, but a living companion
 * that requires care and responds to the AI's environment.
 */

/**
 * Pixel pet vital statistics
 *
 * Tracks health, mood, and energy levels that affect avatar appearance
 */
data class PixelPetState(
    // Core vitals (0-100 scale)
    val health: Float = 100f,        // Overall wellbeing (degraded by system stress)
    val mood: Float = 70f,            // Emotional state (affected by AI activity)
    val energy: Float = 80f,          // Activity capacity (tied to battery/performance)
    val happiness: Float = 60f,       // User interaction satisfaction

    // System metrics influence
    val batteryLevel: Float = 100f,   // Device battery (0-100)
    val cpuUsage: Float = 0f,         // CPU load (0-100)
    val memoryUsage: Float = 0f,      // Memory pressure (0-100)
    val temperature: Float = 25f,     // Device temp in Celsius

    // Activity tracking
    val lastInteraction: Instant = Clock.System.now(),
    val lastFed: Instant = Clock.System.now(),
    val conversationCount: Int = 0,
    val patsReceived: Int = 0,

    // Evolution & progression
    val experiencePoints: Int = 0,
    val evolutionStage: EvolutionStage = EvolutionStage.BASIC,

    // Eco Credits Integration (NEW)
    val totalEcoCredits: Long = 0,           // Lifetime eco credits earned
    val lifetimeWaterMl: Long = 0,           // Total water saved (ml)
    val lifetimeEnergyWh: Long = 0,          // Total energy saved (Wh)
    val lifetimeCO2G: Long = 0,              // Total CO2 prevented (g)
    val dailyEcoGoal: Int = 1000,            // Daily eco credit target
    val currentAchievement: String? = null,  // Highest achievement unlocked

    // Visual Customization (NEW)
    val visualTheme: VisualTheme = VisualTheme.BASIC,     // Robot skin/style
    val environment: Environment = Environment.VOID,       // Background environment

    // Behavioral state
    val behaviorMode: PetBehaviorMode = PetBehaviorMode.NORMAL,
    val needsAttention: Boolean = false
) {
    /**
     * Overall wellbeing score (0-100)
     * Used to determine avatar appearance and behavior
     */
    val wellbeingScore: Float
        get() = (health * 0.4f + mood * 0.3f + energy * 0.2f + happiness * 0.1f)

    /**
     * Check if pet is in critical condition
     */
    val isCritical: Boolean
        get() = health < 20f || energy < 10f

    /**
     * Check if pet is thriving
     */
    val isThriving: Boolean
        get() = wellbeingScore > 80f && happiness > 70f

    /**
     * Get current status description
     */
    val statusDescription: String
        get() = when {
            isCritical -> "Critical - needs immediate attention!"
            health < 40f -> "Unwell - system stress detected"
            energy < 30f -> "Exhausted - low battery affecting performance"
            mood < 30f -> "Unhappy - experiencing errors or issues"
            happiness < 30f -> "Lonely - needs user interaction"
            isThriving -> "Thriving - everything is optimal!"
            wellbeingScore > 70f -> "Healthy - doing well"
            else -> "Okay - could use some care"
        }

    /**
     * Time since last interaction
     */
    fun timeSinceInteraction(): Duration {
        return Clock.System.now() - lastInteraction
    }

    /**
     * Time since last fed (successful task completion)
     */
    fun timeSinceFed(): Duration {
        return Clock.System.now() - lastFed
    }

    companion object {
        // Degradation rates (per hour)
        const val HEALTH_DECAY_IDLE = 2f          // Slow decay when idle
        const val HEALTH_DECAY_STRESSED = 5f       // Faster when CPU/temp high
        const val ENERGY_DECAY_ACTIVE = 10f        // Energy drains during active use
        const val ENERGY_DECAY_LOW_BATTERY = 15f   // Faster on low battery
        const val MOOD_DECAY_ERRORS = 8f           // Mood drops with errors
        const val HAPPINESS_DECAY_NEGLECT = 3f     // Loneliness from no interaction

        // Recovery rates
        const val HEALTH_RECOVERY_REST = 5f        // Recovery during idle/low CPU
        const val ENERGY_RECOVERY_CHARGING = 20f   // Recovery while charging
        const val MOOD_BOOST_SUCCESS = 10f         // Mood boost from successful tasks
        const val HAPPINESS_BOOST_INTERACTION = 15f // Happiness from user pats/interaction

        // Thresholds
        val ATTENTION_NEEDED_THRESHOLD = 6.hours   // Request attention after 6h neglect
        val CRITICAL_NEGLECT_THRESHOLD = 24.hours  // Critical state after 24h
        val FEEDING_INTERVAL = 4.hours             // Expect task completion every 4h
    }
}

/**
 * Evolution stages - visual complexity increases with experience
 */
enum class EvolutionStage(
    val displayName: String,
    val requiredXP: Int,
    val maxLevel: Int,
    val description: String
) {
    BASIC(
        displayName = "Newborn",
        requiredXP = 0,
        maxLevel = 10,
        description = "Fresh AI companion, learning the basics"
    ),
    INTERMEDIATE(
        displayName = "Developing",
        requiredXP = 100,
        maxLevel = 25,
        description = "Growing in capability and personality"
    ),
    ADVANCED(
        displayName = "Mature",
        requiredXP = 500,
        maxLevel = 50,
        description = "Experienced assistant with refined skills"
    ),
    EXPERT(
        displayName = "Master",
        requiredXP = 2000,
        maxLevel = 100,
        description = "Elite AI companion, peak performance"
    ),
    LEGENDARY(
        displayName = "Legendary",
        requiredXP = 10000,
        maxLevel = 999,
        description = "Transcendent intelligence, maximum bond"
    );

    fun canEvolve(currentXP: Int): Boolean {
        val nextStage = entries.getOrNull(ordinal + 1)
        return nextStage != null && currentXP >= nextStage.requiredXP
    }
}

/**
 * Behavioral modes affect animation intensity and response patterns
 */
enum class PetBehaviorMode(
    val description: String,
    val animationIntensity: Float
) {
    SLEEPING(
        description = "Resting to conserve energy",
        animationIntensity = 0.2f
    ),
    IDLE(
        description = "Calm and waiting",
        animationIntensity = 0.4f
    ),
    NORMAL(
        description = "Active and responsive",
        animationIntensity = 0.6f
    ),
    ENERGETIC(
        description = "Highly active and playful",
        animationIntensity = 0.9f
    ),
    STRESSED(
        description = "Overwhelmed by system load",
        animationIntensity = 0.3f
    ),
    SICK(
        description = "Unwell, needs recovery",
        animationIntensity = 0.25f
    );

    companion object {
        /**
         * Determine behavior mode from pet state
         */
        fun fromState(state: PixelPetState): PetBehaviorMode {
            return when {
                state.health < 30f -> SICK
                state.cpuUsage > 80f || state.temperature > 70f -> STRESSED
                state.energy < 20f || state.batteryLevel < 15f -> SLEEPING
                state.energy < 40f -> IDLE
                state.mood > 80f && state.happiness > 70f -> ENERGETIC
                else -> NORMAL
            }
        }
    }
}

/**
 * Events that affect pet state
 */
sealed class PetEvent {
    data class UserInteraction(val type: InteractionType) : PetEvent()
    data class TaskCompleted(val success: Boolean, val xpGained: Int = 10) : PetEvent()
    data class SystemMetricsUpdate(
        val battery: Float,
        val cpu: Float,
        val memory: Float,
        val temperature: Float
    ) : PetEvent()
    data class AIActivity(val activity: AvatarActivity) : PetEvent()
    data object TimeElapsed : PetEvent()
    data class HealthCheckup(val boostAmount: Float = 20f) : PetEvent()
}

/**
 * User interaction types
 */
enum class InteractionType(
    val happinessBoost: Float,
    val energyBoost: Float,
    val displayMessage: String
) {
    PAT(
        happinessBoost = 5f,
        energyBoost = 2f,
        displayMessage = "Pet enjoyed the pat! 💙"
    ),
    DOUBLE_TAP(
        happinessBoost = 10f,
        energyBoost = 5f,
        displayMessage = "Extra love! 💕"
    ),
    LONG_PRESS(
        happinessBoost = 15f,
        energyBoost = 10f,
        displayMessage = "Deep connection! ✨"
    ),
    FEED(
        happinessBoost = 20f,
        energyBoost = 25f,
        displayMessage = "Fed and energized! 🌟"
    )
}

/**
 * Achievement milestones
 */
enum class PetAchievement(
    val displayName: String,
    val description: String,
    val condition: (PixelPetState) -> Boolean
) {
    FIRST_PAT(
        displayName = "First Touch",
        description = "Received first user interaction",
        condition = { it.patsReceived >= 1 }
    ),
    CONVERSATION_STARTER(
        displayName = "Chatty",
        description = "Completed 10 conversations",
        condition = { it.conversationCount >= 10 }
    ),
    CENTURY_CLUB(
        displayName = "Century Club",
        description = "Completed 100 conversations",
        condition = { it.conversationCount >= 100 }
    ),
    HEALTHY_LIFESTYLE(
        displayName = "Wellness Master",
        description = "Maintained 90+ health for 7 days",
        condition = { it.health >= 90f && it.wellbeingScore >= 85f }
    ),
    NIGHT_OWL(
        displayName = "Night Owl",
        description = "Active during late hours",
        condition = { false } // Requires time-of-day tracking
    ),
    EVOLUTION_COMPLETE(
        displayName = "Transcendent",
        description = "Reached Legendary evolution stage",
        condition = { it.evolutionStage == EvolutionStage.LEGENDARY }
    );

    fun isUnlocked(state: PixelPetState): Boolean {
        return condition(state)
    }
}

/**
 * Visual themes - Robot skins that unlock with evolution
 */
enum class VisualTheme(
    val displayName: String,
    val primaryColor: androidx.compose.ui.graphics.Color,
    val description: String
) {
    BASIC(
        displayName = "Basic",
        primaryColor = androidx.compose.ui.graphics.Color(0xFF808080), // Gray
        description = "Simple gray robot - starting form"
    ),
    SLEEK(
        displayName = "Sleek",
        primaryColor = androidx.compose.ui.graphics.Color(0xFFC0C0C0), // Silver
        description = "Polished silver robot - refined design"
    ),
    CRYSTALLINE(
        displayName = "Crystalline",
        primaryColor = androidx.compose.ui.graphics.Color(0xFF4169E1), // Royal Blue
        description = "Crystal blue robot - advanced form"
    ),
    ENERGY(
        displayName = "Energy",
        primaryColor = androidx.compose.ui.graphics.Color(0xFFE25303), // M1K3 Orange
        description = "Glowing orange energy robot - master form"
    ),
    LEGENDARY(
        displayName = "Legendary",
        primaryColor = androidx.compose.ui.graphics.Color(0xFFFFD700), // Gold
        description = "Rainbow aura legendary robot - ultimate form"
    );

    companion object {
        fun fromEvolutionStage(stage: EvolutionStage): VisualTheme {
            return when (stage) {
                EvolutionStage.BASIC -> BASIC
                EvolutionStage.INTERMEDIATE -> SLEEK
                EvolutionStage.ADVANCED -> CRYSTALLINE
                EvolutionStage.EXPERT -> ENERGY
                EvolutionStage.LEGENDARY -> LEGENDARY
            }
        }
    }
}

/**
 * Background environments that unlock with evolution
 */
enum class Environment(
    val displayName: String,
    val description: String
) {
    VOID(
        displayName = "Void",
        description = "Empty space - starting environment"
    ),
    OFFICE(
        displayName = "Office",
        description = "Modern workspace - professional setting"
    ),
    GARDEN(
        displayName = "Garden",
        description = "Lush greenery - natural environment"
    ),
    LAB(
        displayName = "Lab",
        description = "High-tech laboratory - advanced facility"
    ),
    SPACE_STATION(
        displayName = "Space Station",
        description = "Orbital platform - legendary domain"
    );

    companion object {
        fun fromEvolutionStage(stage: EvolutionStage): Environment {
            return when (stage) {
                EvolutionStage.BASIC -> VOID
                EvolutionStage.INTERMEDIATE -> OFFICE
                EvolutionStage.ADVANCED -> GARDEN
                EvolutionStage.EXPERT -> LAB
                EvolutionStage.LEGENDARY -> SPACE_STATION
            }
        }
    }
}

/**
 * Usage Examples:
 * ```kotlin
 * // Create initial pet state
 * val petState = PixelPetState()
 *
 * // Update with system metrics
 * val updatedState = petState.copy(
 *     batteryLevel = 45f,
 *     cpuUsage = 65f
 * )
 *
 * // Update with eco credits
 * val withEco = petState.copy(
 *     lifetimeWaterMl = 5000, // 5L saved
 *     totalEcoCredits = 5000 + 100*10 + 50*5 // water + energy*10 + co2*5
 * )
 *
 * // Check behavior mode
 * val mode = PetBehaviorMode.fromState(updatedState)
 *
 * // Process user interaction
 * val afterPat = updatedState.copy(
 *     happiness = (updatedState.happiness + InteractionType.PAT.happinessBoost).coerceIn(0f, 100f),
 *     patsReceived = updatedState.patsReceived + 1,
 *     lastInteraction = Clock.System.now()
 * )
 *
 * // Check if needs attention
 * if (petState.timeSinceInteraction() > PixelPetState.ATTENTION_NEEDED_THRESHOLD) {
 *     // Show notification or visual indicator
 * }
 *
 * // Get visual theme from evolution
 * val theme = VisualTheme.fromEvolutionStage(petState.evolutionStage)
 * val environment = Environment.fromEvolutionStage(petState.evolutionStage)
 * ```
 */
