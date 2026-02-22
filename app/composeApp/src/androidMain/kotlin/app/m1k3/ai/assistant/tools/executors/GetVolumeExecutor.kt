package app.m1k3.ai.assistant.tools.executors

import android.content.Context
import android.media.AudioManager
import app.m1k3.ai.domain.tools.ToolCall
import app.m1k3.ai.domain.tools.ToolError
import app.m1k3.ai.domain.tools.ToolResult
import app.m1k3.ai.domain.tools.services.ToolExecutor

/**
 * Get Volume Executor - Returns current volume levels
 *
 * Uses AudioManager to get volume for different streams.
 *
 * Parameters:
 * - stream: Volume stream type (media, ring, alarm, notification)
 *           Default: media
 */
class GetVolumeExecutor(
    private val context: Context
) : ToolExecutor {

    override val toolId = "get_volume"

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override suspend fun isAvailable(): Boolean = true

    override suspend fun execute(call: ToolCall): ToolResult {
        val startTime = System.currentTimeMillis()

        val streamType = call.getArgumentOrDefault("stream", "media")

        val audioStream = when (streamType.lowercase()) {
            "media" -> AudioManager.STREAM_MUSIC
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }

        return try {
            val currentVolume = audioManager.getStreamVolume(audioStream)
            val maxVolume = audioManager.getStreamMaxVolume(audioStream)
            val percentage = (currentVolume * 100) / maxVolume

            val streamName = when (streamType.lowercase()) {
                "ring" -> "Ringer"
                "alarm" -> "Alarm"
                "notification" -> "Notification"
                else -> "Media"
            }

            ToolResult.Success(
                toolId = toolId,
                output = "$streamName volume is at $percentage%",
                data = mapOf(
                    "stream" to streamType,
                    "current" to currentVolume,
                    "max" to maxVolume,
                    "percentage" to percentage
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
