package app.m1k3.ai.domain.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for DeviceContext data class.
 */
class DeviceContextTest {

    private class MockDateTimeProvider(
        private val hour: Int = 14,
        private val dayOfWeek: String = "Friday",
        private val formattedDate: String = "January 24, 2026",
        private val formattedTime: String = "2:30 PM",
        private val timeZone: String = "PST",
        private val locale: String = "en-US"
    ) : DateTimeProviderInterface {
        override fun getCurrentHour(): Int = hour
        override fun getDayOfWeekName(): String = dayOfWeek
        override fun getFormattedDate(): String = formattedDate
        override fun getFormattedTime(): String = formattedTime
        override fun getTimeZoneShort(): String = timeZone
        override fun getLocaleDisplayName(): String = locale
    }

    private class MockDeviceInfoProvider(
        private val ramGB: Int = 8,
        private val model: String = "Pixel 8 Pro",
        private val batteryLevel: Int? = 75
    ) : DeviceInfoProviderInterface {
        override fun getDeviceRamGB(): Int = ramGB
        override fun getDeviceModel(): String = model
        override fun getBatteryLevel(): Int? = batteryLevel
    }

    @Test
    fun `from factory creates context from providers`() {
        val dateTimeProvider = MockDateTimeProvider()
        val deviceInfoProvider = MockDeviceInfoProvider()

        val context = DeviceContext.from(
            dateTimeProvider = dateTimeProvider,
            deviceInfoProvider = deviceInfoProvider,
            deviceTier = DeviceTier.HIGH_END
        )

        assertEquals(14, context.hour)
        assertEquals("Friday", context.dayOfWeek)
        assertEquals("January 24, 2026", context.formattedDate)
        assertEquals("2:30 PM", context.formattedTime)
        assertEquals("PST", context.timeZone)
        assertEquals("en-US", context.locale)
        assertEquals(75, context.batteryLevel)
        assertEquals("Pixel 8 Pro", context.deviceModel)
        assertEquals(DeviceTier.HIGH_END, context.deviceTier)
    }

    @Test
    fun `from factory handles null battery level`() {
        val dateTimeProvider = MockDateTimeProvider()
        val deviceInfoProvider = MockDeviceInfoProvider(batteryLevel = null)

        val context = DeviceContext.from(
            dateTimeProvider = dateTimeProvider,
            deviceInfoProvider = deviceInfoProvider,
            deviceTier = DeviceTier.BUDGET
        )

        assertNull(context.batteryLevel)
    }

    @Test
    fun `direct construction works`() {
        val context = DeviceContext(
            hour = 9,
            dayOfWeek = "Monday",
            formattedDate = "March 15, 2026",
            formattedTime = "9:00 AM",
            timeZone = "EST",
            locale = "en-GB",
            batteryLevel = 50,
            deviceModel = "iPhone 15 Pro",
            deviceTier = DeviceTier.FLAGSHIP
        )

        assertEquals(9, context.hour)
        assertEquals("Monday", context.dayOfWeek)
        assertEquals("iPhone 15 Pro", context.deviceModel)
        assertEquals(DeviceTier.FLAGSHIP, context.deviceTier)
    }
}
