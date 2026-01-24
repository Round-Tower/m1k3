package app.m1k3.ai.domain.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for DeviceInfoProviderInterface.
 *
 * Uses a mock implementation to verify the interface contract.
 */
class DeviceInfoProviderInterfaceTest {

    private class MockDeviceInfoProvider(
        private val ramGB: Int,
        private val model: String,
        private val batteryLevel: Int?
    ) : DeviceInfoProviderInterface {
        override fun getDeviceRamGB(): Int = ramGB
        override fun getDeviceModel(): String = model
        override fun getBatteryLevel(): Int? = batteryLevel
    }

    @Test
    fun `getDeviceRamGB returns configured value`() {
        val provider = MockDeviceInfoProvider(
            ramGB = 8,
            model = "Test Device",
            batteryLevel = 100
        )

        assertEquals(8, provider.getDeviceRamGB())
    }

    @Test
    fun `getDeviceModel returns configured value`() {
        val provider = MockDeviceInfoProvider(
            ramGB = 8,
            model = "Pixel 8 Pro",
            batteryLevel = 100
        )

        assertEquals("Pixel 8 Pro", provider.getDeviceModel())
    }

    @Test
    fun `getBatteryLevel returns configured value`() {
        val provider = MockDeviceInfoProvider(
            ramGB = 8,
            model = "Test Device",
            batteryLevel = 75
        )

        assertEquals(75, provider.getBatteryLevel())
    }

    @Test
    fun `getBatteryLevel can return null`() {
        val provider = MockDeviceInfoProvider(
            ramGB = 8,
            model = "Test Device",
            batteryLevel = null
        )

        assertNull(provider.getBatteryLevel())
    }

    @Test
    fun `interface can be implemented with different values`() {
        val lowEndDevice = MockDeviceInfoProvider(2, "Budget Phone", 50)
        val flagshipDevice = MockDeviceInfoProvider(16, "Flagship Pro", 80)

        assertTrue(lowEndDevice.getDeviceRamGB() < flagshipDevice.getDeviceRamGB())
    }
}
