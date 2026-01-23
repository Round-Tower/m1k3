package app.m1k3.ai.assistant.knowledge

import android.content.Context
import android.content.SharedPreferences
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.utils.Logger
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Manages knowledge base import with versioning and multi-source loading.
 *
 * Features:
 * - Version-based import (only reimport when KB changes)
 * - Multi-source loading (comprehensive + system knowledge)
 * - Verification and stats
 * - Thread-safe operation
 *
 * Knowledge Base Sources:
 * 1. **Comprehensive KB** (1,391 docs) - General knowledge across 20 categories
 * 2. **M1K3 System KB** (10 docs) - Self-awareness, capabilities, technical details
 *
 * Versioning Format: MAJOR.MINOR.PATCH
 * - MAJOR: Breaking schema changes
 * - MINOR: New categories or substantial content (50+ docs)
 * - PATCH: Bug fixes, small updates (<50 docs)
 *
 * Example usage:
 * ```kotlin
 * val manager = KnowledgeImportManager(context, database)
 * val result = manager.importIfNeeded()
 *
 * when (result) {
 *     is ImportResult.Success -> println("Loaded: ${result.totalDocs} documents")
 *     is ImportResult.AlreadyImported -> println("Already current: ${result.existingDocs}")
 *     is ImportResult.Error -> println("Failed: ${result.error}")
 * }
 * ```
 */
