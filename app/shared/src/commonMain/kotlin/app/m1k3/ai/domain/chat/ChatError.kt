package app.m1k3.ai.domain.chat

/**
 * ChatError - Domain error types for chat generation.
 *
 * Represents error conditions that can occur during AI chat generation.
 * Maps low-level exceptions to domain-level error categories.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Note: UI-specific formatting (toUserMessage, toEmoji) should be
 * implemented as extension functions in the presentation layer.
 */
sealed class ChatError {
    /** Raw error message from underlying system */
    abstract val message: String

    /** Memory error (OOM during generation) */
    data class OutOfMemory(override val message: String) : ChatError()

    /** Generation timeout */
    data class Timeout(override val message: String) : ChatError()

    /** Model loading or inference error */
    data class ModelError(override val message: String) : ChatError()

    /** Engine initialization error */
    data class EngineInitError(override val message: String) : ChatError()

    /** Unknown/unexpected error */
    data class Unknown(override val message: String) : ChatError()

    /**
     * Whether this error is recoverable (user can retry).
     */
    val isRecoverable: Boolean
        get() = when (this) {
            is Timeout -> true
            is OutOfMemory -> true  // Can retry after freeing memory
            is ModelError -> false
            is EngineInitError -> false
            is Unknown -> false
        }

    /**
     * Error category for analytics/logging.
     */
    val category: String
        get() = when (this) {
            is OutOfMemory -> "memory"
            is Timeout -> "timeout"
            is ModelError -> "model"
            is EngineInitError -> "init"
            is Unknown -> "unknown"
        }

    companion object {
        /**
         * Create appropriate ChatError from an exception.
         */
        fun fromException(e: Throwable): ChatError {
            val message = e.message ?: "Unknown error"
            return when {
                message.contains("OutOfMemory", ignoreCase = true) ||
                message.contains("OOM", ignoreCase = true) ->
                    OutOfMemory(message)

                message.contains("timeout", ignoreCase = true) ||
                message.contains("timed out", ignoreCase = true) ->
                    Timeout(message)

                message.contains("model", ignoreCase = true) ||
                message.contains("inference", ignoreCase = true) ->
                    ModelError(message)

                message.contains("init", ignoreCase = true) ||
                message.contains("load", ignoreCase = true) ->
                    EngineInitError(message)

                else -> Unknown(message)
            }
        }
    }
}
