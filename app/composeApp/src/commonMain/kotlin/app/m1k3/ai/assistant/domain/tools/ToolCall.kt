package app.m1k3.ai.assistant.domain.tools

/**
 * Tool Call - Parsed tool invocation from LLM output
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Represents a request to execute a specific tool with arguments.
 * Created by parsing LLM output (JSON, XML-style, or function call format).
 *
 * @property toolId The ID of the tool to invoke (must match a registered Tool)
 * @property arguments Map of argument names to string values
 * @property rawText Original text that was parsed (for debugging/logging)
 */
data class ToolCall(
    val toolId: String,
    val arguments: Map<String, String>,
    val rawText: String
) {
    /**
     * Get an argument value by name
     *
     * @param name Argument name
     * @return Value if present, null otherwise
     */
    fun getArgument(name: String): String? = arguments[name]

    /**
     * Get an argument value or a default
     *
     * @param name Argument name
     * @param default Value to return if argument is not present
     * @return Argument value or default
     */
    fun getArgumentOrDefault(name: String, default: String): String =
        arguments[name] ?: default
}
