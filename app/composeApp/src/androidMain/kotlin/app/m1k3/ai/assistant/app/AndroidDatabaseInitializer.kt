package app.m1k3.ai.assistant.app

import android.content.Context
import app.m1k3.ai.assistant.database.AndroidDatabaseFactory
import app.m1k3.ai.assistant.database.MaDatabase
import co.touchlab.kermit.Logger as KermitLogger

/**
 * Interface for database initialization.
 *
 * Allows mocking in tests for InitializationViewModel.
 */
interface IDatabaseInitializer {
    suspend fun initializeDatabase(): DatabaseInitResult
}

/**
 * Android-specific DatabaseInitializer.
 *
 * Opens the SQLCipher-backed MaDatabase via AndroidDatabaseFactory and returns
 * a type-safe result. Knowledge seeding retired 2026-04-20 — personal
 * corpora live in Passage/Source now (see migrations/5.sqm + 7.sqm).
 */
open class AndroidDatabaseInitializer(
    private val context: Context,
    private val logger: ILogger,
) : IDatabaseInitializer {
    override suspend fun initializeDatabase(): DatabaseInitResult =
        try {
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
 * Adapter to convert KermitLogger to ILogger.
 */
class LoggerAdapter(
    private val logger: KermitLogger,
) : ILogger {
    override fun i(message: String) {
        logger.i { message }
    }

    override fun e(
        error: Throwable?,
        message: String,
    ) {
        logger.e(error) { message }
    }
}
