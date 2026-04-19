package app.m1k3.ai.assistant.eco

import kotlin.math.abs

/**
 * EcoCalculator - Environmental Impact Calculator
 *
 * Calculates environmental savings from local AI processing vs cloud inference.
 *
 * **Baselines (per 100 tokens):**
 * - Water: 120ml saved (data center cooling)
 * - Energy: 3 Wh saved (GPU + network transmission)
 * - CO2: 2g prevented (electricity generation)
 *
 * Note: `bytesSent = 0` is a chat-inference invariant (chat never uses the
 * network), NOT an app-wide guarantee. Model downloads and web search do use
 * the network — they're tracked separately. See ADR-0006. Follow-up task #16
 * will surface real download/search bytes and a `cloudBytesAvoided` headline.
 *
 * **Philosophy:**
 * 間 AI embodies environmental consciousness and privacy transparency.
 * Every query processed locally saves resources and protects privacy.
 *
 * **References:**
 * - Data center water usage: ~1.8L per kWh (cooling)
 * - GPU inference energy: ~50W for cloud-scale models
 * - Network transmission: ~2 kWh per GB
 * - Electricity CO2: ~500g per kWh (global average)
 */
object EcoCalculator {
    // ==================== Constants ====================

    /**
     * Baseline savings per 100 tokens (vs cloud AI)
     */
    private const val WATER_ML_PER_100_TOKENS = 120
    private const val ENERGY_WH_PER_100_TOKENS = 3
    private const val CO2_G_PER_100_TOKENS = 2

    /**
     * Energy is stored as milliwatt-hours (mWh) for precision
     * 1 Wh = 1000 mWh
     */
    private const val ENERGY_MWH_PER_100_TOKENS = 3000

    // ==================== Calculation ====================

    /**
     * Calculate environmental savings for a given number of tokens.
     *
     * Scales linearly from baseline (100 tokens).
     *
     * @param tokens Number of tokens processed (input + output)
     * @return EcoSavings with calculated water, energy, and CO2 savings
     */
    fun calculateSavings(tokens: Int): EcoSavings {
        require(tokens >= 0) { "Tokens must be non-negative" }

        val scaleFactor = tokens / 100.0

        return EcoSavings(
            tokensProcessed = tokens,
            waterSavedMl = (WATER_ML_PER_100_TOKENS * scaleFactor).toInt(),
            energySavedWh = (ENERGY_MWH_PER_100_TOKENS * scaleFactor).toInt(),
            co2PreventedG = (CO2_G_PER_100_TOKENS * scaleFactor).toInt(),
            bytesSent = 0, // Always 0 - privacy enforcement
        )
    }

    // ==================== Formatting ====================

    /**
     * Format water amount in human-readable units.
     *
     * @param milliliters Water amount in milliliters
     * @return Formatted string (e.g., "120 ml" or "1.50 L")
     */
    fun formatWater(milliliters: Int): String =
        when {
            milliliters >= 1000 -> {
                val liters = milliliters / 1000.0
                "%.2f L".format(liters)
            }

            else -> {
                "$milliliters ml"
            }
        }

    /**
     * Format energy amount in human-readable units.
     *
     * @param milliwattHours Energy amount in milliwatt-hours (mWh)
     * @return Formatted string (e.g., "3.00 Wh" or "1.50 kWh")
     */
    fun formatEnergy(milliwattHours: Int): String {
        val wattHours = milliwattHours / 1000.0

        return when {
            wattHours >= 1000 -> {
                val kilowattHours = wattHours / 1000
                "%.2f kWh".format(kilowattHours)
            }

            else -> {
                "%.2f Wh".format(wattHours)
            }
        }
    }

    /**
     * Format CO2 amount in human-readable units.
     *
     * @param grams CO2 amount in grams
     * @return Formatted string (e.g., "100 g" or "1.50 kg")
     */
    fun formatCO2(grams: Int): String =
        when {
            grams >= 1000 -> {
                val kilograms = grams / 1000.0
                "%.2f kg".format(kilograms)
            }

            else -> {
                "$grams g"
            }
        }

    // ==================== Achievements ====================

    /**
     * Get the highest unlocked achievement for total water saved.
     *
     * Achievements motivate users and celebrate environmental impact.
     *
     * @param totalWaterMl Total water saved in milliliters
     * @return Highest unlocked Achievement, or null if below minimum threshold
     */
    fun getAchievement(totalWaterMl: Int): Achievement? {
        // Return the highest achievement where threshold <= totalWaterMl
        return Achievement.entries
            .filter { it.waterThresholdMl <= totalWaterMl }
            .maxByOrNull { it.waterThresholdMl }
    }
}

// ==================== Data Classes ====================

/**
 * Environmental savings from a single query.
 *
 * All values represent savings compared to cloud-based AI inference.
 */
data class EcoSavings(
    val tokensProcessed: Int,
    val waterSavedMl: Int,
    val energySavedWh: Int, // Stored as milliwatt-hours (mWh)
    val co2PreventedG: Int,
    val bytesSent: Int = 0, // Always 0 for privacy metric
)

/**
 * Achievement milestones for environmental impact.
 *
 * Unlocked when user reaches water saving thresholds.
 * Ordered from lowest to highest threshold for easy lookup.
 */
enum class Achievement(
    val waterThresholdMl: Int,
    val title: String,
    val emoji: String,
) {
    WATER_BOTTLE(500, "Saved a water bottle", "💧"),
    BUCKET(5000, "Saved a bucket", "🪣"),
    BATHTUB(100000, "Saved a bathtub", "🛁"),
    POOL(1000000, "Saved a swimming pool", "🏊"),
    OLYMPIC_POOL(2500000, "Saved an Olympic pool", "🏅"),
    ;

    companion object {
        /**
         * Get all achievements in order of threshold (lowest to highest)
         */
        fun getAllAchievements(): List<Achievement> = entries.sortedBy { it.waterThresholdMl }
    }
}
