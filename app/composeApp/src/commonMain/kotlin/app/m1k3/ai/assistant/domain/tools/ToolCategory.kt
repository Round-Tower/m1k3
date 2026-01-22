package app.m1k3.ai.assistant.domain.tools

/**
 * Tool Category - Groups tools for organization and permission scoping
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Categories help with:
 * - UI organization (grouping related tools)
 * - Permission scoping (certain categories may need user consent)
 * - Tool discovery (AI can browse by category)
 *
 * @property displayName Human-readable category name for UI
 */
enum class ToolCategory(val displayName: String) {
    /**
     * System controls: flashlight, volume, brightness, Do Not Disturb
     */
    SYSTEM("System Controls"),

    /**
     * App launchers: camera, browser, settings, contacts
     */
    APPS("App Launcher"),

    /**
     * Device information queries: battery, time, storage, connectivity
     */
    DEVICE_INFO("Device Info"),

    /**
     * File operations: read/write notes, access photos
     * Note: Some operations may require confirmation
     */
    FILES("File Operations"),

    /**
     * Media controls: play/pause music, take photo, record audio
     * Future expansion for multimedia tools
     */
    MEDIA("Media"),

    /**
     * Communication: send message, make call
     * Future expansion - likely requires confirmation
     */
    COMMUNICATION("Communication")
}
