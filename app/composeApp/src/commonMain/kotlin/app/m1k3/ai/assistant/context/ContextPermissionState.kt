package app.m1k3.ai.assistant.context

/**
 * Tracks which context permissions the user has granted.
 *
 * Lives in commonMain so it can be tested without Android.
 * Platform code populates it from the actual permission check results.
 */
data class ContextPermissionState(
    val hasLocation: Boolean = false,
    val hasHealth: Boolean = false,
    val hasScreenTime: Boolean = false,
    val hasNotifications: Boolean = false
) {
    val grantedCount: Int
        get() = listOf(hasLocation, hasHealth, hasScreenTime, hasNotifications).count { it }

    val allGranted: Boolean
        get() = hasLocation && hasHealth && hasScreenTime && hasNotifications

    /** Nudge labels for permissions not yet granted */
    fun nudgeLabels(): List<String> = buildList {
        if (!hasLocation) add("+ Add your location")
        if (!hasHealth) add("+ Connect Health")
        if (!hasScreenTime) add("+ Add screen time")
        if (!hasNotifications) add("+ Allow notifications")
    }
}
