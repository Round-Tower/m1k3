package app.m1k3.ai.domain.chat.format

/**
 * Message Role - Identifies the sender of a message in a conversation
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Used by ChatFormat to apply correct formatting for each role.
 *
 * @property value String value used in prompts (e.g., "user", "assistant")
 */
enum class MessageRole(val value: String) {
    /**
     * System instructions that define AI behavior
     */
    SYSTEM("system"),

    /**
     * User input/queries
     */
    USER("user"),

    /**
     * AI assistant responses
     */
    ASSISTANT("assistant"),

    /**
     * Tool execution results
     */
    TOOL("tool")
}
