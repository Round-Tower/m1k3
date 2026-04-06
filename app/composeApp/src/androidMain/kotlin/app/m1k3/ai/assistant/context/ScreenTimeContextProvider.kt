package app.m1k3.ai.assistant.context

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import app.m1k3.ai.domain.context.AppUsage
import app.m1k3.ai.domain.context.ScreenTimeContext
import java.util.Calendar

/**
 * Provides screen time context from Android UsageStatsManager.
 *
 * Requires PACKAGE_USAGE_STATS — a "special" permission the user
 * grants via Settings → Digital Wellbeing / Special App Access.
 * Cannot be runtime-requested; must deep-link to settings.
 *
 * Returns null if permission not granted.
 */
class ScreenTimeContextProvider(private val context: Context) {

    companion object {
        private const val TAG = "ScreenTimeProvider"
        private val IGNORED_PACKAGES = setOf(
            "android", "com.android.launcher3", "com.android.systemui",
            "com.google.android.inputmethod.latin"
        )
    }

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getScreenTime(): ScreenTimeContext? {
        if (!hasPermission()) {
            Log.d(TAG, "No PACKAGE_USAGE_STATS permission")
            return null
        }

        return try {
            val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = calendar.timeInMillis
            val endMs = System.currentTimeMillis()

            val stats = usageManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startMs, endMs
            )

            if (stats.isNullOrEmpty()) return ScreenTimeContext(0)

            val pm = context.packageManager
            val topApps = stats
                .filter { it.packageName !in IGNORED_PACKAGES && it.totalTimeInForeground > 60_000 }
                .sortedByDescending { it.totalTimeInForeground }
                .take(3)
                .map { stat ->
                    val appName = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
                    } catch (_: Exception) { stat.packageName.substringAfterLast('.') }
                    AppUsage(
                        packageName = stat.packageName,
                        displayName = appName,
                        minutesToday = (stat.totalTimeInForeground / 60_000).toInt()
                    )
                }

            val totalMinutes = stats
                .filter { it.packageName !in IGNORED_PACKAGES }
                .sumOf { it.totalTimeInForeground / 60_000 }
                .toInt()

            ScreenTimeContext(todayMinutes = totalMinutes, topApps = topApps)

        } catch (e: Exception) {
            Log.w(TAG, "Screen time fetch failed: ${e.message}")
            null
        }
    }
}
