package app.m1k3.ai.assistant.tools.executors

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.m1k3.ai.assistant.domain.tools.ToolCall
import app.m1k3.ai.assistant.domain.tools.ToolError
import app.m1k3.ai.assistant.domain.tools.ToolResult
import app.m1k3.ai.assistant.domain.tools.services.ToolExecutor
import java.net.URLEncoder

/**
 * Open Maps Executor - Opens maps app with location or directions
 *
 * Uses geo: URI scheme which is handled by any maps app.
 * Also supports Google Maps for directions.
 *
 * Parameters:
 * - query: Search query or address (e.g., "coffee shop", "123 Main St")
 * - destination: Destination for turn-by-turn directions
 * - mode: Navigation mode (driving, walking, bicycling, transit)
 *         Default: driving
 */
class OpenMapsExecutor(
    private val context: Context
) : ToolExecutor {

    override val toolId = "open_maps"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun execute(call: ToolCall): ToolResult {
        val startTime = System.currentTimeMillis()

        val query = call.getArgument("query")
        val destination = call.getArgument("destination")
        val mode = call.getArgumentOrDefault("mode", "driving")

        if (query == null && destination == null) {
            return ToolResult.Failure(
                toolId = toolId,
                error = ToolError.InvalidArguments("Either query or destination parameter is required"),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        return try {
            val uri = when {
                // Navigation to destination
                destination != null -> {
                    val modeCode = when (mode.lowercase()) {
                        "walking" -> "w"
                        "bicycling", "bike" -> "b"
                        "transit", "public" -> "r"
                        else -> "d" // driving
                    }
                    val encodedDest = URLEncoder.encode(destination, "UTF-8")
                    // Use Google Maps directions URL
                    Uri.parse("google.navigation:q=$encodedDest&mode=$modeCode")
                }
                // Search query
                else -> {
                    val encodedQuery = URLEncoder.encode(query!!, "UTF-8")
                    // Use geo URI for general search
                    Uri.parse("geo:0,0?q=$encodedQuery")
                }
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Check if there's an app to handle this intent
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)

                val output = when {
                    destination != null -> "Opening directions to: $destination (${mode.lowercase()})"
                    else -> "Searching maps for: $query"
                }

                ToolResult.Success(
                    toolId = toolId,
                    output = output,
                    data = mapOf(
                        "query" to (query ?: ""),
                        "destination" to (destination ?: ""),
                        "mode" to mode
                    ),
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            } else {
                ToolResult.Failure(
                    toolId = toolId,
                    error = ToolError.Unavailable("No maps app found"),
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            ToolResult.Failure(
                toolId = toolId,
                error = ToolError.ExecutionFailed(e),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    override fun validateArguments(arguments: Map<String, String>): Result<Unit> {
        val query = arguments["query"]
        val destination = arguments["destination"]

        if (query == null && destination == null) {
            return Result.failure(
                IllegalArgumentException("Either query or destination parameter is required")
            )
        }

        val mode = arguments["mode"]
        val validModes = listOf("driving", "walking", "bicycling", "bike", "transit", "public")
        if (mode != null && mode.lowercase() !in validModes) {
            return Result.failure(
                IllegalArgumentException("mode must be one of: driving, walking, bicycling, transit")
            )
        }

        return Result.success(Unit)
    }
}
