package app.m1k3.ai.assistant.tools

import app.m1k3.ai.domain.tools.Tool
import app.m1k3.ai.domain.tools.ToolCategory
import app.m1k3.ai.domain.tools.services.ToolExecutor
import app.m1k3.ai.domain.tools.services.ToolFilter
import app.m1k3.ai.domain.tools.services.ToolRegistry

/**
 * Base Tool Registry Implementation
 *
 * Common implementation that can be extended by platform-specific registries.
 * Manages tool registration and lookup.
 *
 * **Usage:**
 * ```kotlin
 * // Android
 * class AndroidToolRegistry(context: Context) : ToolRegistryImpl() {
 *     init {
 *         registerTool(flashlightTool, FlashlightExecutor(context))
 *     }
 * }
 *
 * // iOS
 * class IosToolRegistry : ToolRegistryImpl() {
 *     init {
 *         registerTool(flashlightTool, IosFlashlightExecutor())
 *     }
 * }
 * ```
 */
open class ToolRegistryImpl : ToolRegistry {

    private val tools = mutableMapOf<String, Tool>()
    private val executors = mutableMapOf<String, ToolExecutor>()
    private val toolFilter = ToolFilter()

    override fun getAllTools(): List<Tool> = tools.values.toList()

    override fun getToolsByCategory(category: ToolCategory): List<Tool> =
        tools.values.filter { it.category == category }

    override suspend fun getAvailableTools(): List<Tool> =
        tools.values.filter { tool ->
            executors[tool.id]?.isAvailable() == true
        }

    override suspend fun getRelevantTools(query: String, maxTools: Int): List<Tool> {
        val available = getAvailableTools()
        if (available.isEmpty()) return emptyList()

        val scored = toolFilter.filterByRelevance(query, available, maxTools)
        return scored.map { it.first }
    }

    override fun findTool(toolId: String): Tool? = tools[toolId]

    override suspend fun isToolAvailable(toolId: String): Boolean =
        executors[toolId]?.isAvailable() == true

    override fun registerTool(tool: Tool, executor: ToolExecutor) {
        require(tool.id == executor.toolId) {
            "Tool ID mismatch: tool.id=${tool.id}, executor.toolId=${executor.toolId}"
        }
        tools[tool.id] = tool
        executors[tool.id] = executor
    }

    override fun getExecutor(toolId: String): ToolExecutor? = executors[toolId]

    /**
     * Unregister a tool (useful for testing)
     */
    fun unregisterTool(toolId: String) {
        tools.remove(toolId)
        executors.remove(toolId)
    }

    /**
     * Clear all registered tools (useful for testing)
     */
    fun clear() {
        tools.clear()
        executors.clear()
    }
}
