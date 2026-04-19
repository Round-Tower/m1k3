package app.m1k3.ai.assistant.database

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.security.SecureRandom

/**
 * Android Database Factory Implementation.
 *
 * **Current state (2026-04-19):** the SQLite DB file is NOT encrypted at rest.
 * The passphrase plumbing below (Keystore → MasterKey → EncryptedSharedPreferences)
 * is wired up and ready, but the driver is plain `AndroidSqliteDriver` — no
 * SQLCipher. UI copy, `.sq` headers, and the system-knowledge JSON that the LLM
 * reads all still say "SQLCipher AES-256"; that's aspirational, not shipped.
 *
 * Privacy still rests on:
 *   - Android app-private storage (every other app sandboxed out by default).
 *   - Device full-disk encryption (mandatory on API 23+; we target 27+).
 *
 * To actually ship SQLCipher: add `implementation(libs.sqlcipher)` in
 * composeApp/build.gradle.kts, then swap `AndroidSqliteDriver` for a
 * `SupportOpenHelperFactory`-based driver using `getOrCreatePassphrase()` below.
 */
@Suppress("DEPRECATION")
class AndroidDatabaseFactory(
    private val context: Context,
) : OldDatabaseFactory {
    private val masterKey: MasterKey by lazy {
        MasterKey
            .Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "ma_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun createDriver(passphrase: String): SqlDriver {
        // Create standard SQLite driver
        // TODO: Integrate SQLCipher once we resolve gradle dependencies
        return AndroidSqliteDriver(
            schema = MaDatabase.Schema,
            context = context,
            name = DatabaseConfig.DATABASE_NAME,
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
        val passphrase =
            Base64.encodeToString(
                passphraseBytes,
                Base64.NO_WRAP or Base64.NO_PADDING,
            )

        // Store encrypted passphrase
        encryptedPrefs
            .edit()
            .putString(DatabaseConfig.PASSPHRASE_KEY, passphrase)
            .apply()

        return passphrase
    }
}
