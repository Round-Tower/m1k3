package app.m1k3.ai.assistant.context

import android.content.Context
import android.util.Log
import app.m1k3.ai.domain.context.UserContext
import app.m1k3.ai.domain.context.UserContextProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalTime

/**
 * Aggregates all Android context providers into a single UserContext.
 *
 * All providers are queried in parallel — total latency is bounded by
 * the slowest provider (typically location, ~500ms). Others are ~instant.
 *
 * Each provider is fully independent. If location fails, health still works.
 * Zero throws — every provider catches its own exceptions.
 *
 * "The more you share, the more M1K3 understands —
 *  but it never leaves your phone."
 */
class UserContextManager(context: Context) : UserContextProvider {

    companion object {
        private const val TAG = "UserContextManager"
    }

    private val locationProvider = LocationContextProvider(context)
    private val healthProvider = HealthContextProvider(context)
    private val screenTimeProvider = ScreenTimeContextProvider(context)
    private val nameProvider = UserNameProvider(context)
    private val notificationProvider = NotificationContextProvider(context)
    private val weatherProvider = WeatherContextProvider()

    override suspend fun getContext(): UserContext = coroutineScope {
        Log.d(TAG, "Fetching user context...")

        // Parallel fetch — all independent
        val locationDeferred = async { locationProvider.getLocation() }
        val healthDeferred = async { healthProvider.getHealth() }

        // These are synchronous (no I/O)
        val screenTime = screenTimeProvider.getScreenTime()
        val userName = nameProvider.getUserFirstName()
        val notifications = notificationProvider.getNotifications()

        val location = locationDeferred.await()
        val health = healthDeferred.await()

        // Weather — fetch in parallel with location, uses location result
        val weather = location?.let { loc ->
            try { weatherProvider.getWeather(loc) } catch (_: Exception) { null }
        }

        val ctx = UserContext(
            hourOfDay = LocalTime.now().hour,
            userName = userName,
            location = location,
            health = health,
            screenTime = screenTime,
            notifications = notifications,
            weather = weather
        )

        Log.d(TAG, buildDebugSummary(ctx))
        ctx
    }

    override fun hasBasicContext(): Boolean {
        // Name is available without any permissions on most devices
        return nameProvider.getUserFirstName() != null
    }

    /** Which permissions are currently granted — for settings UI */
    fun getPermissionStatus(): ContextPermissionStatus {
        return ContextPermissionStatus(
            hasLocation = locationProvider.hasPermission(),
            hasHealth = healthProvider.isAvailable(),
            hasScreenTime = screenTimeProvider.hasPermission(),
            hasNotifications = notificationProvider.hasPermission()
        )
    }

    private fun buildDebugSummary(ctx: UserContext): String = buildString {
        append("UserContext: hour=${ctx.hourOfDay}")
        ctx.userName?.let { append(", name=$it") }
        ctx.location?.let { append(", location=${it.displayName}") }
        ctx.health?.let { h ->
            h.stepsToday?.let { append(", steps=$it") }
            h.sleepLastNightMinutes?.let { append(", sleep=${it}min") }
        }
        ctx.screenTime?.let { append(", screenTime=${it.todayMinutes}min") }
        ctx.notifications?.let { append(", notifications=${it.unreadCount}") }
    }
}

data class ContextPermissionStatus(
    val hasLocation: Boolean,
    val hasHealth: Boolean,
    val hasScreenTime: Boolean,
    val hasNotifications: Boolean
) {
    val grantedCount: Int
        get() = listOf(hasLocation, hasHealth, hasScreenTime, hasNotifications).count { it }

    val allGranted: Boolean
        get() = hasLocation && hasHealth && hasScreenTime && hasNotifications
}
