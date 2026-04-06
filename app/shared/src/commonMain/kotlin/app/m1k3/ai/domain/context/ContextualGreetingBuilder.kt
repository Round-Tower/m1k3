package app.m1k3.ai.domain.context

/**
 * Builds a structured contextual greeting from a UserContext snapshot.
 *
 * Pure function — no platform deps, fully testable in commonTest.
 *
 * Output is a GreetingResult with separate lines so the UI can
 * style each part independently. The builder is smart about
 * what to surface — morning prioritises sleep, evening prioritises
 * step count and day summary.
 *
 * Philosophy: don't dump everything. Show what matters right now.
 */
class ContextualGreetingBuilder {

    fun build(context: UserContext): GreetingResult {
        return GreetingResult(
            greeting = buildGreeting(context),
            locationLine = buildLocationLine(context.location),
            healthLine = buildHealthLine(context),
            screenTimeLine = buildScreenTimeLine(context.screenTime),
            notificationLine = buildNotificationLine(context.notifications),
            closingLine = buildClosingLine(context)
        )
    }

    // ── Greeting ───────────────────────────────────────────────

    private fun buildGreeting(context: UserContext): String {
        val timeWord = when (context.hourOfDay) {
            in 5..11  -> "morning"
            in 12..17 -> "afternoon"
            in 18..21 -> "evening"
            else      -> "night"
        }
        val name = context.userName?.let { ", $it" } ?: ""
        return "Good $timeWord$name"
    }

    // ── Location ───────────────────────────────────────────────

    private fun buildLocationLine(location: LocationContext?): String? {
        location ?: return null
        return location.displayName.ifBlank { null }
    }

    // ── Health ─────────────────────────────────────────────────

    private fun buildHealthLine(context: UserContext): String? {
        val health = context.health ?: return null
        if (health.isEmpty) return null

        val parts = mutableListOf<String>()
        val isMorning = context.hourOfDay in 5..11

        // Morning: prioritise sleep, then steps
        // Afternoon/evening: prioritise steps, then sleep
        if (isMorning) {
            health.sleepLastNightMinutes?.let { parts += formatSleep(it) }
            health.stepsToday?.takeIf { it > 0 }?.let { parts += formatSteps(it) }
        } else {
            health.stepsToday?.takeIf { it > 0 }?.let { parts += formatSteps(it) }
            health.sleepLastNightMinutes?.let { parts += formatSleep(it) }
        }

        health.heartRateLatestBpm?.let { parts += "$it bpm" }

        return parts.joinToString(" · ").ifBlank { null }
    }

    // ── Screen time ────────────────────────────────────────────

    private fun buildScreenTimeLine(screenTime: ScreenTimeContext?): String? {
        screenTime ?: return null
        if (screenTime.todayMinutes <= 0) return null
        return "${formatMinutes(screenTime.todayMinutes)} screen time today"
    }

    // ── Notifications ──────────────────────────────────────────

    private fun buildNotificationLine(notifications: NotificationContext?): String? {
        notifications ?: return null
        if (notifications.unreadCount <= 0) return null
        val noun = if (notifications.unreadCount == 1) "notification" else "notifications"
        return "${notifications.unreadCount} $noun waiting"
    }

    // ── Closing ────────────────────────────────────────────────

    private fun buildClosingLine(context: UserContext): String {
        return when (context.hourOfDay) {
            in 5..8   -> "Ready when you are."
            in 9..17  -> "What are we working on?"
            in 18..21 -> "How was the day?"
            else      -> "Still here."
        }
    }

    // ── Formatters ─────────────────────────────────────────────

    private fun formatSleep(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "${h}h sleep" else "${h}h ${m}m sleep"
    }

    private fun formatSteps(steps: Long): String {
        return if (steps >= 1000) {
            val thousands = steps / 1000
            val remainder = steps % 1000
            "${thousands},${remainder.toString().padStart(3, '0')} steps"
        } else {
            "$steps steps"
        }
    }

    private fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h == 0) "${m}m" else if (m == 0) "${h}h" else "${h}h ${m}m"
    }
}

/**
 * A structured greeting broken into displayable lines.
 *
 * Each line is independently styled in the UI.
 * Null lines are hidden — no empty rows.
 */
data class GreetingResult(
    /** e.g. "Good morning, Kev" */
    val greeting: String,

    /** e.g. "Dublin, Ireland" — null if no location */
    val locationLine: String?,

    /** e.g. "7h 20m sleep · 4,821 steps · 68 bpm" — null if no health */
    val healthLine: String?,

    /** e.g. "2h 22m screen time today" — null if no usage stats */
    val screenTimeLine: String?,

    /** e.g. "4 notifications waiting" — null if zero or no access */
    val notificationLine: String?,

    /** e.g. "What are we working on?" — always present */
    val closingLine: String
) {
    /** All non-null lines in display order */
    val allLines: List<String>
        get() = listOfNotNull(
            greeting,
            locationLine,
            healthLine,
            screenTimeLine,
            notificationLine,
            closingLine
        )
}
