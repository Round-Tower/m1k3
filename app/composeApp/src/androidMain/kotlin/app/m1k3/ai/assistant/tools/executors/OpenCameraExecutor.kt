package app.m1k3.ai.assistant.tools.executors

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import app.m1k3.ai.domain.tools.ToolCall
import app.m1k3.ai.domain.tools.ToolError
import app.m1k3.ai.domain.tools.ToolResult
import app.m1k3.ai.domain.tools.services.ToolExecutor

/**
 * Open Camera Executor - Launches device camera app
 *
 * Uses implicit Intent to launch any camera app on the device.
 */
class OpenCameraExecutor(
    private val context: Context
) : ToolExecutor {

    override val toolId = "open_camera"

    override suspend fun isAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    override suspend fun execute(call: ToolCall): ToolResult {
        val startTime = System.currentTimeMillis()

        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ToolResult.Success(
                    toolId = toolId,
                    output = "Camera app opened",
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            } else {
                ToolResult.Failure(
                    toolId = toolId,
                    error = ToolError.Unavailable("No camera app found"),
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

    override fun validateArguments(arguments: Map<String, String>): Result<Unit> =
        Result.success(Unit) // No arguments
}
