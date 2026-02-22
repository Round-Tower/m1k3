package app.m1k3.ai.assistant.platform

import app.m1k3.ai.domain.platform.DateTimeProviderInterface

/**
 * DateTimeProvider - Platform abstraction for date/time information.
 *
 * Provides temporal and locale context for AI prompts:
 * - Current time and date for context-aware responses
 * - Day of week for time-based greetings
 * - Timezone and locale for formatting
 *
 * **Usage:**
 * ```kotlin
 * val dateTime = DateTimeProvider()  // Android/iOS
 * val hour = dateTime.getCurrentHour()
 * val greeting = when (hour) {
 *     in 5..11 -> "Good morning!"
 *     in 12..17 -> "Good afternoon!"
 *     else -> "Good evening!"
 * }
 * ```
 */
expect class DateTimeProvider() : DateTimeProviderInterface {
    /**
     * Get current hour in 24-hour format.
     *
     * @return Hour 0-23
     */
    override fun getCurrentHour(): Int

    /**
     * Get current day of week name.
     *
     * @return Day name (e.g., "Friday", "Monday")
     */
    override fun getDayOfWeekName(): String

    /**
     * Get formatted date string.
     *
     * @return Date string (e.g., "January 24, 2026")
     */
    override fun getFormattedDate(): String

    /**
     * Get formatted time string.
     *
     * @return Time string (e.g., "2:30 PM")
     */
    override fun getFormattedTime(): String

    /**
     * Get short timezone identifier.
     *
     * @return Timezone abbreviation (e.g., "PST", "EST", "UTC")
     */
    override fun getTimeZoneShort(): String

    /**
     * Get locale display name.
     *
     * @return Locale identifier (e.g., "en-US", "de-DE")
     */
    override fun getLocaleDisplayName(): String
}
