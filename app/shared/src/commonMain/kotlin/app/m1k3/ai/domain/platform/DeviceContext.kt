package app.m1k3.ai.domain.platform

/**
 * Device context for AI prompt enrichment.
 *
 * Packages all device and temporal context for injection into system prompts.
 * This gives the AI awareness of the user's environment.
 *
 * @property hour Current hour 0-23 for time-based greeting
 * @property dayOfWeek Day name (e.g., "Friday")
 * @property formattedDate Date string (e.g., "January 24, 2026")
 * @property formattedTime Time string (e.g., "2:30 PM")
 * @property timeZone Timezone abbreviation (e.g., "PST")
 * @property locale Locale identifier (e.g., "en-US")
 * @property batteryLevel Battery 0-100, null if unavailable
 * @property deviceModel Device model name (e.g., "Pixel 8 Pro")
 * @property deviceTier Device capability tier for context budget
 */
data class DeviceContext(
    val hour: Int,
    val dayOfWeek: String,
    val formattedDate: String,
    val formattedTime: String,
    val timeZone: String,
    val locale: String,
    val batteryLevel: Int?,
    val deviceModel: String,
    val deviceTier: DeviceTier
) {
    companion object {
        /**
         * Create DeviceContext from providers.
         *
         * Convenience factory that combines DateTimeProvider and DeviceInfoProvider.
         */
        fun from(
            dateTimeProvider: DateTimeProviderInterface,
            deviceInfoProvider: DeviceInfoProviderInterface,
            deviceTier: DeviceTier
        ): DeviceContext = DeviceContext(
            hour = dateTimeProvider.getCurrentHour(),
            dayOfWeek = dateTimeProvider.getDayOfWeekName(),
            formattedDate = dateTimeProvider.getFormattedDate(),
            formattedTime = dateTimeProvider.getFormattedTime(),
            timeZone = dateTimeProvider.getTimeZoneShort(),
            locale = dateTimeProvider.getLocaleDisplayName(),
            batteryLevel = deviceInfoProvider.getBatteryLevel(),
            deviceModel = deviceInfoProvider.getDeviceModel(),
            deviceTier = deviceTier
        )
    }
}
