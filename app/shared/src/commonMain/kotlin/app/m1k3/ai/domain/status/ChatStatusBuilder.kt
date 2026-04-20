package app.m1k3.ai.domain.status

/**
 * Status information for display at the start of a new chat.
 *
 * @property greeting Time-based greeting (e.g., "Good afternoon!")
 * @property engineReady Whether the AI engine is ready
 * @property memoryCount Number of memories indexed
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
    val maxContextTokens: Int,
    val deviceTierName: String,
    val lastSessionTokens: Long?,
    val lastSessionWaterMl: Long?,
    val lastSessionEnergyWh: Long?,
    val lastSessionCo2G: Long?,
)

/**
 * Builds chat status for display at the start of a new conversation.
 *
 * Assembles greeting, engine status, memory stats, and eco stats from the
 * previous session into a displayable format.
 */
class ChatStatusBuilder {
    fun getTimeBasedGreeting(hour: Int): String =
        when (hour) {
            in 5..11 -> "Good morning!"
            in 12..17 -> "Good afternoon!"
            else -> "Good evening!"
        }

    fun build(
        hour: Int,
        engineReady: Boolean,
        memoryCount: Long,
        maxContextTokens: Int,
        deviceTierName: String,
        lastSessionTokens: Long?,
        lastSessionWaterMl: Long?,
        lastSessionEnergyWh: Long?,
        lastSessionCo2G: Long?,
    ): ChatStatus =
        ChatStatus(
            greeting = getTimeBasedGreeting(hour),
            engineReady = engineReady,
            memoryCount = memoryCount,
            maxContextTokens = maxContextTokens,
            deviceTierName = deviceTierName,
            lastSessionTokens = lastSessionTokens,
            lastSessionWaterMl = lastSessionWaterMl,
            lastSessionEnergyWh = lastSessionEnergyWh,
            lastSessionCo2G = lastSessionCo2G,
        )

    fun formatStatusText(status: ChatStatus): String {
        val lines = mutableListOf<String>()

        lines.add(status.greeting)
        lines.add("")

        val engineStatus = if (status.engineReady) "Ready" else "Loading..."
        lines.add("Engine: $engineStatus | Memories: ${status.memoryCount}")
        lines.add("Context: ${formatNumber(status.maxContextTokens.toLong())} tokens (${status.deviceTierName})")

        if (status.lastSessionWaterMl != null && status.lastSessionEnergyWh != null && status.lastSessionCo2G != null) {
            lines.add("")
            val waterL = status.lastSessionWaterMl / 1000.0
            lines.add(
                "Last session: ${formatWater(waterL)} water | ${status.lastSessionEnergyWh} Wh | ${status.lastSessionCo2G}g CO2 saved",
            )
        }

        return lines.joinToString("\n")
    }

    private fun formatNumber(n: Long): String =
        if (n >= 1000) {
            val thousands = n / 1000
            val remainder = n % 1000
            if (remainder == 0L) {
                "$thousands,000"
            } else {
                "$thousands,${remainder.toString().padStart(3, '0')}"
            }
        } else {
            n.toString()
        }

    private fun formatWater(liters: Double): String =
        if (liters >= 1.0) {
            "${String.format("%.1f", liters)}L"
        } else {
            "${(liters * 1000).toInt()}ml"
        }
}
