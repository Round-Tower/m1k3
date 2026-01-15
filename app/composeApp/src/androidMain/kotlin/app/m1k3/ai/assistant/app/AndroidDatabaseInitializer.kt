package app.m1k3.ai.assistant.app

import app.m1k3.ai.assistant.database.AndroidDatabaseFactory
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.knowledge.KnowledgeImportManager
import android.content.Context

/**
 * Android-specific DatabaseInitializer
 *
 * Implements database initialization and knowledge import using concrete Android types
 *
 * **Responsibilities:**
 * - Create database driver with encrypted passphrase
 * - Initialize MaDatabase with driver
 * - Import knowledge base (345 documents)
 * - Return type-safe results instead of throwing exceptions
 * - Log all operations for debugging
 *
 * **Usage:**
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *     private val logger = Logger.withTag("MainActivity")
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         lifecycleScope.launch {
 *             val initializer = AndroidDatabaseInitializer(this@MainActivity, logger)
 *             val dbResult = initializer.initializeDatabase()
 *             when (dbResult) {
 *                 is DatabaseInitResult.Success -> {
 *                     val database = dbResult.database as MaDatabase
 *                     val knowledgeResult = initializer.importKnowledge(database)
 *                     // Handle knowledge import result
 *                 }
 *                 is DatabaseInitResult.Error -> {
 *                     logger.e { "Database init failed: ${dbResult.message}" }
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
class AndroidDatabaseInitializer(
    private val context: Context,
    private val logger: ILogger
) {
    /**
     * Initialize database with encrypted passphrase
     *
     * Creates driver with passphrase and initializes MaDatabase
     *
     * @return DatabaseInitResult.Success or DatabaseInitResult.Error
     */
    suspend fun initializeDatabase(): DatabaseInitResult {
        return try {
            logger.i("Initializing database...")

            val databaseFactory = AndroidDatabaseFactory(context)
            val passphrase = databaseFactory.getDatabasePassphrase()
            val driver = databaseFactory.createDriver(passphrase)
            val database = MaDatabase(driver)

            logger.i("Database initialized successfully")
            DatabaseInitResult.Success(database)
        } catch (e: Exception) {
            logger.e(e, "Failed to initialize database")
            DatabaseInitResult.Error("Database initialization failed: ${e.message}", e)
        }
    }

    /**
     * Import knowledge base if not already imported
     *
     * @param database MaDatabase instance
     * @return KnowledgeImportResult
     */
    suspend fun importKnowledge(database: MaDatabase): KnowledgeImportResult {
        return try {
            logger.i("Importing knowledge base...")

            val knowledgeManager = KnowledgeImportManager(context, database)
            val result = knowledgeManager.importIfNeeded()

            when (result) {
                is KnowledgeImportManager.ImportResult.Success -> {
                    logger.i("Knowledge import succeeded: ${result.totalDocs} documents")
                    KnowledgeImportResult.Success(
                        totalDocs = result.totalDocs,
                        comprehensiveDocs = result.comprehensiveDocs,
                        systemDocs = result.systemDocs
                    )
                }
                is KnowledgeImportManager.ImportResult.AlreadyImported -> {
                    logger.i("Knowledge already imported: ${result.existingDocs} documents")
                    KnowledgeImportResult.AlreadyImported(result.existingDocs)
                }
                is KnowledgeImportManager.ImportResult.Error -> {
                    logger.e(result.error, result.message)
                    KnowledgeImportResult.Error(result.message)
                }
            }
        } catch (e: Exception) {
            logger.e(e, "Failed to import knowledge")
            KnowledgeImportResult.Error("Knowledge import failed: ${e.message}")
        }
    }
}
