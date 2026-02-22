package app.m1k3.ai.assistant.tools.executors

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import app.m1k3.ai.domain.tools.ToolCall
import app.m1k3.ai.domain.tools.ToolError
import app.m1k3.ai.domain.tools.ToolResult
import app.m1k3.ai.domain.tools.services.ToolExecutor

/**
 * Set Alarm Executor - Creates an alarm using the system alarm app
 *
 * Uses AlarmClock.ACTION_SET_ALARM intent which is handled by the
 * default clock app. No special permissions required.
 *
 * Parameters:
 * - hour: Hour (0-23)
 * - minute: Minute (0-59)
 * - message: Optional label for the alarm
 */
class SetAlarmExecutor(
    private val context: Context
) : ToolExecutor {

    override val toolId = "set_alarm"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun execute(call: ToolCall): ToolResult {
        val startTime = System.currentTimeMillis()

        val hourStr = call.getArgument("hour")
            ?: return ToolResult.Failure(
                toolId = toolId,
                error = ToolError.InvalidArguments("hour parameter is required"),
                executionTimeMs = System.currentTimeMillis() - startTime
            )

        val minuteStr = call.getArgumentOrDefault("minute", "0")
        val message = call.getArgument("message")

        val hour = hourStr.toIntOrNull()
        val minute = minuteStr.toIntOrNull()

        if (hour == null || hour !in 0..23) {
            return ToolResult.Failure(
                toolId = toolId,
                error = ToolError.InvalidArguments("hour must be 0-23"),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        if (minute == null || minute !in 0..59) {
            return ToolResult.Failure(
                toolId = toolId,
                error = ToolError.InvalidArguments("minute must be 0-59"),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                if (message != null) {
                    putExtra(AlarmClock.EXTRA_MESSAGE, message)
                }
                putExtra(AlarmClock.EXTRA_SKIP_UI, false) // Show UI for confirmation
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

            val timeStr = formatTime(hour, minute)
            val output = if (message != null) {
                "Setting alarm for $timeStr: $message"
            } else {
                "Setting alarm for $timeStr"
            }

            ToolResult.Success(
                toolId = toolId,
                output = output,
                data = mapOf(
                    "hour" to hour,
                    "minute" to minute,
                    "message" to (message ?: "")
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

    override fun validateArguments(arguments: Map<String, String>): Result<Unit> {
        val hour = arguments["hour"]
        if (hour == null) {
            return Result.failure(IllegalArgumentException("hour parameter is required"))
        }
        val hourInt = hour.toIntOrNull()
        if (hourInt == null || hourInt !in 0..23) {
            return Result.failure(IllegalArgumentException("hour must be 0-23"))
        }

        val minute = arguments["minute"]
        if (minute != null) {
            val minuteInt = minute.toIntOrNull()
            if (minuteInt == null || minuteInt !in 0..59) {
                return Result.failure(IllegalArgumentException("minute must be 0-59"))
            }
        }

        return Result.success(Unit)
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "$displayHour:${minute.toString().padStart(2, '0')} $amPm"
    }
}
