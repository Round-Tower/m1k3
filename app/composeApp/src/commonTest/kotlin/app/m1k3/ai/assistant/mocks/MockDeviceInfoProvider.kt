package app.m1k3.ai.assistant.mocks

import app.m1k3.ai.assistant.config.GenerationConstants
import app.m1k3.ai.assistant.platform.DeviceInfoProviderInterface

/**
 * Mock implementation of DeviceInfoProvider for testing.
 *
 * Allows tests to simulate different device tiers without
 * requiring actual Android context.
 *
 * **Usage:**
 * ```kotlin
 * val mockDevice = MockDeviceInfoProvider(ramGB = 8)
 * assertEquals(DeviceTier.HIGH_END, mockDevice.getDeviceTier())
 *
 * // Simulate battery drain
 * mockDevice.setBatteryLevel(15)
 * ```
 */
class MockDeviceInfoProvider(
    private var ramGB: Int = GenerationConstants.DeviceRam.MID_RANGE,
    private var model: String = "Mock Device",
    private var battery: Int? = 80
) : DeviceInfoProviderInterface {
    override fun getDeviceRamGB(): Int = ramGB

    override fun getDeviceModel(): String = model

    override fun getBatteryLevel(): Int? = battery

    /**
     * Set battery level for testing power-aware scenarios.
     */
    fun setBatteryLevel(level: Int?) {
        battery = level
    }

    // ===== Test Helpers =====

    /**
     * Set device to flagship tier (12GB+).
     */
    fun setFlagship() {
        ramGB = GenerationConstants.DeviceRam.FLAGSHIP
    }

    /**
     * Set device to high-end tier (8GB+).
     */
    fun setHighEnd() {
        ramGB = GenerationConstants.DeviceRam.HIGH_END
    }

    /**
     * Set device to mid-range tier (6GB+).
     */
    fun setMidRange() {
        ramGB = GenerationConstants.DeviceRam.MID_RANGE
    }

    /**
     * Set device to budget tier (4GB+).
     */
    fun setBudget() {
        ramGB = GenerationConstants.DeviceRam.BUDGET
    }

    /**
     * Set custom RAM value.
     */
    fun setRamGB(value: Int) {
        ramGB = value
    }

    /**
     * Set device model for logging tests.
     */
    fun setModel(value: String) {
        model = value
    }

    companion object {
        /**
         * Create a flagship device mock.
         */
        fun flagship() = MockDeviceInfoProvider(
            ramGB = GenerationConstants.DeviceRam.FLAGSHIP,
            model = "Pixel 8 Pro"
        )

        /**
         * Create a high-end device mock.
         */
        fun highEnd() = MockDeviceInfoProvider(
            ramGB = GenerationConstants.DeviceRam.HIGH_END,
            model = "Pixel 8"
        )

        /**
         * Create a mid-range device mock.
         */
        fun midRange() = MockDeviceInfoProvider(
            ramGB = GenerationConstants.DeviceRam.MID_RANGE,
            model = "Pixel 6a"
        )

        /**
         * Create a budget device mock.
         */
        fun budget() = MockDeviceInfoProvider(
            ramGB = GenerationConstants.DeviceRam.BUDGET,
            model = "Budget Phone"
        )
    }
}
