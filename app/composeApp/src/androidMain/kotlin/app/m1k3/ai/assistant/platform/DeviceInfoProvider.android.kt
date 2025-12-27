package app.m1k3.ai.assistant.platform

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build

/**
 * Android implementation of DeviceInfoProvider.
 *
 * Extracts device information from Android system services.
 */
actual class DeviceInfoProvider(
    private val context: Context
) {
    /**
     * Get device RAM in gigabytes from ActivityManager.
     */
    actual fun getDeviceRamGB(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.totalMem / (1024L * 1024L * 1024L)).toInt()
    }

    /**
     * Get device model from Build.MODEL.
     */
    actual fun getDeviceModel(): String {
        return Build.MODEL
    }

    /**
     * Get current battery level from BatteryManager.
     *
     * @return Battery level 0-100, or null if unavailable
     */
    actual fun getBatteryLevel(): Int? {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it > 0 }
    }
}
