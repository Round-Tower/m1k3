package app.m1k3.ai.domain.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for DateTimeProviderInterface.
 *
 * Uses a mock implementation to verify the interface contract.
 */
class DateTimeProviderInterfaceTest {

    private class MockDateTimeProvider(
        private val hour: Int,
        private val dayOfWeek: String,
        private val formattedDate: String,
        private val formattedTime: String,
        private val timeZone: String,
        private val locale: String
    ) : DateTimeProviderInterface {
        override fun getCurrentHour(): Int = hour
        override fun getDayOfWeekName(): String = dayOfWeek
        override fun getFormattedDate(): String = formattedDate
        override fun getFormattedTime(): String = formattedTime
        override fun getTimeZoneShort(): String = timeZone
        override fun getLocaleDisplayName(): String = locale
    }

    @Test
    fun `getCurrentHour returns configured value`() {
        val provider = MockDateTimeProvider(
            hour = 14,
            dayOfWeek = "Friday",
            formattedDate = "January 24, 2026",
            formattedTime = "2:30 PM",
            timeZone = "PST",
            locale = "en-US"
        )

        assertEquals(14, provider.getCurrentHour())
    }

    @Test
    fun `getDayOfWeekName returns configured value`() {
        val provider = MockDateTimeProvider(
            hour = 10,
            dayOfWeek = "Monday",
            formattedDate = "January 20, 2026",
            formattedTime = "10:00 AM",
            timeZone = "EST",
            locale = "en-US"
        )

        assertEquals("Monday", provider.getDayOfWeekName())
    }

    @Test
    fun `getFormattedDate returns configured value`() {
        val provider = MockDateTimeProvider(
            hour = 9,
            dayOfWeek = "Tuesday",
            formattedDate = "December 25, 2025",
            formattedTime = "9:00 AM",
            timeZone = "UTC",
            locale = "en-GB"
        )

        assertEquals("December 25, 2025", provider.getFormattedDate())
    }

    @Test
    fun `getFormattedTime returns configured value`() {
        val provider = MockDateTimeProvider(
            hour = 23,
            dayOfWeek = "Saturday",
            formattedDate = "January 1, 2026",
            formattedTime = "11:59 PM",
            timeZone = "JST",
            locale = "ja-JP"
        )

        assertEquals("11:59 PM", provider.getFormattedTime())
    }

    @Test
    fun `getTimeZoneShort returns configured value`() {
        val provider = MockDateTimeProvider(
            hour = 12,
            dayOfWeek = "Wednesday",
            formattedDate = "June 15, 2026",
            formattedTime = "12:00 PM",
            timeZone = "PDT",
            locale = "en-US"
        )

        assertEquals("PDT", provider.getTimeZoneShort())
    }

    @Test
    fun `getLocaleDisplayName returns configured value`() {
        val provider = MockDateTimeProvider(
            hour = 8,
            dayOfWeek = "Thursday",
            formattedDate = "March 10, 2026",
            formattedTime = "8:30 AM",
            timeZone = "CET",
            locale = "de-DE"
        )

        assertEquals("de-DE", provider.getLocaleDisplayName())
    }

    @Test
    fun `hour range is valid 0-23`() {
        val morningProvider = MockDateTimeProvider(6, "Monday", "", "", "", "")
        val afternoonProvider = MockDateTimeProvider(14, "Monday", "", "", "", "")
        val nightProvider = MockDateTimeProvider(23, "Monday", "", "", "", "")

        assertTrue(morningProvider.getCurrentHour() in 0..23)
        assertTrue(afternoonProvider.getCurrentHour() in 0..23)
        assertTrue(nightProvider.getCurrentHour() in 0..23)
    }
}
