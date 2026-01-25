package app.m1k3.ai.domain.chat.services

import app.m1k3.ai.domain.platform.DeviceContext
import app.m1k3.ai.domain.platform.DeviceTier

/**
 * Formats device context for injection into AI system prompts.
 *
 * Produces a compact, readable string that gives the AI awareness
 * of the user's environment without consuming excessive tokens.
 */
class DeviceContextFormatter {

    /**
     * Format device context for system prompt injection.
     *
     * @param context The device context to format
     * @return Formatted string like:
     *         [Context: Friday, January 24, 2026 at 2:30 PM PST. Device: Pixel 8 Pro (high-end). Battery: 75%. Locale: en-US]
     */
    fun formatForSystemPrompt(context: DeviceContext): String {
        val parts = mutableListOf<String>()

        // Date and time
        parts.add("${context.dayOfWeek}, ${context.formattedDate} at ${context.formattedTime} ${context.timeZone}")

        // Device info
        parts.add("Device: ${context.deviceModel} (${formatDeviceTier(context.deviceTier)})")

        // Battery (if available)
        context.batteryLevel?.let {
            parts.add("Battery: $it%")
        }

        // Locale
        parts.add("Locale: ${context.locale}")

        return "[Context: ${parts.joinToString(". ")}]"
    }

    /**
     * Format device tier to human-readable string.
     *
     * @param tier The device tier
     * @return Lowercase tier name (e.g., "flagship", "high-end")
     */
    fun formatDeviceTier(tier: DeviceTier): String = when (tier) {
        DeviceTier.FLAGSHIP -> "flagship"
        DeviceTier.HIGH_END -> "high-end"
        DeviceTier.MID_RANGE -> "mid-range"
        DeviceTier.BUDGET -> "budget"
        DeviceTier.LOW_END -> "low-end"
    }
}
