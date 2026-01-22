package app.m1k3.ai.assistant.tools.executors

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import app.m1k3.ai.assistant.domain.tools.ToolCall
import app.m1k3.ai.assistant.domain.tools.ToolError
import app.m1k3.ai.assistant.domain.tools.ToolResult
import app.m1k3.ai.assistant.domain.tools.services.ToolExecutor

/**
 * Toggle Flashlight Executor - Controls device flashlight
 *
 * Uses Camera2 API to control the flashlight torch mode.
 */
class ToggleFlashlightExecutor(
    private val context: Context
) : ToolExecutor {

    override val toolId = "toggle_flashlight"

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return false
            cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun execute(call: ToolCall): ToolResult {
        val startTime = System.currentTimeMillis()

        val enableStr = call.getArgument("enable")
            ?: return ToolResult.Failure(
                toolId = toolId,
                error = ToolError.InvalidArguments("enable parameter is required"),
                executionTimeMs = System.currentTimeMillis() - startTime
            )

        val enable = enableStr.lowercase() == "true"

        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return ToolResult.Failure(
                    toolId = toolId,
                    error = ToolError.Unavailable("No camera found"),
                    executionTimeMs = System.currentTimeMillis() - startTime
                )

            cameraManager.setTorchMode(cameraId, enable)

            val output = if (enable) "Flashlight turned on" else "Flashlight turned off"

            ToolResult.Success(
                toolId = toolId,
                output = output,
                data = mapOf("enabled" to enable),
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
        val enable = arguments["enable"]
        if (enable == null) {
            return Result.failure(IllegalArgumentException("enable parameter is required"))
        }
        if (enable.lowercase() !in listOf("true", "false")) {
            return Result.failure(IllegalArgumentException("enable must be 'true' or 'false'"))
        }
        return Result.success(Unit)
    }
}
