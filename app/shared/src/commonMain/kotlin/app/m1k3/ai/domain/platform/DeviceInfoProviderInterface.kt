package app.m1k3.ai.domain.platform

/**
 * Device Info Provider Interface - Abstract device information access.
 *
 * Provides device capabilities for adaptive configuration:
 * - RAM for token limit scaling
 * - Device model for debugging
 * - Battery level for power-aware generation
 *
 * Domain interface - Pure Kotlin, no platform dependencies.
 * Platform implementations provide actual device information.
 *
 * @see DeviceTier for RAM-based device classification
 */
interface DeviceInfoProviderInterface {
    /**
     * Get device RAM in gigabytes.
     *
     * Used for adaptive token limit calculation and memory-aware
     * feature configuration.
     *
     * @return RAM in GB (e.g., 8 for 8GB device)
     */
    fun getDeviceRamGB(): Int

    /**
     * Get device model name.
     *
     * Used for debugging, logging, and device-specific workarounds.
     *
     * @return Device model (e.g., "Pixel 8 Pro", "iPhone 15 Pro")
     */
    fun getDeviceModel(): String

    /**
     * Get current battery level percentage.
     *
     * Can be used for power-aware generation (reduce token limits
     * or disable features when battery is low).
     *
     * @return Battery level 0-100, or null if unavailable
     */
    fun getBatteryLevel(): Int?
}
