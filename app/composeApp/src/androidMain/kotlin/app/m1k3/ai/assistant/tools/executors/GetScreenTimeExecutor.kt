package app.m1k3.ai.assistant.tools.executors

import android.content.Context
import app.m1k3.ai.assistant.context.ScreenTimeContextProvider
import app.m1k3.ai.domain.tools.ToolCall
import app.m1k3.ai.domain.tools.ToolError
import app.m1k3.ai.domain.tools.ToolResult
import app.m1k3.ai.domain.tools.services.ToolExecutor

/**
 * Get Screen Time Executor — reads UsageStats (total + top apps).
 */
class GetScreenTimeExecutor(
    private val context: Context
) : ToolExecutor {

    override val toolId = "get_screen_time"

    private val provider = ScreenTimeContextProvider(context)

    override suspend fun isAvailable(): Boolean = provider.hasPermission()

    override suspend fun execute(call: ToolCall): ToolResult {
        val startTime = System.currentTimeMillis()
        return try {
            val screenTime = provider.getScreenTime()
                ?: return ToolResult.Failure(
                    toolId = toolId,
                    error = ToolError.PermissionDenied("Screen time access not granted"),
                    executionTimeMs = System.currentTimeMillis() - startTime
                )

            val h = screenTime.todayMinutes / 60
            val m = screenTime.todayMinutes % 60
            val total = if (h > 0) "${h}h ${m}m" else "${m}m"
            val data = mutableMapOf<String, Any>(
                "total_minutes" to screenTime.todayMinutes
            )

            val output = buildString {
                append("Screen time today: $total")
                if (screenTime.topApps.isNotEmpty()) {
                    appendLine()
                    append("Top apps: ")
                    append(screenTime.topApps.take(5).joinToString(", ") { app ->
                        "${app.displayName} (${app.minutesToday}m)"
                    })
                    data["top_apps"] = screenTime.topApps.map {
                        mapOf("name" to it.displayName, "minutes" to it.minutesToday)
                    }
                }
            }

            ToolResult.Success(
                toolId = toolId,
                output = output,
                data = data,
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
        Result.success(Unit)
}
