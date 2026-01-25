package app.m1k3.ai.domain.chat.services

import app.m1k3.ai.domain.platform.DeviceContext
import app.m1k3.ai.domain.platform.DeviceTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * Tests for DeviceContextFormatter.
 */
class DeviceContextFormatterTest {

    private val formatter = DeviceContextFormatter()

    private fun createContext(
        hour: Int = 14,
        dayOfWeek: String = "Friday",
        formattedDate: String = "January 24, 2026",
        formattedTime: String = "2:30 PM",
        timeZone: String = "PST",
        locale: String = "en-US",
        batteryLevel: Int? = 75,
        deviceModel: String = "Pixel 8 Pro",
        deviceTier: DeviceTier = DeviceTier.HIGH_END
    ) = DeviceContext(
        hour = hour,
        dayOfWeek = dayOfWeek,
        formattedDate = formattedDate,
        formattedTime = formattedTime,
        timeZone = timeZone,
        locale = locale,
        batteryLevel = batteryLevel,
        deviceModel = deviceModel,
        deviceTier = deviceTier
    )

    @Test
    fun `formatForSystemPrompt includes day of week`() {
        val context = createContext(dayOfWeek = "Monday")
        val result = formatter.formatForSystemPrompt(context)
        assertContains(result, "Monday")
    }

    @Test
    fun `formatForSystemPrompt includes date`() {
        val context = createContext(formattedDate = "March 15, 2026")
        val result = formatter.formatForSystemPrompt(context)
        assertContains(result, "March 15, 2026")
    }

    @Test
    fun `formatForSystemPrompt includes time and timezone`() {
        val context = createContext(formattedTime = "10:30 AM", timeZone = "EST")
        val result = formatter.formatForSystemPrompt(context)
        assertContains(result, "10:30 AM")
        assertContains(result, "EST")
    }

    @Test
    fun `formatForSystemPrompt includes device model`() {
        val context = createContext(deviceModel = "iPhone 15 Pro")
        val result = formatter.formatForSystemPrompt(context)
        assertContains(result, "iPhone 15 Pro")
    }

    @Test
    fun `formatForSystemPrompt includes device tier`() {
        val context = createContext(deviceTier = DeviceTier.FLAGSHIP)
        val result = formatter.formatForSystemPrompt(context)
        assertContains(result.lowercase(), "flagship")
    }

    @Test
    fun `formatForSystemPrompt includes battery level when present`() {
        val context = createContext(batteryLevel = 42)
        val result = formatter.formatForSystemPrompt(context)
        assertContains(result, "42%")
    }

    @Test
    fun `formatForSystemPrompt omits battery when null`() {
        val context = createContext(batteryLevel = null)
        val result = formatter.formatForSystemPrompt(context)
        assertFalse(result.contains("Battery"))
    }

    @Test
    fun `formatForSystemPrompt includes locale`() {
        val context = createContext(locale = "de-DE")
        val result = formatter.formatForSystemPrompt(context)
        assertContains(result, "de-DE")
    }

    @Test
    fun `formatForSystemPrompt wraps in brackets`() {
        val context = createContext()
        val result = formatter.formatForSystemPrompt(context)
        assert(result.startsWith("[")) { "Should start with [" }
        assert(result.endsWith("]")) { "Should end with ]" }
    }

    @Test
    fun `formatForSystemPrompt full output matches expected format`() {
        val context = createContext(
            dayOfWeek = "Friday",
            formattedDate = "January 24, 2026",
            formattedTime = "2:30 PM",
            timeZone = "PST",
            deviceModel = "Pixel 8 Pro",
            deviceTier = DeviceTier.HIGH_END,
            batteryLevel = 75,
            locale = "en-US"
        )

        val result = formatter.formatForSystemPrompt(context)

        // Verify key components are present
        assertContains(result, "Friday")
        assertContains(result, "January 24, 2026")
        assertContains(result, "2:30 PM")
        assertContains(result, "PST")
        assertContains(result, "Pixel 8 Pro")
        assertContains(result, "75%")
        assertContains(result, "en-US")
    }

    @Test
    fun `formatDeviceTier returns human readable tier names`() {
        assertEquals("flagship", formatter.formatDeviceTier(DeviceTier.FLAGSHIP))
        assertEquals("high-end", formatter.formatDeviceTier(DeviceTier.HIGH_END))
        assertEquals("mid-range", formatter.formatDeviceTier(DeviceTier.MID_RANGE))
        assertEquals("budget", formatter.formatDeviceTier(DeviceTier.BUDGET))
        assertEquals("low-end", formatter.formatDeviceTier(DeviceTier.LOW_END))
    }
}
