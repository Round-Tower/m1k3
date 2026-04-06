package app.m1k3.ai.assistant.context

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import app.m1k3.ai.domain.context.NotificationContext

/**
 * Provides notification context.
 *
 * Notification access requires the user to grant it in:
 * Settings → Notifications → Notification access
 *
 * We check if access is granted; if not, return null gracefully.
 * Actual notification count is tracked by MaNotificationListenerService
 * which must be declared in AndroidManifest.
 */
class NotificationContextProvider(private val context: Context) {

    companion object {
        private const val TAG = "NotificationProvider"
    }

    fun hasPermission(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName)
    }

    fun getNotifications(): NotificationContext? {
        if (!hasPermission()) {
            Log.d(TAG, "No notification listener access")
            return null
        }
        // Count is maintained by MaNotificationListenerService via SharedPreferences
        return try {
            val prefs = context.getSharedPreferences("ma_notifications", Context.MODE_PRIVATE)
            val count = prefs.getInt("unread_count", 0)
            val hasUrgent = prefs.getBoolean("has_urgent", false)
            NotificationContext(unreadCount = count, hasUrgent = hasUrgent)
        } catch (e: Exception) {
            Log.w(TAG, "Notification count read failed: ${e.message}")
            null
        }
    }

    /** Deep-links to notification access settings */
    fun openNotificationAccessSettings(): android.content.Intent =
        android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
}
