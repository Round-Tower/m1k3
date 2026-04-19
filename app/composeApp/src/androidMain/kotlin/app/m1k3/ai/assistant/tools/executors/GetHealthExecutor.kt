package app.m1k3.ai.assistant.tools.executors

import android.content.Context
import app.m1k3.ai.assistant.context.HealthContextProvider
import app.m1k3.ai.domain.tools.ToolCall
import app.m1k3.ai.domain.tools.ToolError
import app.m1k3.ai.domain.tools.ToolResult
import app.m1k3.ai.domain.tools.services.ToolExecutor

/**
 * Get Health Executor — reads Health Connect data (steps, sleep, heart rate, calories).
 */
class GetHealthExecutor(
    private val context: Context
) : ToolExecutor {

    override val toolId = "get_health"

    private val provider = HealthContextProvider(context)

    override suspend fun isAvailable(): Boolean = provider.isAvailable()

    override suspend fun execute(call: ToolCall): ToolResult {
        val startTime = System.currentTimeMillis()
        return try {
            val health = provider.getHealth()
                ?: return ToolResult.Failure(
                    toolId = toolId,
                    error = ToolError.PermissionDenied("Health Connect access not granted"),
                    executionTimeMs = System.currentTimeMillis() - startTime
                )

            if (health.isEmpty) {
                return ToolResult.Success(
                    toolId = toolId,
                    output = "No health data available. Health Connect permissions may need to be granted.",
                    data = emptyMap(),
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }

            val parts = mutableListOf<String>()
            val data = mutableMapOf<String, Any>()

            health.stepsToday?.let {
                parts += "$it steps today"
                data["steps"] = it
            }
            health.sleepLastNightMinutes?.let {
                val h = it / 60; val m = it % 60
                parts += "${h}h${if (m > 0) " ${m}m" else ""} sleep last night"
                data["sleep_minutes"] = it
            }
            health.heartRateLatestBpm?.let {
                parts += "${it}bpm heart rate"
                data["heart_rate_bpm"] = it
            }
            health.activeCaloriesToday?.let {
                parts += "$it active calories"
                data["active_calories"] = it
            }

            ToolResult.Success(
                toolId = toolId,
                output = parts.joinToString(", "),
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
