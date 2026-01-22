package app.m1k3.ai.assistant.domain.tools

/**
 * Tool - A capability the AI can invoke
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Tools represent actions the AI assistant can perform on the device.
 * Each tool has a unique identifier, description for LLM context,
 * parameter schema for validation, and category for organization.
 *
 * **Design Philosophy:**
 * - Tools are declarative (describe what, not how)
 * - Platform implementations provide the actual execution
 * - Schema enables both validation and LLM prompt generation
 *
 * @property id Unique identifier (snake_case, e.g., "open_camera")
 * @property name Human-readable name for UI
 * @property description Description for LLM context (explains what the tool does)
 * @property parameters List of parameters this tool accepts
 * @property category Category for grouping and permissions
 * @property requiresConfirmation Whether user confirmation is needed before execution
 */
data class Tool(
    val id: String,
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>,
    val category: ToolCategory,
    val requiresConfirmation: Boolean = false
) {
    /**
     * Generate schema string for LLM prompt inclusion
     *
     * Formats the tool as a human-readable schema that can be
     * included in LLM prompts for tool selection.
     *
     * Example output:
     * ```
     * toggle_flashlight: Toggle device flashlight
     *   Parameters:
     *     - enable: boolean (required)
     *       Turn on (true) or off (false)
     * ```
     *
     * @return Formatted schema string
     */
    fun toSchemaString(): String = buildString {
        appendLine("$id: $description")
        if (parameters.isNotEmpty()) {
            appendLine("  Parameters:")
            parameters.forEach { param ->
                val requiredStr = if (param.required) "(required)" else "(optional)"
                appendLine("    - ${param.name}: ${param.type.name.lowercase()} $requiredStr")
                appendLine("      ${param.description}")
            }
        }
    }
}
