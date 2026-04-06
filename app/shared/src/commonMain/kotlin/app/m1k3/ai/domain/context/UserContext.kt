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
 */
data class UserContext(
    val hourOfDay: Int = 12,
    val userName: String? = null,
    val location: LocationContext? = null,
    val health: HealthContext? = null,
    val screenTime: ScreenTimeContext? = null,
    val notifications: NotificationContext? = null
) {
    val hasAnyContext: Boolean
        get() = userName != null || location != null || health != null ||
                screenTime != null || notifications != null
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
 */
data class NotificationContext(
    val unreadCount: Int = 0,
    val hasUrgent: Boolean = false
)
