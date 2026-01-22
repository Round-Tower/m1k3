package app.m1k3.ai.assistant.tools.executors

import android.content.Context
import android.content.Intent
import android.provider.Settings
import app.m1k3.ai.assistant.domain.tools.ToolCall
import app.m1k3.ai.assistant.domain.tools.ToolError
import app.m1k3.ai.assistant.domain.tools.ToolResult
import app.m1k3.ai.assistant.domain.tools.services.ToolExecutor

/**
 * Open Settings Executor - Opens device settings
 *
 * Can open specific settings sections like WiFi, Bluetooth, etc.
 */
class OpenSettingsExecutor(
    private val context: Context
) : ToolExecutor {

    override val toolId = "open_settings"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun execute(call: ToolCall): ToolResult {
        val startTime = System.currentTimeMillis()

        val section = call.getArgumentOrDefault("section", "main")

        val action = when (section.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }

        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

            val sectionName = if (section == "main") "main settings" else "$section settings"
            ToolResult.Success(
                toolId = toolId,
                output = "Opened $sectionName",
                data = mapOf("section" to section),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            ToolResult.Failure(
                toolId = toolId,
                error = ToolError.ExecutionFailed(e),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    override fun validateArguments(arguments: Map<String, String>): Result<Unit> {
        val section = arguments["section"]
        val validSections = listOf("main", "wifi", "bluetooth", "display", "sound", "battery", "apps")
        if (section != null && section.lowercase() !in validSections) {
            return Result.failure(
                IllegalArgumentException("section must be one of: ${validSections.joinToString()}")
            )
        }
        return Result.success(Unit)
    }
}
