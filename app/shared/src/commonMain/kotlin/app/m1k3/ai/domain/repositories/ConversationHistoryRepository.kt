package app.m1k3.ai.domain.repositories

/**
 * Conversation History Repository - Abstract access to conversation messages.
 *
 * Provides access to recent messages for context retrieval without
 * exposing the database implementation.
 *
 * Domain interface - Pure Kotlin, no platform dependencies.
 *
 * **Usage:**
 * ```kotlin
 * val history = repository.getRecentUserMessages(projectId = "default", limit = 6)
 * // Returns: ["What is photosynthesis?", "Can you explain more?", ...]
 * ```
 */
interface ConversationHistoryRepository {
    /**
     * Get recent user messages for a project.
     *
     * Returns only user messages (not assistant responses) to avoid
     * feedback loops in context retrieval.
     *
     * @param projectId Project ID to scope messages
     * @param limit Maximum number of messages to retrieve
     * @return List of message content strings, oldest first
     */
    fun getRecentUserMessages(projectId: String, limit: Int): List<String>
}
