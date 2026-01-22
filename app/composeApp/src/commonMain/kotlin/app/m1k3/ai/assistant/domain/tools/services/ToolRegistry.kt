package app.m1k3.ai.assistant.domain.tools.services

import app.m1k3.ai.assistant.domain.tools.Tool
import app.m1k3.ai.assistant.domain.tools.ToolCategory

/**
 * Tool Registry - Central registry for available tools
 *
 * Domain service interface - Pure Kotlin definition.
 * Implementations are platform-specific.
 *
 * The registry maintains a catalog of tools and their executors.
 * Platform implementations register their tools at startup.
 *
 * **Responsibilities:**
 * - Track all registered tools
 * - Map tools to their executors
 * - Filter tools by availability/category
 * - Provide tool lookup by ID
 *
 * **Usage:**
 * ```kotlin
 * class AndroidToolRegistry(context: Context) : ToolRegistry {
 *     init {
 *         registerTool(flashlightTool, FlashlightExecutor(context))
 *         registerTool(batteryTool, BatteryExecutor(context))
 *     }
 * }
 * ```
 */
interface ToolRegistry {
    /**
     * Get all registered tools
     *
     * @return List of all tools (regardless of availability)
     */
    fun getAllTools(): List<Tool>

    /**
     * Get tools filtered by category
     *
     * @param category The category to filter by
     * @return List of tools in the specified category
     */
    fun getToolsByCategory(category: ToolCategory): List<Tool>

    /**
     * Get tools that are currently available
     *
     * Filters out tools that aren't available on the current device
     * (e.g., flashlight on a device without flash).
     *
     * Note: This may involve async availability checks.
     *
     * @return List of available tools
     */
    suspend fun getAvailableTools(): List<Tool>

    /**
     * Find a tool by its ID
     *
     * @param toolId The tool identifier
     * @return The tool if found, null otherwise
     */
    fun findTool(toolId: String): Tool?

    /**
     * Check if a tool is available on the current device
     *
     * @param toolId The tool identifier
     * @return true if the tool exists and is available
     */
    suspend fun isToolAvailable(toolId: String): Boolean

    /**
     * Register a tool with its executor
     *
     * Called by platform implementations to add tools.
     *
     * @param tool The tool definition
     * @param executor The executor that handles this tool
     */
    fun registerTool(tool: Tool, executor: ToolExecutor)

    /**
     * Get the executor for a tool
     *
     * @param toolId The tool identifier
     * @return The executor if registered, null otherwise
     */
    fun getExecutor(toolId: String): ToolExecutor?
}
