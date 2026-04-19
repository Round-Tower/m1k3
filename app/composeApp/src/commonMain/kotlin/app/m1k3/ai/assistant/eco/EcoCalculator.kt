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

    /**
     * Estimated bytes a round-trip to a cloud LLM API would consume per
     * 100 tokens. Basis: ~2 KB JSON request envelope + ~4 KB response
     * envelope for an OpenAI-shape chat completion. Order-of-magnitude —
     * used only for the "cloud bytes avoided" hero stat.
     */
    private const val CLOUD_BYTES_PER_100_TOKENS = 6_000L

    // ==================== Calculation ====================

    /**
     * Calculate environmental savings for on-device inference of [tokens].
     *
     * Scales linearly from baseline (100 tokens). Inference rows always
     * record 0 network bytes — chat stays on-device. For real network
     * events (downloads, web search), use [networkEvent] instead.
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
            bytesSent = 0,
            bytesReceived = 0,
        )
    }

    /**
     * Record a real network event (download or web-search round-trip).
     *
     * Zero tokens, zero inference savings — just real bytes. Persisted
     * alongside inference rows so the "network usage" breakdown and the
     * "cloud bytes avoided" headline can be derived from a single table.
     */
    fun networkEvent(
        bytesSent: Int,
        bytesReceived: Int,
    ): EcoSavings {
        require(bytesSent >= 0 && bytesReceived >= 0) { "Bytes must be non-negative" }
        return EcoSavings(
            tokensProcessed = 0,
            waterSavedMl = 0,
            energySavedWh = 0,
            co2PreventedG = 0,
            bytesSent = bytesSent,
            bytesReceived = bytesReceived,
        )
    }

    /**
     * Estimate the bytes that WOULD have been sent to a cloud LLM API for
     * [tokens] of on-device inference. Headline privacy-win metric.
     */
    fun cloudBytesAvoided(tokens: Long): Long {
        require(tokens >= 0) { "Tokens must be non-negative" }
        return (tokens * CLOUD_BYTES_PER_100_TOKENS) / 100L
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
 * A single EcoMetrics row.
 *
 * Two shapes — same data class, different fields populated:
 *   - **Inference:** tokens + water/energy/co2 savings, bytes = 0.
 *   - **Network event:** tokens = 0, savings = 0, real bytes set.
 *
 * Keeping both in one table lets the UI aggregate freely (e.g. "per-day
 * tokens" sums inference rows naturally because network rows have 0 tokens).
 * Use [EcoCalculator.calculateSavings] for inference, [EcoCalculator.networkEvent]
 * for downloads / web searches.
 */
data class EcoSavings(
    val tokensProcessed: Int,
    val waterSavedMl: Int,
    val energySavedWh: Int, // Stored as milliwatt-hours (mWh)
    val co2PreventedG: Int,
    val bytesSent: Int = 0,
    val bytesReceived: Int = 0,
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
