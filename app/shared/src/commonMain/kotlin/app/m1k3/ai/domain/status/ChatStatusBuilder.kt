package app.m1k3.ai.domain.status

/**
 * Status information for display at the start of a new chat.
 *
 * @property greeting Time-based greeting (e.g., "Good afternoon!")
 * @property engineReady Whether the AI engine is ready
 * @property memoryCount Number of memories indexed
 * @property knowledgeCount Number of knowledge facts available
 * @property maxContextTokens Maximum context window size
 * @property deviceTierName Human-readable device tier
 * @property lastSessionTokens Tokens generated in last session (null if none)
 * @property lastSessionWaterMl Water saved in last session (ml)
 * @property lastSessionEnergyWh Energy saved in last session (Wh)
 * @property lastSessionCo2G CO2 prevented in last session (g)
 */
data class ChatStatus(
    val greeting: String,
    val engineReady: Boolean,
    val memoryCount: Long,
    val knowledgeCount: Long,
    val maxContextTokens: Int,
    val deviceTierName: String,
    val lastSessionTokens: Long?,
    val lastSessionWaterMl: Long?,
    val lastSessionEnergyWh: Long?,
    val lastSessionCo2G: Long?
)

/**
 * Builds chat status for display at the start of a new conversation.
 *
 * Assembles greeting, engine status, memory/knowledge stats, and
 * eco stats from the previous session into a displayable format.
 */
class ChatStatusBuilder {

    /**
     * Get time-based greeting for the given hour.
     *
     * @param hour Hour in 24-hour format (0-23)
     * @return Greeting string
     */
    fun getTimeBasedGreeting(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning!"
        in 12..17 -> "Good afternoon!"
        else -> "Good evening!"
    }

    /**
     * Build ChatStatus from component values.
     *
     * @param hour Current hour (0-23) for greeting
     * @param engineReady Whether AI engine is ready
     * @param memoryCount Number of indexed memories
     * @param knowledgeCount Number of knowledge facts
     * @param maxContextTokens Context window size
     * @param deviceTierName Human-readable device tier
     * @param lastSessionTokens Tokens from last session (null if none)
     * @param lastSessionWaterMl Water saved in last session
     * @param lastSessionEnergyWh Energy saved in last session
     * @param lastSessionCo2G CO2 prevented in last session
     */
    fun build(
        hour: Int,
        engineReady: Boolean,
        memoryCount: Long,
        knowledgeCount: Long,
        maxContextTokens: Int,
        deviceTierName: String,
        lastSessionTokens: Long?,
        lastSessionWaterMl: Long?,
        lastSessionEnergyWh: Long?,
        lastSessionCo2G: Long?
    ): ChatStatus = ChatStatus(
        greeting = getTimeBasedGreeting(hour),
        engineReady = engineReady,
        memoryCount = memoryCount,
        knowledgeCount = knowledgeCount,
        maxContextTokens = maxContextTokens,
        deviceTierName = deviceTierName,
        lastSessionTokens = lastSessionTokens,
        lastSessionWaterMl = lastSessionWaterMl,
        lastSessionEnergyWh = lastSessionEnergyWh,
        lastSessionCo2G = lastSessionCo2G
    )

    /**
     * Format ChatStatus as displayable text.
     *
     * @param status The status to format
     * @return Multi-line formatted status string
     */
    fun formatStatusText(status: ChatStatus): String {
        val lines = mutableListOf<String>()

        // Greeting
        lines.add(status.greeting)
        lines.add("")

        // Engine and stats
        val engineStatus = if (status.engineReady) "Ready" else "Loading..."
        lines.add("Engine: $engineStatus | Memories: ${status.memoryCount} | Knowledge: ${formatNumber(status.knowledgeCount)} facts")
        lines.add("Context: ${formatNumber(status.maxContextTokens.toLong())} tokens (${status.deviceTierName})")

        // Last session eco stats (if available)
        if (status.lastSessionWaterMl != null && status.lastSessionEnergyWh != null && status.lastSessionCo2G != null) {
            lines.add("")
            val waterL = status.lastSessionWaterMl / 1000.0
            lines.add("Last session: ${formatWater(waterL)} water | ${status.lastSessionEnergyWh} Wh | ${status.lastSessionCo2G}g CO2 saved")
        }

        return lines.joinToString("\n")
    }

    private fun formatNumber(n: Long): String {
        return if (n >= 1000) {
            val thousands = n / 1000
            val remainder = n % 1000
            if (remainder == 0L) {
                "${thousands},000"
            } else {
                "$thousands,${remainder.toString().padStart(3, '0')}"
            }
        } else {
            n.toString()
        }
    }

    private fun formatWater(liters: Double): String {
        return if (liters >= 1.0) {
            "${String.format("%.1f", liters)}L"
        } else {
            "${(liters * 1000).toInt()}ml"
        }
    }
}
