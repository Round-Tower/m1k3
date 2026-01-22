package app.m1k3.ai.assistant.tools.executors

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.m1k3.ai.assistant.domain.tools.ToolCall
import app.m1k3.ai.assistant.domain.tools.ToolError
import app.m1k3.ai.assistant.domain.tools.ToolResult
import app.m1k3.ai.assistant.domain.tools.services.ToolExecutor

/**
 * Open Browser Executor - Opens URLs in the web browser
 *
 * Uses ACTION_VIEW intent to open URLs in the default browser.
 */
class OpenBrowserExecutor(
    private val context: Context
) : ToolExecutor {

    override val toolId = "open_browser"

    override suspend fun isAvailable(): Boolean = true // Browser is always available

    override suspend fun execute(call: ToolCall): ToolResult {
        val startTime = System.currentTimeMillis()

        val url = call.getArgumentOrDefault("url", "https://google.com")

        // Ensure URL has a scheme
        val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

            ToolResult.Success(
                toolId = toolId,
                output = "Opening $normalizedUrl in browser",
                data = mapOf("url" to normalizedUrl),
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
        val url = arguments["url"]
        if (url != null && url.contains(" ")) {
            return Result.failure(IllegalArgumentException("URL cannot contain spaces"))
        }
        return Result.success(Unit)
    }
}
