package app.m1k3.ai.assistant.domain.tools

/**
 * Tool Error - Represents different failure modes for tool execution
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Sealed class hierarchy enables exhaustive handling of error cases.
 * Each error type carries relevant context for debugging and user feedback.
 */
sealed class ToolError {
    /**
     * Human-readable error message for UI display
     */
    abstract val displayMessage: String

    /**
     * Tool was not found in the registry
     *
     * @property toolId The requested tool ID that doesn't exist
     */
    data class NotFound(val toolId: String) : ToolError() {
        override val displayMessage: String
            get() = "Tool '$toolId' not found"
    }

    /**
     * Arguments failed validation
     *
     * @property message Description of what's wrong with the arguments
     */
    data class InvalidArguments(val message: String) : ToolError() {
        override val displayMessage: String
            get() = "Invalid arguments: $message"
    }

    /**
     * Required permission was denied
     *
     * @property permission The permission that was denied (e.g., "CAMERA")
     */
    data class PermissionDenied(val permission: String) : ToolError() {
        override val displayMessage: String
            get() = "Permission denied: $permission"
    }

    /**
     * Tool execution failed with an exception
     *
     * @property cause The underlying exception
     */
    data class ExecutionFailed(val cause: Throwable) : ToolError() {
        override val displayMessage: String
            get() = "Execution failed: ${cause.message ?: "Unknown error"}"
    }

    /**
     * Tool is not available on this device
     *
     * @property reason Why the tool is unavailable
     */
    data class Unavailable(val reason: String) : ToolError() {
        override val displayMessage: String
            get() = "Tool unavailable: $reason"
    }
}
