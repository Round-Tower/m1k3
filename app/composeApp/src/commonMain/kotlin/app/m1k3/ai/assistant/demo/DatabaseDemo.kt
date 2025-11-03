package app.m1k3.ai.assistant.demo

import app.m1k3.ai.assistant.database.MaDatabase
import kotlinx.datetime.Clock

/**
 * M1K3 AI - Database Demo
 *
 * Demonstrates working SQLDelight database with:
 * - Project creation
 * - Message storage
 * - Knowledge base queries
 *
 * 100% local, encrypted storage.
 */
class DatabaseDemo(private val database: MaDatabase) {

    /**
     * Creates a demo project and returns statistics
     */
    fun createDemoProject(): ProjectStats {
        val projectId = "demo_project_001"
        val now = Clock.System.now().toEpochMilliseconds()

        // Create demo project
        database.projectQueries.insertProject(
            id = projectId,
            name = "M1K3 AI Demo",
            description = "Privacy-first AI assistant demonstration",
            created_at = now,
            updated_at = now,
            is_archived = 0,
            color = "#00BCD4",
            icon = "robot",
            message_count = 0,
            total_tokens = 0
        )

        // Add demo message
        database.messageQueries.insertMessage(
            id = "msg_001",
            project_id = projectId,
            role = "assistant",
            content = "Welcome to M1K3 AI! I'm your privacy-first assistant running 100% locally on your device.",
            timestamp = now,
            tokens = 25,
            image_uri = null,
            sentiment_valence = 0.8,
            sentiment_arousal = 0.6,
            sentiment_dominance = 0.7,
            sentiment_emotion = "happy",
            sentiment_intensity = 0.75,
            rag_sources = null,
            rag_confidence = null
        )

        // Update project message count
        database.projectQueries.incrementMessageCount(
            updated_at = now,
            id = projectId
        )

        // Get stats
        val project = database.projectQueries.getProjectById(projectId).executeAsOne()
        val messageCount = database.messageQueries.getMessageCount(projectId).executeAsOne()
        val triviaCount = database.triviaFactQueries.getTotalFactCount().executeAsOne()

        return ProjectStats(
            projectName = project.name,
            messageCount = messageCount.toInt(),
            triviaFactsAvailable = triviaCount.toInt(),
            databaseSize = "~1.6MB",
            encrypted = true
        )
    }

    /**
     * Queries knowledge base for a category
     */
    fun queryKnowledgeBase(category: String): List<KnowledgeFact> {
        return database.triviaFactQueries
            .getFactsByCategory(category)
            .executeAsList()
            .take(5)
            .map { fact ->
                KnowledgeFact(
                    category = fact.category,
                    question = fact.question,
                    answerPreview = fact.answer.take(100) + "..."
                )
            }
    }

    /**
     * Gets all available knowledge categories
     */
    fun getKnowledgeCategories(): List<String> {
        return database.triviaFactQueries
            .getAllCategories()
            .executeAsList()
    }
}

data class ProjectStats(
    val projectName: String,
    val messageCount: Int,
    val triviaFactsAvailable: Int,
    val databaseSize: String,
    val encrypted: Boolean
)

data class KnowledgeFact(
    val category: String,
    val question: String,
    val answerPreview: String
)
