package app.m1k3.ai.assistant.database

import app.cash.sqldelight.db.SqlDriver

/**
 * PHASE0-003: Database Factory Interface (LEGACY)
 *
 * Cross-platform interface for creating encrypted database instances.
 * Each platform (Android, iOS, Desktop) provides its own implementation
 * with SQLCipher encryption.
 *
 * Privacy Guarantee: All data encrypted at rest with AES-256.
 *
 * NOTE: This interface is kept for backward compatibility.
 * New code should use the SimpleDatabaseFactory class instead.
 */
@Deprecated("Use SimpleDatabaseFactory instead")
interface OldDatabaseFactory {
    fun createDriver(passphrase: String): SqlDriver
    fun getDatabasePassphrase(): String
    fun generateAndStorePassphrase(): String
}

/**
 * Database encryption configuration
 */
object DatabaseConfig {
    const val DATABASE_NAME = "ma_database.db"
    const val PASSPHRASE_KEY = "db_passphrase_v1"
    const val KEYSTORE_ALIAS = "ma_database_key"

    // SQLCipher settings
    const val CIPHER_PAGE_SIZE = 4096
    const val KDF_ITER = 256000 // PBKDF2 iterations (recommended: 256000+)
}

/**
 * Simple DatabaseFactory implementation for Koin DI
 *
 * Accepts a pre-configured SqlDriver and creates the database instance.
 * This is simpler than the interface-based approach and works well with Koin.
 */
class DatabaseFactory(private val driver: SqlDriver) {
    fun createDatabase(): MaDatabase {
        return MaDatabase(driver)
    }
}
