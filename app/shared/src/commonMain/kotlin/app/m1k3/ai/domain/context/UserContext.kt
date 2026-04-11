package app.m1k3.ai.domain.context

/**
 * A snapshot of the user's real-world context at the moment M1K3 starts.
 *
 * Every field is optional — M1K3 works without any of it.
 * The more the user shares, the more personalised the experience.
 *
 * Philosophy: "The more you share, the more M1K3 understands —
 * but it never leaves your phone."
 *
 * @param hourOfDay         Current hour (0–23) for time-aware greeting
 * @param userName          User's name from device account (if available)
 * @param location          City/country/coords (coarse only, opt-in)
 * @param health            Steps, sleep, heart rate from Health Connect
 * @param screenTime        Today's screen time from UsageStats
 * @param notifications     Unread notification count (opt-in listener)
 * @param weather           Current conditions at the user's location
 */
data class UserContext(
    val hourOfDay: Int = 12,
    val userName: String? = null,
    val location: LocationContext? = null,
    val health: HealthContext? = null,
    val screenTime: ScreenTimeContext? = null,
    val notifications: NotificationContext? = null,
    val weather: WeatherContext? = null
) {
    val hasAnyContext: Boolean
        get() = userName != null || location != null || health != null ||
                screenTime != null || notifications != null || weather != null
}

/**
 * Coarse location context — city-level only by default.
 * Fine location available if user upgrades permission.
 */
data class LocationContext(
    val city: String? = null,
    val country: String? = null,
    val lat: Double? = null,
    val lon: Double? = null
) {
    val displayName: String
        get() = listOfNotNull(city, country).joinToString(", ").ifBlank { "Unknown location" }
}

/**
 * Health data from Health Connect (Android 14+ built-in).
 * Each field independently optional — user may grant some but not all.
 */
data class HealthContext(
    val stepsToday: Long? = null,
    val sleepLastNightMinutes: Int? = null,
    val heartRateLatestBpm: Int? = null,
    val activeCaloriesToday: Int? = null
) {
    val hasSleep: Boolean get() = sleepLastNightMinutes != null
    val hasSteps: Boolean get() = stepsToday != null
    val hasHeartRate: Boolean get() = heartRateLatestBpm != null
    val isEmpty: Boolean get() = !hasSleep && !hasSteps && !hasHeartRate && activeCaloriesToday == null
}

/**
 * Screen time from Android UsageStatsManager.
 * Requires PACKAGE_USAGE_STATS special permission.
 */
data class ScreenTimeContext(
    val todayMinutes: Int = 0,
    val topApps: List<AppUsage> = emptyList()
)

data class AppUsage(
    val packageName: String,
    val displayName: String,
    val minutesToday: Int
)

/**
 * Notification context from NotificationListenerService.
 * Requires user to grant notification access in Settings.
 *
 * Now captures actual content — M1K3 reads the letters, not just counts envelopes.
 */
data class NotificationContext(
    val unreadCount: Int = 0,
    val hasUrgent: Boolean = false,
    val recentNotifications: List<NotificationContent> = emptyList()
) {
    /** Human-readable summary of notification content for embedding/prompts */
    val contentSummary: String
        get() = if (recentNotifications.isEmpty()) {
            "$unreadCount notification${if (unreadCount != 1) "s" else ""}"
        } else {
            recentNotifications.joinToString("\n") { it.summary }
        }
}

/**
 * Individual notification content — the actual intelligence.
 *
 * @param appName Source app display name (e.g. "WhatsApp", "Gmail")
 * @param title Notification title (e.g. sender name, subject)
 * @param text Notification body text
 * @param timestamp When the notification was posted (epoch ms)
 */
data class NotificationContent(
    val appName: String,
    val title: String? = null,
    val text: String? = null,
    val timestamp: Long = 0L
) {
    /** Embeddable summary: "[App] Title: Text" */
    val summary: String
        get() = buildString {
            append("[$appName]")
            title?.let { append(" $it") }
            text?.let { append(": $it") }
        }
}
