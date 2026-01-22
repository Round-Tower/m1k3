package app.m1k3.ai.assistant.tools.executors

import app.m1k3.ai.assistant.domain.tools.ToolCall
import app.m1k3.ai.assistant.domain.tools.ToolError
import app.m1k3.ai.assistant.domain.tools.ToolResult
import app.m1k3.ai.assistant.domain.tools.services.ToolExecutor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Get Time Executor - Gets current date and time
 *
 * Pure Kotlin implementation (no Android-specific APIs needed).
 * Supports multiple format options.
 */
class GetTimeExecutor : ToolExecutor {

    override val toolId = "get_current_time"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun execute(call: ToolCall): ToolResult {
        val startTime = System.currentTimeMillis()

        return try {
            val format = call.getArgumentOrDefault("format", "long")
            val now = Date()

            val (pattern, output) = when (format) {
                "short" -> "h:mm a" to SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
                "date" -> "EEEE, MMMM d, yyyy" to SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(now)
                else -> "EEEE, MMMM d, yyyy 'at' h:mm a" to SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(now)
            }

            ToolResult.Success(
                toolId = toolId,
                output = "Current time: $output",
                data = mapOf(
                    "formatted" to output,
                    "timestamp" to now.time,
                    "format" to format
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
        val format = arguments["format"]
        if (format != null && format !in listOf("short", "long", "date")) {
            return Result.failure(IllegalArgumentException("format must be 'short', 'long', or 'date'"))
        }
        return Result.success(Unit)
    }
}
