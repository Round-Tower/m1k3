package app.m1k3.ai.domain.context

/**
 * Converts UserContext snapshots into embeddable memory chunks.
 *
 * This is the bridge between context capture and the memory/RAG system.
 * Each context dimension (health, screen time, notifications) produces
 * text chunks that get embedded, stored, and become searchable.
 *
 * "Hey M1K3, what was that delivery notification?" → RAG finds the chunk.
 * "How's my sleep this week?" → trend analysis over health chunks.
 *
 * Pure Kotlin — no platform dependencies, fully testable.
 *
 * @see <a href="https://murphysig.dev">MurphySig</a>
 * Signed: kev + claude | confidence: 0.9 | context: context-to-memory pipeline
 */
class ContextMemoryService {

    /**
     * Convert a UserContext snapshot into embeddable chunks.
     *
     * Each context dimension produces zero or more chunks with:
     * - Human-readable text (for embedding + display)
     * - Category tag (for filtering + trend analysis)
     * - Pre-calculated importance (for memory threshold)
     *
     * @param context Current user context snapshot
     * @param timestamp When this snapshot was taken (epoch ms)
     * @return List of context chunks ready for embedding
     */
    fun createContextChunks(context: UserContext, timestamp: Long): List<ContextChunk> {
        val chunks = mutableListOf<ContextChunk>()

        // Notification content → individual chunks per notification
        context.notifications?.let { notifs ->
            notifs.recentNotifications.forEach { notification ->
                val importance = if (notifs.hasUrgent) 0.5f else 0.4f
                chunks += ContextChunk(
                    text = notification.summary,
                    category = "notification",
                    importance = importance,
                    timestamp = notification.timestamp
                )
            }
        }

        // Health snapshot → single chunk with all vitals
        context.health?.let { health ->
            if (!health.isEmpty) {
                val parts = mutableListOf<String>()
                health.stepsToday?.let { parts += "$it steps" }
                health.sleepLastNightMinutes?.let {
                    val h = it / 60
                    val m = it % 60
                    parts += "${h}h${if (m > 0) " ${m}m" else ""} sleep"
                }
                health.heartRateLatestBpm?.let { parts += "${it}bpm resting heart rate" }
                health.activeCaloriesToday?.let { parts += "$it active calories" }

                chunks += ContextChunk(
                    text = "Health snapshot: ${parts.joinToString(", ")}",
                    category = "health",
                    importance = 0.35f,
                    timestamp = timestamp
                )
            }
        }

        // Screen time → single chunk with total + top apps
        context.screenTime?.let { screen ->
            if (screen.todayMinutes > 0) {
                val h = screen.todayMinutes / 60
                val m = screen.todayMinutes % 60
                val total = if (h > 0) "${h}h ${m}m" else "${m}m"
                val topApps = screen.topApps.take(3).joinToString(", ") {
                    "${it.displayName} ${it.minutesToday}m"
                }
                val text = buildString {
                    append("Screen time today: $total")
                    if (topApps.isNotBlank()) append(". Top apps: $topApps")
                }
                chunks += ContextChunk(
                    text = text,
                    category = "screen_time",
                    importance = 0.3f,
                    timestamp = timestamp
                )
            }
        }

        return chunks
    }
}

/**
 * A chunk of user context ready for embedding into the memory system.
 *
 * @param text Human-readable summary for embedding + display
 * @param category Context dimension: "notification", "health", "screen_time", "location"
 * @param importance Pre-calculated importance for memory threshold (0.0-1.0)
 * @param timestamp When this context was captured (epoch ms)
 */
data class ContextChunk(
    val text: String,
    val category: String,
    val importance: Float,
    val timestamp: Long
)
