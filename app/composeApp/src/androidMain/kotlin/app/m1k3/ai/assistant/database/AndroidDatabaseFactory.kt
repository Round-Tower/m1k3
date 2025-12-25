package app.m1k3.ai.assistant.database

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.security.SecureRandom
import android.util.Base64

/**
 * PHASE0-003: Android Database Factory Implementation
 *
 * Creates SQLite databases with:
 * - AndroidX Security Crypto for passphrase storage
 * - Android Keystore for master key management
 *
 * TODO: Add SQLCipher encryption once we resolve native library dependencies
 *
 * Security Architecture:
 * 1. Master key stored in Android Keystore (hardware-backed)
 * 2. Database passphrase encrypted with master key
 * 3. Passphrase stored in EncryptedSharedPreferences
 *
 * Privacy Guarantee: Passphrase encrypted at rest.
 */
@Suppress("DEPRECATION")
class AndroidDatabaseFactory(private val context: Context) : OldDatabaseFactory {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "ma_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun createDriver(passphrase: String): SqlDriver {
        // Create standard SQLite driver
        // TODO: Integrate SQLCipher once we resolve gradle dependencies
        return AndroidSqliteDriver(
            schema = MaDatabase.Schema,
            context = context,
            name = DatabaseConfig.DATABASE_NAME
        )
    }

    override fun getDatabasePassphrase(): String {
        var passphrase = encryptedPrefs.getString(DatabaseConfig.PASSPHRASE_KEY, null)

        if (passphrase == null) {
            // First launch: generate new passphrase
            passphrase = generateAndStorePassphrase()
        }

        return passphrase
    }

    override fun generateAndStorePassphrase(): String {
        // Generate cryptographically secure random passphrase
        val random = SecureRandom()
        val passphraseBytes = ByteArray(32) // 256 bits
        random.nextBytes(passphraseBytes)

        // Encode as Base64 for storage
        val passphrase = Base64.encodeToString(
            passphraseBytes,
            Base64.NO_WRAP or Base64.NO_PADDING
        )

        // Store encrypted passphrase
        encryptedPrefs.edit()
            .putString(DatabaseConfig.PASSPHRASE_KEY, passphrase)
            .apply()

        return passphrase
    }
}
