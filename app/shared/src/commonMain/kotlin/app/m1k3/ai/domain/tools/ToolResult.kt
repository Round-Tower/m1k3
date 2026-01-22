package app.m1k3.ai.domain.tools

/**
 * Tool Result - Outcome of tool execution
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Sealed class with three possible outcomes:
 * - Success: Tool completed successfully with output
 * - Failure: Tool failed with an error
 * - RequiresConfirmation: Tool needs user approval before executing
 *
 * All variants track execution time for performance monitoring.
 */
sealed class ToolResult {
    /**
     * The ID of the tool that was executed
     */
    abstract val toolId: String

    /**
     * Execution time in milliseconds
     */
    abstract val executionTimeMs: Long

    /**
     * Whether this result represents a successful execution
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * Whether this result represents a failed execution
     */
    val isFailure: Boolean
        get() = this is Failure

    /**
     * Whether this result requires user confirmation
     */
    val requiresConfirmation: Boolean
        get() = this is RequiresConfirmation

    /**
     * Successful tool execution
     *
     * @property toolId ID of the executed tool
     * @property output Human-readable output for display/LLM context
     * @property data Structured data from the tool (optional)
     * @property executionTimeMs Time taken to execute
     */
    data class Success(
        override val toolId: String,
        val output: String,
        val data: Map<String, Any>? = null,
        override val executionTimeMs: Long
    ) : ToolResult()

    /**
     * Failed tool execution
     *
     * @property toolId ID of the tool that failed
     * @property error The error that occurred
     * @property executionTimeMs Time until failure
     */
    data class Failure(
        override val toolId: String,
        val error: ToolError,
        override val executionTimeMs: Long
    ) : ToolResult()

    /**
     * Tool requires user confirmation before execution
     *
     * Used for sensitive operations (file writes, sending messages, etc.)
     * The UI should show a confirmation dialog and either:
     * - Re-execute the tool with confirmation flag
     * - Cancel the operation
     *
     * @property toolId ID of the tool requiring confirmation
     * @property confirmationPrompt Message to show the user
     * @property pendingCall The original call to execute if confirmed
     * @property executionTimeMs Always 0 (no execution occurred yet)
     */
    data class RequiresConfirmation(
        override val toolId: String,
        val confirmationPrompt: String,
        val pendingCall: ToolCall,
        override val executionTimeMs: Long = 0
    ) : ToolResult()
}