class KnowledgeImportManager(
    private val context: Context,
    private val database: MaDatabase
) {
    private val logger = Logger.withTag("KnowledgeImportManager")
    private val prefs: SharedPreferences = context.getSharedPreferences("m1k3_kb", Context.MODE_PRIVATE)

    companion object {
        /**
         * Current knowledge base version.
         * Increment when KB content changes.
         *
         * Current: 1.0.0 (fresh start with tiered retrieval)
         * - 1,391 comprehensive documents (VERIFIED/SYNTHETIC based on metadata)
         * - 10 M1K3 system documents (CURATED)
         * - 25 AI/ML curated documents (CURATED)
         * - Total: ~1,426 documents with tiered retrieval
         */
        const val CURRENT_KB_VERSION = "1.0.0"

        private const val COMPREHENSIVE_KB_PATH = "composeResources/myapplication.composeapp.generated.resources/files/comprehensive_knowledge_base.json"
        private const val SYSTEM_KB_PATH = "composeResources/myapplication.composeapp.generated.resources/files/m1k3_system_knowledge.json"
        private const val AI_ML_KB_PATH = "knowledge/ai_ml_knowledge.json"
    }

    /**
     * Result of knowledge base import operation.
     */
    sealed class ImportResult {
        /**
         * Knowledge base successfully imported.
         *
         * @param totalDocs Total documents imported
         * @param comprehensiveDocs Documents from comprehensive KB
         * @param systemDocs Documents from system KB
         * @param version KB version imported
         */
        data class Success(
            val totalDocs: Int,
            val comprehensiveDocs: Int,
            val systemDocs: Int,
            val version: String
        ) : ImportResult()

        /**
         * Knowledge base already imported and current.
         *
         * @param existingDocs Number of documents already in database
         * @param version Current KB version
         */
        data class AlreadyImported(
            val existingDocs: Long,
            val version: String
        ) : ImportResult()

        /**
         * Import failed with error.
         *
         * @param error Exception that caused failure
         * @param message Human-readable error message
         */
        data class Error(
            val error: Exception,
            val message: String
        ) : ImportResult()
    }

    /**
     * Import knowledge base if needed (version check).
     *
     * Checks stored KB version and only reimports if:
     * - No documents exist in database
     * - Stored version != current version
     *
     * Thread-safe: Can be called from any coroutine/thread.
     *
     * @return ImportResult indicating success, skip, or error
     */
    suspend fun importIfNeeded(): ImportResult {
        return try {
            val importer = KnowledgeBaseImporter(database)

            // Check existing count
            val existingCount = database.triviaFactQueries.getTotalFactCount().executeAsOne()

            // Check version
            val storedVersion = prefs.getString("kb_version", "0.0.0") ?: "0.0.0"
            val needsReimport = storedVersion != CURRENT_KB_VERSION

            // Log version check
            if (needsReimport && existingCount > 0) {
                logger.i { "Knowledge base update: $storedVersion → $CURRENT_KB_VERSION" }
                database.triviaFactQueries.deleteAllFacts()
            }

            // Skip if already imported and current
            if (existingCount > 0 && !needsReimport) {
                logger.i { "Knowledge base already loaded ($existingCount documents, version $storedVersion)" }
                return ImportResult.AlreadyImported(existingCount, storedVersion)
            }

            // Perform import
            logger.i { "Importing knowledge bases (1,401 documents from 2 sources)" }

            // 1. Load comprehensive knowledge base (1,391 docs)
            // Uses metadata.synthetic flag to determine tier (SYNTHETIC or VERIFIED)
            val comprehensiveJson = loadAssetFile(COMPREHENSIVE_KB_PATH)
            val comprehensiveResult = importer.importKnowledgeBase(
                jsonContent = comprehensiveJson,
                tierOverride = null, // Auto-detect from synthetic flag
                sourceOverride = "comprehensive_kb"
            )
            logger.i { "Comprehensive KB: ${comprehensiveResult.imported} documents imported" }

            // 2. Load M1K3 system knowledge base (10 docs) - CURATED tier
            // System knowledge is hand-crafted, high-quality content
            val systemJson = loadAssetFile(SYSTEM_KB_PATH)
            val systemResult = importer.importKnowledgeBase(
                jsonContent = systemJson,
                tierOverride = "CURATED",
                sourceOverride = "m1k3_system_kb"
            )
            logger.i { "M1K3 System KB: ${systemResult.imported} documents imported (CURATED)" }

            // 3. Load AI/ML curated knowledge base (~25 docs) - CURATED tier
            // Expert-crafted AI/ML educational content
            val aiMlJson = loadAssetFile(AI_ML_KB_PATH)
            val aiMlResult = importer.importCuratedKnowledgeBase(
                jsonContent = aiMlJson,
                tier = "CURATED",
                source = "ai_ml_curated"
            )
            logger.i { "AI/ML KB: ${aiMlResult.imported} documents imported (CURATED)" }

            // Verify combined import
            val verification = importer.verifyImport()
            logger.d { verification.toString() }

            val totalImported = comprehensiveResult.imported + systemResult.imported + aiMlResult.imported

            // Save KB version after successful import
            prefs.edit().putString("kb_version", CURRENT_KB_VERSION).apply()
            logger.i { "Knowledge base version $CURRENT_KB_VERSION saved" }

            // Log tier breakdown
            val tierStats = database.triviaFactQueries.getTierStats().executeAsList()
            tierStats.forEach { stat ->
                logger.i { "  Tier ${stat.tier}: ${stat.fact_count} facts (avg importance: ${"%.2f".format(stat.avg_importance)})" }
            }

            ImportResult.Success(
                totalDocs = totalImported,
                comprehensiveDocs = comprehensiveResult.imported,
                systemDocs = systemResult.imported + aiMlResult.imported, // Combined curated docs
                version = CURRENT_KB_VERSION
            )

        } catch (e: Exception) {
            logger.e(e) { "Knowledge import failed" }
            ImportResult.Error(e, "Failed to import knowledge base: ${e.message}")
        }
    }

    /**
     * Load text file from assets.
     *
     * @param path Asset path relative to assets directory
     * @return File contents as string
     * @throws Exception if file not found or read error
     */
    private fun loadAssetFile(path: String): String {
        return context.assets.open(path).use { input ->
            BufferedReader(InputStreamReader(input)).use { reader ->
                reader.readText()
            }
        }
    }

    /**
     * Get current knowledge base statistics.
     *
     * @return Pair of (document count, version string)
     */
    fun getStats(): Pair<Long, String> {
        val count = database.triviaFactQueries.getTotalFactCount().executeAsOne()
        val version = prefs.getString("kb_version", "unknown") ?: "unknown"
        return Pair(count, version)
    }

    /**
     * Force reimport of knowledge base (ignores version check).
     *
     * Useful for:
     * - Debugging/testing
     * - Recovering from corrupted data
     * - Manual refresh
     *
     * @return ImportResult indicating success or error
     */
    suspend fun forceReimport(): ImportResult {
        // Clear version to trigger reimport
        prefs.edit().remove("kb_version").apply()
        database.triviaFactQueries.deleteAllFacts()

        return importIfNeeded()
    }
}
