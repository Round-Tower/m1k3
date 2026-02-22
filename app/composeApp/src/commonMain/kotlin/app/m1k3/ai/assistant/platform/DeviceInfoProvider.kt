package app.m1k3.ai.assistant.platform

import app.m1k3.ai.assistant.config.GenerationConstants

/**
 * DeviceInfoProviderInterface.
 *
 * @deprecated Use app.m1k3.ai.domain.platform.DeviceInfoProviderInterface instead.
 * This typealias exists for backward compatibility.
 */
typealias DeviceInfoProviderInterface = app.m1k3.ai.domain.platform.DeviceInfoProviderInterface

/**
 * DeviceInfoProvider - Platform abstraction for device information.
 *
 * Provides device capabilities for adaptive generation:
 * - RAM for token limit scaling
 * - Device model for debugging
 * - Battery level for power-aware generation
 *
 * **Usage:**
 * ```kotlin
 * val deviceInfo = DeviceInfoProvider(context)  // Android
 * val ramGB = deviceInfo.getDeviceRamGB()
 * val tier = deviceInfo.getDeviceTier()
 * ```
 */
expect class DeviceInfoProvider : DeviceInfoProviderInterface {
    /**
     * Get device RAM in gigabytes.
     * Used for adaptive token limit calculation.
     *
     * @return RAM in GB (e.g., 8 for 8GB device)
     */
    override fun getDeviceRamGB(): Int

    /**
     * Get device model name.
     * Used for debugging and logging.
     *
     * @return Device model (e.g., "Pixel 8 Pro")
     */
    override fun getDeviceModel(): String

    /**
     * Get current battery level percentage.
     * Can be used for power-aware generation.
     *
     * @return Battery level 0-100, or null if unavailable
     */
    override fun getBatteryLevel(): Int?
}

/**
 * DeviceTier.
 *
 * @deprecated Use app.m1k3.ai.domain.platform.DeviceTier instead.
 * This typealias exists for backward compatibility.
 */
typealias DeviceTier = app.m1k3.ai.domain.platform.DeviceTier

/**
 * Extension to get device tier from RAM.
 */
fun DeviceInfoProviderInterface.getDeviceTier(): DeviceTier {
    val ramGB = getDeviceRamGB()
    return when {
        ramGB >= GenerationConstants.DeviceRam.FLAGSHIP -> DeviceTier.FLAGSHIP
        ramGB >= GenerationConstants.DeviceRam.HIGH_END -> DeviceTier.HIGH_END
        ramGB >= GenerationConstants.DeviceRam.MID_RANGE -> DeviceTier.MID_RANGE
        ramGB >= GenerationConstants.DeviceRam.BUDGET -> DeviceTier.BUDGET
        else -> DeviceTier.LOW_END
    }
}

/**
 * Extension to get memory topK based on device tier.
 */
fun DeviceInfoProviderInterface.getMemoryTopK(): Int {
    return when (getDeviceTier()) {
        DeviceTier.FLAGSHIP -> GenerationConstants.MemoryTopK.FLAGSHIP
        DeviceTier.HIGH_END -> GenerationConstants.MemoryTopK.HIGH_END
        DeviceTier.MID_RANGE -> GenerationConstants.MemoryTopK.MID_RANGE
        DeviceTier.BUDGET -> GenerationConstants.MemoryTopK.BUDGET
        DeviceTier.LOW_END -> GenerationConstants.MemoryTopK.BUDGET
    }
}
