package app.m1k3.ai.assistant.database

import app.cash.sqldelight.db.SqlDriver

/**
 * PHASE0-003: Database Factory Interface
 *
 * Cross-platform interface for creating encrypted database instances.
 * Each platform (Android, iOS, Desktop) provides its own implementation
 * with SQLCipher encryption.
 *
 * Privacy Guarantee: All data encrypted at rest with AES-256.
 */
interface DatabaseFactory {
    /**
     * Creates an encrypted SQLDriver instance.
     *
     * @param passphrase The encryption key (derived from device keystore)
     * @return Encrypted SQLDriver instance
     */
    fun createDriver(passphrase: String): SqlDriver

    /**
     * Gets the database encryption passphrase.
     * On Android: Uses AndroidX Security EncryptedSharedPreferences
     * On iOS: Uses Keychain
     * On Desktop: Uses OS-specific secure storage
     *
     * @return Securely stored passphrase
     */
    fun getDatabasePassphrase(): String

    /**
     * Generates and stores a new database passphrase.
     * Called on first app launch.
     *
     * @return Newly generated passphrase
     */
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
