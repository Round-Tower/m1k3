package app.m1k3.ai.assistant.tools.executors

import android.content.Context
import android.os.BatteryManager
import app.m1k3.ai.assistant.domain.tools.ToolCall
import app.m1k3.ai.assistant.domain.tools.ToolError
import app.m1k3.ai.assistant.domain.tools.ToolResult
import app.m1k3.ai.assistant.domain.tools.services.ToolExecutor

/**
 * Battery Level Executor - Gets current battery status
 *
 * Uses BatteryManager to retrieve battery percentage and charging state.
 */
class BatteryLevelExecutor(
    private val context: Context
) : ToolExecutor {

    override val toolId = "get_battery_level"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun execute(call: ToolCall): ToolResult {
        val startTime = System.currentTimeMillis()

        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = batteryManager.isCharging

            val status = when {
                isCharging && level == 100 -> "fully charged"
                isCharging -> "charging"
                level <= 15 -> "low"
                level <= 5 -> "critical"
                else -> "discharging"
            }

            val output = "Battery is at $level%${if (isCharging) " (charging)" else ""}"

            ToolResult.Success(
                toolId = toolId,
                output = output,
                data = mapOf(
                    "level" to level,
                    "charging" to isCharging,
                    "status" to status
                ),
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

    override fun validateArguments(arguments: Map<String, String>): Result<Unit> =
        Result.success(Unit) // No arguments to validate
}
