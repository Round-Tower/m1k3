package app.m1k3.ai.assistant.domain.tools.services

import app.m1k3.ai.assistant.domain.tools.ToolCall
import app.m1k3.ai.assistant.domain.tools.ToolResult

/**
 * Tool Executor - Executes a specific tool
 *
 * Domain service interface - Pure Kotlin definition.
 * Implementations are platform-specific (Android/iOS).
 *
 * Each tool has its own executor that knows how to:
 * - Check if the tool is available on the device
 * - Validate arguments before execution
 * - Execute the tool and return results
 *
 * **Implementation Pattern:**
 * - Android: Uses Intents, system services, ContentProviders
 * - iOS: Uses UIApplication, system frameworks
 *
 * **Usage:**
 * ```kotlin
 * class FlashlightExecutor(context: Context) : ToolExecutor {
 *     override val toolId = "toggle_flashlight"
 *
 *     override suspend fun execute(call: ToolCall): ToolResult {
 *         // Android-specific flashlight control
 *     }
 * }
 * ```
 */
interface ToolExecutor {
    /**
     * The ID of the tool this executor handles
     */
    val toolId: String

    /**
     * Check if this tool is available on the current device
     *
     * Should check hardware capabilities, permissions, etc.
     *
     * @return true if the tool can be executed
     */
    suspend fun isAvailable(): Boolean

    /**
     * Execute the tool with the given arguments
     *
     * @param call The tool call with arguments
     * @return Result of the execution (Success, Failure, or RequiresConfirmation)
     */
    suspend fun execute(call: ToolCall): ToolResult

    /**
     * Validate arguments before execution
     *
     * Called before execute() to check argument validity.
     * Should validate types, ranges, required fields, etc.
     *
     * @param arguments Map of argument names to values
     * @return Result.success if valid, Result.failure with error message if not
     */
    fun validateArguments(arguments: Map<String, String>): Result<Unit>
}
