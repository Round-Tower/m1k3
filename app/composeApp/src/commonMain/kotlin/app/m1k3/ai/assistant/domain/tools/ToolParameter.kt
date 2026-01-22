package app.m1k3.ai.assistant.domain.tools

/**
 * Parameter Type - Supported argument types for tool parameters
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 */
enum class ParameterType {
    STRING,
    NUMBER,
    BOOLEAN,
    ENUM
}

/**
 * Tool Parameter - Describes a single input parameter for a tool
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Parameters define the schema for tool arguments, enabling:
 * - Validation before execution
 * - LLM prompt generation (tool descriptions)
 * - UI rendering for manual tool invocation
 *
 * @property name Parameter name (used in argument maps)
 * @property type Data type for validation
 * @property description Human-readable description for LLM context
 * @property required Whether this parameter must be provided
 * @property defaultValue Default value if not provided (for optional params)
 * @property enumValues Valid values if type is ENUM
 */
data class ToolParameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = true,
    val defaultValue: String? = null,
    val enumValues: List<String>? = null
)
