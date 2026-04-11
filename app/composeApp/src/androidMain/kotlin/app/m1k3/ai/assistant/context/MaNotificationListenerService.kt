package app.m1k3.ai.assistant.context

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Notification listener — captures content for M1K3's context intelligence.
 *
 * Reads the ACTUAL content of notifications (title, text, app name)
 * not just counts. This feeds into the embedding pipeline so M1K3 can
 * answer "what was that delivery notification?" via RAG.
 *
 * All data stays on-device in SharedPreferences. The cloud empires
 * will NEVER see these notifications.
 *
 * @see <a href="https://murphysig.dev">MurphySig</a>
 * Signed: kev + claude | confidence: 0.9 | context: notification content extraction
 */
class MaNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MaNotificationListener"
        const val PREFS = "ma_notifications"
        private const val MAX_STORED_NOTIFICATIONS = 20

        // System packages to filter out (noise, not signal)
        private val SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.launcher",
            "com.google.android.inputmethod",
            "com.android.providers",
            "com.android.settings"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        updateNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        updateNotifications()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateNotifications()
        Log.d(TAG, "Notification listener connected")
    }

    /**
     * Extract content from all active notifications and persist.
     *
     * Captures: app name, title, text, timestamp, priority.
     * Filters: ongoing notifications, system packages.
     * Stores: JSON array in SharedPreferences (capped at MAX_STORED_NOTIFICATIONS).
     */
    private fun updateNotifications() {
        try {
            val notifications = activeNotifications ?: return
            val filtered = notifications.filter { sbn ->
                !sbn.isOngoing && !isSystemPackage(sbn.packageName)
            }

            // Extract content from each notification
            val contentArray = JSONArray()
            filtered
                .sortedByDescending { it.postTime }
                .take(MAX_STORED_NOTIFICATIONS)
                .forEach { sbn ->
                    val extras = sbn.notification.extras
                    val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    val appName = getAppDisplayName(sbn.packageName)

                    contentArray.put(JSONObject().apply {
                        put("app", appName)
                        put("pkg", sbn.packageName)
                        put("title", title ?: "")
                        put("text", text ?: "")
                        put("time", sbn.postTime)
                        put("urgent", sbn.notification.priority >= Notification.PRIORITY_HIGH)
                    })
                }

            // Persist both counts and content
            val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("unread_count", filtered.size)
                .putBoolean("has_urgent", filtered.any {
                    it.notification.priority >= Notification.PRIORITY_HIGH
                })
                .putString("content_json", contentArray.toString())
                .apply()

        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notifications: ${e.message}")
        }
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return SYSTEM_PACKAGES.any { packageName.contains(it) }
    }

    private fun getAppDisplayName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
                .replaceFirstChar { it.uppercase() }
        }
    }
}
