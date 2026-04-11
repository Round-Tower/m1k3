package app.m1k3.ai.assistant.context

import android.content.Context
import android.provider.Settings
import android.util.Log
import app.m1k3.ai.domain.context.NotificationContent
import app.m1k3.ai.domain.context.NotificationContext
import org.json.JSONArray

/**
 * Provides notification context including actual content.
 *
 * Reads from SharedPreferences where MaNotificationListenerService
 * persists notification data. Returns both counts AND content.
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
        return try {
            val prefs = context.getSharedPreferences(
                MaNotificationListenerService.PREFS,
                Context.MODE_PRIVATE
            )
            val count = prefs.getInt("unread_count", 0)
            val hasUrgent = prefs.getBoolean("has_urgent", false)
            val contentJson = prefs.getString("content_json", null)

            val recentNotifications = contentJson?.let { parseNotificationContent(it) }
                ?: emptyList()

            NotificationContext(
                unreadCount = count,
                hasUrgent = hasUrgent,
                recentNotifications = recentNotifications
            )
        } catch (e: Exception) {
            Log.w(TAG, "Notification read failed: ${e.message}")
            null
        }
    }

    private fun parseNotificationContent(json: String): List<NotificationContent> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                NotificationContent(
                    appName = obj.optString("app", "Unknown"),
                    title = obj.optString("title").ifBlank { null },
                    text = obj.optString("text").ifBlank { null },
                    timestamp = obj.optLong("time", 0L)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse notification content: ${e.message}")
            emptyList()
        }
    }

    fun openNotificationAccessSettings(): android.content.Intent =
        android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
}
