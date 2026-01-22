package app.m1k3.ai.assistant.tools.executors

import android.content.Context
import android.media.AudioManager
import app.m1k3.ai.assistant.domain.tools.ToolCall
import app.m1k3.ai.assistant.domain.tools.ToolError
import app.m1k3.ai.assistant.domain.tools.ToolResult
import app.m1k3.ai.assistant.domain.tools.services.ToolExecutor

/**
 * Set Volume Executor - Adjusts volume levels
 *
 * Uses AudioManager to set volume for different streams.
 *
 * Parameters:
 * - level: Volume level 0-100 (percentage)
 * - stream: Volume stream type (media, ring, alarm, notification)
 *           Default: media
 */
class SetVolumeExecutor(
    private val context: Context
) : ToolExecutor {

    override val toolId = "set_volume"

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override suspend fun isAvailable(): Boolean = true

    override suspend fun execute(call: ToolCall): ToolResult {
        val startTime = System.currentTimeMillis()

        val levelStr = call.getArgument("level")
            ?: return ToolResult.Failure(
                toolId = toolId,
                error = ToolError.InvalidArguments("level parameter is required"),
                executionTimeMs = System.currentTimeMillis() - startTime
            )

        val level = levelStr.toIntOrNull()
        if (level == null || level !in 0..100) {
            return ToolResult.Failure(
                toolId = toolId,
                error = ToolError.InvalidArguments("level must be 0-100"),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val streamType = call.getArgumentOrDefault("stream", "media")

        val audioStream = when (streamType.lowercase()) {
            "media" -> AudioManager.STREAM_MUSIC
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }

        return try {
            val maxVolume = audioManager.getStreamMaxVolume(audioStream)
            val targetVolume = (level * maxVolume) / 100

            audioManager.setStreamVolume(
                audioStream,
                targetVolume,
                AudioManager.FLAG_SHOW_UI // Show the volume UI
            )

            val streamName = when (streamType.lowercase()) {
                "ring" -> "Ringer"
                "alarm" -> "Alarm"
                "notification" -> "Notification"
                else -> "Media"
            }

            ToolResult.Success(
                toolId = toolId,
                output = "$streamName volume set to $level%",
                data = mapOf(
                    "stream" to streamType,
                    "level" to level,
                    "actualValue" to targetVolume,
                    "maxValue" to maxVolume
                ),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: SecurityException) {
            // May need Do Not Disturb access for ring/notification on some devices
            ToolResult.Failure(
                toolId = toolId,
                error = ToolError.PermissionDenied("Volume control requires Do Not Disturb access"),
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
        val level = arguments["level"]
        if (level == null) {
            return Result.failure(IllegalArgumentException("level parameter is required"))
        }
        val levelInt = level.toIntOrNull()
        if (levelInt == null || levelInt !in 0..100) {
            return Result.failure(IllegalArgumentException("level must be 0-100"))
        }

        val stream = arguments["stream"]
        val validStreams = listOf("media", "ring", "alarm", "notification")
        if (stream != null && stream.lowercase() !in validStreams) {
            return Result.failure(
                IllegalArgumentException("stream must be one of: ${validStreams.joinToString()}")
            )
        }

        return Result.success(Unit)
    }
}
