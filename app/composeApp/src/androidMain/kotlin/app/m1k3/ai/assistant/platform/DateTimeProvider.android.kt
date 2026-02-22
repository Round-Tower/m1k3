package app.m1k3.ai.assistant.platform

import app.m1k3.ai.domain.platform.DateTimeProviderInterface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Android implementation of DateTimeProvider.
 *
 * Uses Java date/time APIs for current time and locale information.
 */
actual class DateTimeProvider : DateTimeProviderInterface {

    /**
     * Get current hour in 24-hour format.
     */
    actual override fun getCurrentHour(): Int {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }

    /**
     * Get current day of week name.
     */
    actual override fun getDayOfWeekName(): String {
        val format = SimpleDateFormat("EEEE", Locale.getDefault())
        return format.format(Calendar.getInstance().time)
    }

    /**
     * Get formatted date string.
     */
    actual override fun getFormattedDate(): String {
        val format = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        return format.format(Calendar.getInstance().time)
    }

    /**
     * Get formatted time string.
     */
    actual override fun getFormattedTime(): String {
        val format = SimpleDateFormat("h:mm a", Locale.getDefault())
        return format.format(Calendar.getInstance().time)
    }

    /**
     * Get short timezone identifier.
     */
    actual override fun getTimeZoneShort(): String {
        val tz = TimeZone.getDefault()
        val isDst = tz.inDaylightTime(Calendar.getInstance().time)
        return tz.getDisplayName(isDst, TimeZone.SHORT, Locale.getDefault())
    }

    /**
     * Get locale display name.
     */
    actual override fun getLocaleDisplayName(): String {
        val locale = Locale.getDefault()
        return "${locale.language}-${locale.country}"
    }
}
