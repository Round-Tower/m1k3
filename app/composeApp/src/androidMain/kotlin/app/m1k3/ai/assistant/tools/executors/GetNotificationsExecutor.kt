package app.m1k3.ai.assistant.tools.executors

import android.content.Context
import app.m1k3.ai.assistant.context.NotificationContextProvider
import app.m1k3.ai.domain.tools.ToolCall
import app.m1k3.ai.domain.tools.ToolError
import app.m1k3.ai.domain.tools.ToolResult
import app.m1k3.ai.domain.tools.services.ToolExecutor

/**
 * Get Notifications Executor — reads actual notification content.
 *
 * The villain reads EVERY letter. Not just counts — titles, text, app names.
 * "What notifications did I miss?" now has a real answer.
 */
class GetNotificationsExecutor(
    private val context: Context
) : ToolExecutor {

    override val toolId = "get_notifications"

    private val provider = NotificationContextProvider(context)

    override suspend fun isAvailable(): Boolean = provider.hasPermission()

    override suspend fun execute(call: ToolCall): ToolResult {
        val startTime = System.currentTimeMillis()
        return try {
            val notifications = provider.getNotifications()
                ?: return ToolResult.Failure(
                    toolId = toolId,
                    error = ToolError.PermissionDenied("Notification access not granted"),
                    executionTimeMs = System.currentTimeMillis() - startTime
                )

            val output = if (notifications.recentNotifications.isEmpty()) {
                "You have ${notifications.unreadCount} notification${if (notifications.unreadCount != 1) "s" else ""} (no content available)."
            } else {
                buildString {
                    appendLine("${notifications.unreadCount} notification${if (notifications.unreadCount != 1) "s" else ""}:")
                    notifications.recentNotifications.take(10).forEach { n ->
                        appendLine("• ${n.summary}")
                    }
                }
            }

            ToolResult.Success(
                toolId = toolId,
                output = output,
                data = mapOf(
                    "count" to notifications.unreadCount,
                    "urgent" to notifications.hasUrgent,
                    "notifications" to notifications.recentNotifications.map { it.summary }
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
        Result.success(Unit)
}
