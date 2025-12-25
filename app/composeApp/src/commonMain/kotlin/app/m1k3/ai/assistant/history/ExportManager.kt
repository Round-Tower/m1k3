package app.m1k3.ai.assistant.history

import app.m1k3.ai.assistant.database.MaDatabase
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * ExportManager - Data Portability for Conversation History
 *
 * **Philosophy:**
 * Your data belongs to you. ExportManager provides complete data portability
 * with human-readable and machine-readable formats for backup, sharing, and migration.
 *
 * **Supported Formats:**
 * - JSON: Machine-readable, preserves all metadata, perfect for backups
 * - Markdown: Human-readable, shareable, great for documentation
 *
 * **Usage Example:**
 * ```kotlin
 * // Export single conversation to JSON
 * val json = exportManager.exportConversationToJson(conversationId)
 * saveToFile("conversation_backup.json", json)
 *
 * // Export to Markdown for sharing
 * val markdown = exportManager.exportConversationToMarkdown(conversationId)
 * shareText(markdown)
 *
 * // Export entire project
 * val projectJson = exportManager.exportProjectToJson(projectId)
 * ```
 */
class ExportManager(private val database: MaDatabase) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    // ==================== JSON Export ====================

    /**
     * Export a single conversation to JSON format.
     *
     * Includes:
     * - Conversation metadata (title, timestamps, counts)
     * - All messages with full metadata
     * - Preserves timestamps, tokens, roles
     *
     * @param conversationId Conversation to export
     * @return JSON string or null if conversation doesn't exist
     */
    fun exportConversationToJson(conversationId: Long): String? {
        // Get conversation metadata
        val conversation = database.conversationMetadataQueries
            .getConversationById(conversationId)
            .executeAsOneOrNull()
            ?: return null

        // Get all messages
        val messages = database.messageQueries
            .getMessagesByConversation(conversationId)
            .executeAsList()

        // Build export object
        val export = ConversationExport(
            title = conversation.title,
            projectId = conversation.project_id,
            startedAt = conversation.started_at,
            lastMessageAt = conversation.last_message_at,
            messageCount = conversation.message_count.toInt(),
            tokenCount = conversation.token_count.toInt(),
            messages = messages.map { message ->
                MessageExport(
                    id = message.id,
                    role = message.role,
                    content = message.content,
                    timestamp = message.timestamp,
                    tokens = message.tokens?.toInt()
                )
            }
        )

        return json.encodeToString(export)
    }

    /**
     * Export an entire project (all conversations) to JSON format.
     *
     * Useful for full project backups.
     *
     * @param projectId Project to export
     * @return JSON string with all conversations
     */
    fun exportProjectToJson(projectId: String): String {
        // Get all conversations in project
        val conversations = database.conversationMetadataQueries
            .getConversationsByProject(projectId)
            .executeAsList()

        // Export each conversation
        val conversationExports = conversations.mapNotNull { conversation ->
            val messages = database.messageQueries
                .getMessagesByConversation(conversation.id)
                .executeAsList()

            ConversationExport(
                title = conversation.title,
                projectId = conversation.project_id,
                startedAt = conversation.started_at,
                lastMessageAt = conversation.last_message_at,
                messageCount = conversation.message_count.toInt(),
                tokenCount = conversation.token_count.toInt(),
                messages = messages.map { message ->
                    MessageExport(
                        id = message.id,
                        role = message.role,
                        content = message.content,
                        timestamp = message.timestamp,
                        tokens = message.tokens?.toInt()
                    )
                }
            )
        }

        val projectExport = ProjectExport(
            projectId = projectId,
            exportedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            conversationCount = conversationExports.size,
            conversations = conversationExports
        )

        return json.encodeToString(projectExport)
    }

    // ==================== Markdown Export ====================

    /**
     * Export a single conversation to Markdown format.
     *
     * Creates a human-readable document with:
     * - Conversation title as heading
     * - Metadata section (dates, counts)
     * - Messages in readable format
     * - Timestamps for each message
     *
     * @param conversationId Conversation to export
     * @return Markdown string or null if conversation doesn't exist
     */
    fun exportConversationToMarkdown(conversationId: Long): String? {
        // Get conversation metadata
        val conversation = database.conversationMetadataQueries
            .getConversationById(conversationId)
            .executeAsOneOrNull()
            ?: return null

        // Get all messages
        val messages = database.messageQueries
            .getMessagesByConversation(conversationId)
            .executeAsList()

        // Build Markdown document
        val markdown = buildString {
            // Title
            appendLine("# ${conversation.title ?: "Untitled Conversation"}")
            appendLine()

            // Metadata section
            appendLine("---")
            appendLine("**Started:** ${formatTimestamp(conversation.started_at)}")
            appendLine("**Last Message:** ${formatTimestamp(conversation.last_message_at)}")
            appendLine("**Messages:** ${conversation.message_count}")
            appendLine("**Tokens:** ${conversation.token_count}")
            appendLine("---")
            appendLine()

            // Messages
            if (messages.isEmpty()) {
                appendLine("*No messages in this conversation.*")
            } else {
                messages.forEach { message ->
                    val roleLabel = when (message.role) {
                        "user" -> "**User:**"
                        "assistant" -> "**Assistant:**"
                        else -> "**${message.role.replaceFirstChar { it.uppercase() }}:**"
                    }

                    appendLine("$roleLabel")
                    appendLine(message.content)
                    appendLine()
                    appendLine("*${formatTimestamp(message.timestamp)}*")
                    if (message.tokens != null) {
                        appendLine("*(${message.tokens} tokens)*")
                    }
                    appendLine()
                }
            }
        }

        return markdown
    }

    /**
     * Export an entire project to Markdown format.
     *
     * Creates a document with all conversations concatenated.
     *
     * @param projectId Project to export
     * @return Markdown string with all conversations
     */
    fun exportProjectToMarkdown(projectId: String): String {
        val conversations = database.conversationMetadataQueries
            .getConversationsByProject(projectId)
            .executeAsList()

        return buildString {
            appendLine("# Project Export: $projectId")
            appendLine()
            appendLine("---")
            appendLine("**Exported:** ${formatTimestamp(kotlinx.datetime.Clock.System.now().toEpochMilliseconds())}")
            appendLine("**Conversations:** ${conversations.size}")
            appendLine("---")
            appendLine()

            conversations.forEach { conversation ->
                val conversationMarkdown = exportConversationToMarkdown(conversation.id)
                if (conversationMarkdown != null) {
                    appendLine(conversationMarkdown)
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
            }
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Format Unix timestamp to human-readable date/time.
     *
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted date string (e.g., "2025-01-15 14:30")
     */
    private fun formatTimestamp(timestamp: Long): String {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

        return "${dateTime.date} ${dateTime.hour}:${dateTime.minute.toString().padStart(2, '0')}"
    }

    // ==================== Future: Import Functionality (Phase 2.1) ====================

    /**
     * Import a conversation from JSON (backup restoration).
     *
     * TODO: Implement in Phase 2.1
     *
     * @param projectId Project to import into
     * @param json JSON export string
     * @return New conversation ID
     */
    fun importConversationFromJson(projectId: String, json: String): Long? {
        // TODO: Phase 2.1
        // - Parse JSON
        // - Create conversation
        // - Insert messages
        // - Return new conversation ID
        return null
    }
}

// ==================== Data Classes ====================

/**
 * Serializable conversation export format.
 */
@Serializable
data class ConversationExport(
    val title: String?,
    val projectId: String,
    val startedAt: Long,
    val lastMessageAt: Long,
    val messageCount: Int,
    val tokenCount: Int,
    val messages: List<MessageExport>
)

/**
 * Serializable message export format.
 */
@Serializable
data class MessageExport(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val tokens: Int?
)

/**
 * Serializable project export format.
 */
@Serializable
data class ProjectExport(
    val projectId: String,
    val exportedAt: Long,
    val conversationCount: Int,
    val conversations: List<ConversationExport>
)
