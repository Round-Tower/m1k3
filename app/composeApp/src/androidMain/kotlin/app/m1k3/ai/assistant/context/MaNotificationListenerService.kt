package app.m1k3.ai.assistant.context

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Notification listener service — counts active notifications for context.
 *
 * Declared in AndroidManifest with BIND_NOTIFICATION_LISTENER_SERVICE permission.
 * User must explicitly grant access in Settings → Notifications → Notification access.
 *
 * Stores the count in SharedPreferences so UserContextManager can read it
 * without requiring the Service to be running at query time.
 */
class MaNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MaNotificationListener"
        private const val PREFS = "ma_notifications"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        updateCount()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        updateCount()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateCount()
        Log.d(TAG, "Notification listener connected")
    }

    private fun updateCount() {
        try {
            val notifications = activeNotifications ?: return
            val filtered = notifications.filter {
                !it.isOngoing && !it.packageName.contains("android")
            }
            val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("unread_count", filtered.size)
                .putBoolean("has_urgent", filtered.any {
                    it.notification.priority >= android.app.Notification.PRIORITY_HIGH
                })
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification count: ${e.message}")
        }
    }
}
