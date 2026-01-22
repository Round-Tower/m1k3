package app.m1k3.ai.assistant.tools.executors

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import app.m1k3.ai.assistant.domain.tools.ToolCall
import app.m1k3.ai.assistant.domain.tools.ToolError
import app.m1k3.ai.assistant.domain.tools.ToolResult
import app.m1k3.ai.assistant.domain.tools.services.ToolExecutor

/**
 * Set Timer Executor - Creates a countdown timer
 *
 * Uses AlarmClock.ACTION_SET_TIMER intent which is handled by the
 * default clock app. No special permissions required.
 *
 * Parameters:
 * - seconds: Total seconds for the timer (or use minutes/hours)
 * - minutes: Minutes (combined with seconds)
 * - hours: Hours (combined with minutes/seconds)
 * - message: Optional label for the timer
 */
class SetTimerExecutor(
    private val context: Context
) : ToolExecutor {

    override val toolId = "set_timer"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun execute(call: ToolCall): ToolResult {
        val startTime = System.currentTimeMillis()

        // Parse time components
        val hours = call.getArgumentOrDefault("hours", "0").toIntOrNull() ?: 0
        val minutes = call.getArgumentOrDefault("minutes", "0").toIntOrNull() ?: 0
        val seconds = call.getArgumentOrDefault("seconds", "0").toIntOrNull() ?: 0
        val message = call.getArgument("message")

        // Calculate total seconds
        val totalSeconds = (hours * 3600) + (minutes * 60) + seconds

        if (totalSeconds <= 0) {
            return ToolResult.Failure(
                toolId = toolId,
                error = ToolError.InvalidArguments("Timer must be at least 1 second"),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        if (totalSeconds > 86400) { // 24 hours
            return ToolResult.Failure(
                toolId = toolId,
                error = ToolError.InvalidArguments("Timer cannot exceed 24 hours"),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, totalSeconds)
                if (message != null) {
                    putExtra(AlarmClock.EXTRA_MESSAGE, message)
                }
                putExtra(AlarmClock.EXTRA_SKIP_UI, false) // Show UI for confirmation
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

            val output = buildString {
                append("Setting timer for ")
                append(formatDuration(totalSeconds))
                if (message != null) {
                    append(": $message")
                }
            }

            ToolResult.Success(
                toolId = toolId,
                output = output,
                data = mapOf(
                    "totalSeconds" to totalSeconds,
                    "hours" to hours,
                    "minutes" to minutes,
                    "seconds" to seconds,
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
        val hours = arguments["hours"]?.toIntOrNull() ?: 0
        val minutes = arguments["minutes"]?.toIntOrNull() ?: 0
        val seconds = arguments["seconds"]?.toIntOrNull() ?: 0

        if (hours < 0 || minutes < 0 || seconds < 0) {
            return Result.failure(IllegalArgumentException("Time values cannot be negative"))
        }

        val totalSeconds = (hours * 3600) + (minutes * 60) + seconds
        if (totalSeconds <= 0) {
            return Result.failure(IllegalArgumentException("Timer must be at least 1 second"))
        }
        if (totalSeconds > 86400) {
            return Result.failure(IllegalArgumentException("Timer cannot exceed 24 hours"))
        }

        return Result.success(Unit)
    }

    private fun formatDuration(totalSeconds: Int): String = buildString {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        if (hours > 0) {
            append("$hours hour")
            if (hours > 1) append("s")
        }
        if (minutes > 0) {
            if (hours > 0) append(" ")
            append("$minutes minute")
            if (minutes > 1) append("s")
        }
        if (seconds > 0) {
            if (hours > 0 || minutes > 0) append(" ")
            append("$seconds second")
            if (seconds > 1) append("s")
        }
    }
}
