package app.m1k3.ai.assistant.app

import app.m1k3.ai.assistant.database.AndroidDatabaseFactory
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.knowledge.KnowledgeImportManager
import android.content.Context
import co.touchlab.kermit.Logger as KermitLogger

/**
 * Interface for database initialization
 *
 * Allows mocking in tests for InitializationViewModel
 */
interface IDatabaseInitializer {
    suspend fun initializeDatabase(): DatabaseInitResult
    suspend fun importKnowledge(database: MaDatabase): KnowledgeImportResult
}

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
open class AndroidDatabaseInitializer(
    private val context: Context,
    private val logger: ILogger
) : IDatabaseInitializer {
    /**
     * Initialize database with encrypted passphrase
     *
     * Creates driver with passphrase and initializes MaDatabase
     *
     * @return DatabaseInitResult.Success or DatabaseInitResult.Error
     */
    override suspend fun initializeDatabase(): DatabaseInitResult {
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
    override suspend fun importKnowledge(database: MaDatabase): KnowledgeImportResult {
        return try {
            logger.i("Importing knowledge base...")

            val knowledgeManager = KnowledgeImportManager(context, database)
            val result = knowledgeManager.importIfNeeded()

            when (result) {
                is KnowledgeImportManager.ImportResult.Success -> {
                    logger.i("Knowledge import succeeded: ${result.totalDocs} curated documents")
                    KnowledgeImportResult.Success(
                        totalDocs = result.totalDocs,
                        curatedDocs = result.curatedDocs
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

/**
 * Adapter to convert KermitLogger to ILogger interface
 *
 * Bridges Kermit's logger API with the ILogger interface expected by initialization managers.
 */
class LoggerAdapter(private val logger: KermitLogger) : ILogger {
    override fun i(message: String) {
        logger.i { message }
    }

    override fun e(error: Throwable?, message: String) {
        logger.e(error) { message }
    }
}
