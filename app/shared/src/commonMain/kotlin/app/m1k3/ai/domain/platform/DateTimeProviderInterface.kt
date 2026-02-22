package app.m1k3.ai.domain.platform

/**
 * DateTime Provider Interface - Abstract temporal and locale information access.
 *
 * Provides time/date context for:
 * - Context-aware AI prompts (knows current time, day)
 * - Time-based greetings (good morning/afternoon/evening)
 * - Locale-aware formatting
 *
 * Domain interface - Pure Kotlin, no platform dependencies.
 * Platform implementations provide actual device time/locale.
 *
 * @see DeviceInfoProviderInterface for hardware info
 */
interface DateTimeProviderInterface {
    /**
     * Get current hour in 24-hour format.
     *
     * Used for time-based greeting selection.
     *
     * @return Hour 0-23
     */
    fun getCurrentHour(): Int

    /**
     * Get current day of week name.
     *
     * @return Day name (e.g., "Friday", "Monday")
     */
    fun getDayOfWeekName(): String

    /**
     * Get formatted date string.
     *
     * @return Date string (e.g., "January 24, 2026")
     */
    fun getFormattedDate(): String

    /**
     * Get formatted time string.
     *
     * @return Time string (e.g., "2:30 PM")
     */
    fun getFormattedTime(): String

    /**
     * Get short timezone identifier.
     *
     * @return Timezone abbreviation (e.g., "PST", "EST", "UTC")
     */
    fun getTimeZoneShort(): String

    /**
     * Get locale display name.
     *
     * @return Locale identifier (e.g., "en-US", "de-DE")
     */
    fun getLocaleDisplayName(): String
}
