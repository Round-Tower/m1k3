package app.m1k3.ai.assistant.knowledge

import app.m1k3.ai.assistant.database.MaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * PHASE0-012: Knowledge Base Importer
 *
 * Imports M1K3's comprehensive knowledge base (1,341+ documents) into 間 AI.
 * Provides expert knowledge for RAG across 20 categories.
 *
 * Import Process:
 * 1. Read comprehensive_knowledge_base.json from resources
 * 2. Parse JSON documents
 * 3. Map to TriviaFact table schema
 * 4. Batch insert with conflict resolution
 *
 * Privacy: All knowledge stored locally, encrypted at rest.
 */
class KnowledgeBaseImporter(
    private val database: MaDatabase,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    /**
     * Imports the comprehensive knowledge base from JSON.
     *
     * @param jsonContent The knowledge base JSON string
     * @return Import statistics
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun importKnowledgeBase(jsonContent: String): ImportResult = withContext(Dispatchers.Default) {
        try {
            // Parse knowledge base JSON
            val knowledgeBase = json.decodeFromString<KnowledgeBase>(jsonContent)

            var successCount = 0
            var skipCount = 0
            var errorCount = 0

            // Import documents in batches
            knowledgeBase.documents.forEach { doc ->
                try {
                    // Map document to TriviaFact schema
                    val triviaFact = mapDocumentToTriviaFact(doc)

                    // Check if fact already exists
                    val existingFact = database.triviaFactQueries.getTriviaFactById(triviaFact.id).executeAsOneOrNull()

                    if (existingFact == null) {
                        // Insert new fact
                        database.triviaFactQueries.insertTriviaFact(
                            id = triviaFact.id,
                            category = triviaFact.category,
                            question = triviaFact.question,
                            answer = triviaFact.answer,
                            question_variants = triviaFact.questionVariants,
                            importance = triviaFact.importance.toDouble(),
                            confidence = triviaFact.confidence.toDouble(),
                            access_count = 0,
                            last_accessed_at = null,
                            embedding_id = null,
                            has_embedding = 0, // SQLite INTEGER: 0 = false, 1 = true
                            source = "m1k3_kb_v1",
                            created_at = Clock.System.now().toEpochMilliseconds(),
                            updated_at = Clock.System.now().toEpochMilliseconds()
                        )
                        successCount++
                    } else {
                        skipCount++
                    }

                } catch (e: Exception) {
                    errorCount++
                    println("⚠️ Failed to import document ${doc.id}: ${e.message}")
                }
            }

            ImportResult(
                totalDocuments = knowledgeBase.documents.size,
                imported = successCount,
                skipped = skipCount,
                errors = errorCount,
                categories = knowledgeBase.metadata.categories.size,
                version = knowledgeBase.version
            )

        } catch (e: Exception) {
            throw ImportException("Failed to import knowledge base", e)
        }
    }

    /**
     * Maps a knowledge base document to TriviaFact schema.
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun mapDocumentToTriviaFact(doc: KnowledgeDocument): TriviaFactData {
        // Extract question from title (remove "Problem: " prefix if present)
        val question = doc.title.replace(Regex("^(.*?Problem:|.*?Calculation:|.*?Query:)\\s*"), "")

        // Generate question variants from tags
        val questionVariants = doc.metadata.tags.joinToString(",")

        // Calculate importance based on difficulty
        val importance = when (doc.metadata.difficulty) {
            "beginner" -> 0.6
            "intermediate" -> 0.7
            "advanced" -> 0.8
            "expert" -> 0.9
            else -> 0.5
        }

        return TriviaFactData(
            id = doc.id,
            category = doc.category,
            question = question,
            answer = doc.content,
            questionVariants = questionVariants,
            importance = importance.toFloat(),
            confidence = 1.0f
        )
    }

    /**
     * Verifies knowledge base integrity after import.
     */
    suspend fun verifyImport(): VerificationResult = withContext(Dispatchers.Default) {
        val totalFacts = database.triviaFactQueries.getTotalFactCount().executeAsOne().toInt()
        val categories = database.triviaFactQueries.getAllCategories().executeAsList()
        val categoryCounts = database.triviaFactQueries.getCategoryCounts().executeAsList()

        VerificationResult(
            totalFacts = totalFacts,
            categories = categories,
            categoryBreakdown = categoryCounts.associate { it.category to it.count.toInt() }
        )
    }
}

/**
 * Knowledge base JSON schema (from M1K3)
 */
@Serializable
data class KnowledgeBase(
    val version: String,
    val metadata: KnowledgeMetadata,
    val documents: List<KnowledgeDocument>
)

@Serializable
data class KnowledgeMetadata(
    val created_at: String,
    val total_documents: Int,
    val categories: Map<String, Int>,
    val intents: Map<String, Int>? = null,
    val updated_at: String? = null
)

@Serializable
data class KnowledgeDocument(
    val id: String,
    val title: String,
    val content: String,
    val category: String,
    val intent: String,
    val metadata: DocumentMetadata,
    val embedding: String? = null,
    val created_at: String
)

@Serializable
data class DocumentMetadata(
    val tags: List<String>,
    val difficulty: String,
    val synthetic: Boolean? = null,
    val template_variables: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    val generated_at: String? = null
)

/**
 * Internal data class for TriviaFact
 */
data class TriviaFactData(
    val id: String,
    val category: String,
    val question: String,
    val answer: String,
    val questionVariants: String,
    val importance: Float,
    val confidence: Float
)

/**
 * Import result statistics
 */
data class ImportResult(
    val totalDocuments: Int,
    val imported: Int,
    val skipped: Int,
    val errors: Int,
    val categories: Int,
    val version: String
) {
    val successRate: Float = if (totalDocuments > 0) (imported.toFloat() / totalDocuments) * 100 else 0f

    override fun toString(): String = """
        📚 Knowledge Base Import Complete
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        Version: $version
        Total Documents: $totalDocuments
        ✅ Imported: $imported
        ⏭️ Skipped: $skipped
        ❌ Errors: $errors
        📁 Categories: $categories
        📊 Success Rate: ${"%.1f".format(successRate)}%
    """.trimIndent()
}

/**
 * Verification result
 */
data class VerificationResult(
    val totalFacts: Int,
    val categories: List<String>,
    val categoryBreakdown: Map<String, Int>
) {
    override fun toString(): String {
        val breakdown = categoryBreakdown.entries
            .sortedByDescending { it.value }
            .joinToString("\n") { "  • ${it.key}: ${it.value} facts" }

        return """
        🔍 Knowledge Base Verification
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        Total Facts: $totalFacts
        Categories: ${categories.size}

        Category Breakdown:
        $breakdown
    """.trimIndent()
    }
}

/**
 * Import exception
 */
class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
